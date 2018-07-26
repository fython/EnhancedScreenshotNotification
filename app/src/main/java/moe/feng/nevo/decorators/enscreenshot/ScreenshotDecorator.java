package moe.feng.nevo.decorators.enscreenshot;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ScreenshotDecorator extends NevoDecoratorService {

    private static final String TAG = ScreenshotDecorator.class.getSimpleName();

    private static final String TARGET_PACKAGE = "com.android.systemui";

    private static final String EXTRA_NOTIFICATION_KEY =
            BuildConfig.APPLICATION_ID + ".extra.NOTIFICATION_KEY";
    private static final String EXTRA_ORIGINAL_PENDING_INTENT =
            BuildConfig.APPLICATION_ID + ".extra.ORIGINAL_PENDING_INTENT";

    private static final String CHANNEL_ID_SCREENSHOT = "screenshot";
    private static final String CHANNEL_ID_OTHER = "other";
    private static final String CHANNEL_ID_PERMISSION = "permission";

    private static final int NOTIFICATION_ID_REQUEST_PERMISSION = 10;

    private static final String ACTION_DELETE_SCREENSHOT =
            BuildConfig.APPLICATION_ID + ".action.DELETE_SCREENSHOT";

    private static final Intent INTENT_DELETE_SCREENSHOT = new Intent(ACTION_DELETE_SCREENSHOT);

    private final BroadcastReceiver mDeleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(@NonNull Context context, @Nullable Intent intent) {
            if (intent == null || !ACTION_DELETE_SCREENSHOT.equals(intent.getAction())) {
                return;
            }

            final String key = intent.getStringExtra(EXTRA_NOTIFICATION_KEY);
            // TODO: Sometimes it cannot cancel evolved notification.
            cancelNotification(key);

            final PendingIntent pi = intent.getParcelableExtra(EXTRA_ORIGINAL_PENDING_INTENT);
            try {
                pi.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
                if (isOrderedBroadcast()) {
                    try {
                        abortBroadcast();
                    } catch (RuntimeException ignored) {

                    }
                }
            }
        }
    };

    private ScreenshotPreferences mPreferences;

    @Override
    protected void onConnected() {
        mPreferences = new ScreenshotPreferences(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationChannel screenshotChannel = new NotificationChannel(
                    CHANNEL_ID_SCREENSHOT,
                    getString(R.string.noti_channel_screenshot),
                    NotificationManager.IMPORTANCE_HIGH
            );
            screenshotChannel.setSound(Uri.EMPTY, new AudioAttributes.Builder().build());
            screenshotChannel.enableLights(false);

            final NotificationChannel otherChannel = new NotificationChannel(
                    CHANNEL_ID_OTHER,
                    getString(R.string.noti_channel_other),
                    NotificationManager.IMPORTANCE_HIGH
            );
            screenshotChannel.setSound(Uri.EMPTY, new AudioAttributes.Builder().build());
            screenshotChannel.enableLights(true);

            createNotificationChannels(
                    TARGET_PACKAGE, Arrays.asList(screenshotChannel, otherChannel));
        }

        final IntentFilter intentFilter = new IntentFilter(ACTION_DELETE_SCREENSHOT);
        registerReceiver(mDeleteReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(mDeleteReceiver);
        } catch (Exception ignored) {

        }
        super.onDestroy();
    }

    @Override
    protected void apply(MutableStatusBarNotification evolving) {
        final MutableNotification n = evolving.getNotification();

        if (!isScreenshotNotification(n)) {
            Log.d(TAG, "Detect non-screenshot notification from System UI");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                n.setChannelId(CHANNEL_ID_OTHER);
            }
            return;
        } else {
            Log.d(TAG, "Detect screenshot notification from System UI");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            n.setChannelId(CHANNEL_ID_SCREENSHOT);
        }

        // Find out delete action and replace it for repairing bug
        for (int i = 0; i < n.actions.length; i++) {
            final Notification.Action a = n.actions[i];
            if (isDeleteActionText(this, a.title)) {
                final Intent intent = new Intent(INTENT_DELETE_SCREENSHOT);
                intent.putExtra(EXTRA_NOTIFICATION_KEY, evolving.getKey());
                intent.putExtra(EXTRA_ORIGINAL_PENDING_INTENT, a.actionIntent);
                final PendingIntent pi = PendingIntent.getBroadcast(
                        this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                final Notification.Action.Builder builder =
                        new Notification.Action.Builder(Icon.createWithResource(
                                this, R.drawable.ic_delete_black_24dp), a.title, pi);
                n.actions[i] = builder.build();
                break;
            }
        }

        // Add edit action if available
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            final File[] shots = new File(mPreferences.getScreenshotPath()).listFiles();
            if (shots != null && shots.length > 0) {
                final File recentShot = Arrays.stream(shots)
                        .sorted(Comparator.comparing(File::lastModified).reversed())
                        .collect(Collectors.toList())
                        .get(0);
                // Note: I attempted to use own file provider, but receivers wouldn't start to edit
                //       successfully. Maybe some of my code was wrong.
                //       Whatever, getting uri from MediaStore works great.
                final Uri documentUri = getImageContentUri(this, recentShot);
                final Notification.Action[] actions =
                        Arrays.copyOf(n.actions, n.actions.length + 1);
                final Intent intent = new Intent(Intent.ACTION_EDIT);
                intent.setDataAndType(documentUri, getMimeTypeFromFile(recentShot));
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                final PendingIntent editPendingIntent;
                final CharSequence editActionText;
                if (mPreferences.isPreferredEditorAvailable()) {
                    Log.d(TAG, "Your preferred editor is available.");
                    intent.setComponent(mPreferences.getPreferredEditorComponentName()
                            .orElseGet(() -> { throw new IllegalArgumentException("bky"); }));
                    editPendingIntent = PendingIntent.getActivity(
                            this, 0,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                    editActionText = getString(R.string.action_edit_in_format,
                            mPreferences.getPreferredEditorTitle().orElse("!?"));
                } else {
                    editPendingIntent = PendingIntent.getActivity(
                            this, 0,
                            Intent.createChooser(intent, getString(R.string.chooser_title_edit)),
                            PendingIntent.FLAG_UPDATE_CURRENT);
                    editActionText = getString(R.string.action_edit);
                }

                final Notification.Action.Builder builder = new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_delete_black_24dp),
                        editActionText, editPendingIntent);
                actions[actions.length - 1] = builder.build();
                n.actions = actions;
            }
        } else {
            // Notify user for required permission
            final NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                final Intent intent = new Intent(getApplicationContext(),
                        PermissionRequestActivity.class);
                intent.putExtra(PermissionRequestActivity.EXTRA_PERMISSION_TYPE,
                        PermissionRequestActivity.TYPE_STORAGE);
                final PendingIntent pi = PendingIntent.getActivity(
                        this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                final String content = getString(R.string.noti_storage_permission_required_content);
                final Notification.Builder builder = new Notification.Builder(this)
                        .setContentTitle(getString(R.string.noti_storage_permission_required_title))
                        .setStyle(new Notification.BigTextStyle().bigText(content))
                        .setSmallIcon(R.drawable.ic_assistant_white_24dp)
                        .setAutoCancel(true)
                        .setShowWhen(false)
                        .setContentIntent(pi);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setChannelId(CHANNEL_ID_PERMISSION);

                    final NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID_PERMISSION, "Permission",
                            NotificationManager.IMPORTANCE_LOW);
                    nm.createNotificationChannel(channel);
                }

                nm.notify(NOTIFICATION_ID_REQUEST_PERMISSION, builder.build());
            }
        }
    }

    private static boolean isScreenshotNotification(@Nullable MutableNotification n) {
        if (n == null) {
            return false;
        }
        if (n.extras == null || !n.extras.containsKey(Notification.EXTRA_TEMPLATE)) {
            return false;
        }
        if (!TEMPLATE_BIG_PICTURE.equals(n.extras.getString(Notification.EXTRA_TEMPLATE))) {
            return false;
        }
        if (n.actions == null || n.actions.length < 1) {
            return false;
        }
        return true;
    }

    private static boolean isDeleteActionText(
            @NonNull Context context, @Nullable CharSequence text) {
        if (text == null) {
            return false;
        }
        final String[] possibleTranslations = context.getResources()
                .getStringArray(R.array.action_delete_translations);
        return Arrays.stream(possibleTranslations)
                .anyMatch(one -> one.equalsIgnoreCase(text.toString()));
    }

    @Nullable
    private static String getMimeTypeFromFile(@NonNull File file) {
        final String name = file.getName();
        String extension = null;
        if (name.contains(".")) {
            extension = name.substring(name.lastIndexOf(".") + 1);
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }

    @Nullable
    private static Uri getImageContentUri(@NonNull Context context, @NonNull File imageFile) {
        String filePath = imageFile.getAbsolutePath();
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Images.Media._ID },
                MediaStore.Images.Media.DATA + "=? ",
                new String[] { filePath }, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor
                        .getColumnIndex(MediaStore.MediaColumns._ID));
                Uri baseUri = Uri.parse("content://media/external/images/media");
                return Uri.withAppendedPath(baseUri, "" + id);
            } else {
                if (imageFile.exists()) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DATA, filePath);
                    return context.getContentResolver().insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                } else {
                    return null;
                }
            }
        }
    }
}
