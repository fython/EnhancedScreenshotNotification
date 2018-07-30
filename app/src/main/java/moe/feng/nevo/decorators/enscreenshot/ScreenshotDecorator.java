package moe.feng.nevo.decorators.enscreenshot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.*;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;

import androidx.core.content.FileProvider;
import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.utils.FileUtils;
import moe.feng.nevo.decorators.enscreenshot.utils.IntentUtils;

public final class ScreenshotDecorator extends NevoDecoratorService {

    private static final String TAG = ScreenshotDecorator.class.getSimpleName();

    private static final String TARGET_PACKAGE = "com.android.systemui";

    private static final String EXTRA_NOTIFICATION_KEY =
            BuildConfig.APPLICATION_ID + ".extra.NOTIFICATION_KEY";
    private static final String EXTRA_ORIGINAL_PENDING_INTENT =
            BuildConfig.APPLICATION_ID + ".extra.ORIGINAL_PENDING_INTENT";
    private static final String EXTRA_RECENT_SHOT =
            BuildConfig.APPLICATION_ID + ".extra.RECENT_SHOT";

    private static final String CHANNEL_ID_SCREENSHOT = "screenshot";
    private static final String CHANNEL_ID_OTHER = "other";
    private static final String CHANNEL_ID_PERMISSION = "permission";

    private static final int NOTIFICATION_ID_REQUEST_PERMISSION = 10;

    private static final String ACTION_SHARE_SCREENSHOT =
            BuildConfig.APPLICATION_ID + ".action.SHARE_SCREENSHOT";
    private static final String ACTION_DELETE_SCREENSHOT =
            BuildConfig.APPLICATION_ID + ".action.DELETE_SCREENSHOT";

    private final BroadcastReceiver mShareReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(@NonNull Context context, @Nullable Intent intent) {
            if (intent == null || !ACTION_SHARE_SCREENSHOT.equals(intent.getAction())) {
                return;
            }

            if (mPreferences.getShareEvolveType() != ScreenshotPreferences.SHARE_EVOLVE_TYPE_NONE) {
                final String key = intent.getStringExtra(EXTRA_NOTIFICATION_KEY);
                cancelNotification(key);
            }

            if (mPreferences.getShareEvolveType() == ScreenshotPreferences.SHARE_EVOLVE_TYPE_DISMISS_AFTER_SHARING) {
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
            } else if (mPreferences.getShareEvolveType() == ScreenshotPreferences.SHARE_EVOLVE_TYPE_DELETE_SCREENSHOT) {
                try {
                    final File recentShot = (File) intent.getSerializableExtra(EXTRA_RECENT_SHOT);
                    if (recentShot != null) {
                        final File backup = backupScreenshot(context, recentShot);
                        if (backup != null) {
                            Log.d(TAG, recentShot.getAbsolutePath());
                            if (recentShot.exists() && !recentShot.delete()) {
                                Log.w(TAG, "Failed to delete screenshot! But we didn\'t notify users.");
                            }
                            final Uri uri = FileProvider.getUriForFile(context,
                                    DecoratorApplication.FILE_PROVIDER_AUTHORITY, backup);
                            final Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.setType(FileUtils.getMimeTypeFromFileName(
                                    Optional.ofNullable(uri.getLastPathSegment()).orElse("")));
                            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            context.startActivity(Intent.createChooser(shareIntent,
                                    context.getString(R.string.action_share_screenshot)));
                        } else {
                            Log.e(TAG, "Cannot backup screenshot so we fails to delete screenshot.");
                        }
                    } else {
                        Log.e(TAG, "Recent shot is null. Cannot finish deleting after sharing.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            IntentUtils.closeSystemDialogs(context);
        }
    };

    private final BroadcastReceiver mDeleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(@NonNull Context context, @Nullable Intent intent) {
            if (intent == null || !ACTION_DELETE_SCREENSHOT.equals(intent.getAction())) {
                return;
            }

            final String key = intent.getStringExtra(EXTRA_NOTIFICATION_KEY);
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

            IntentUtils.closeSystemDialogs(context);
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
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            screenshotChannel.setSound(Uri.EMPTY, new AudioAttributes.Builder().build());
            screenshotChannel.enableLights(true);

            createNotificationChannels(
                    TARGET_PACKAGE, Arrays.asList(screenshotChannel, otherChannel));
        }

        // Register action receivers
        try {
            registerReceiver(mShareReceiver, new IntentFilter(ACTION_SHARE_SCREENSHOT));
            registerReceiver(mDeleteReceiver, new IntentFilter(ACTION_DELETE_SCREENSHOT));
        } catch (Exception ignored) {

        }
    }

    @Override
    public void onDestroy() {
        // Unregister receivers safely
        try {
            unregisterReceiver(mShareReceiver);
            unregisterReceiver(mDeleteReceiver);
        } catch (Exception ignored) {

        }
        super.onDestroy();
    }

    @Override
    protected void apply(MutableStatusBarNotification evolving) {
        final MutableNotification n = evolving.getNotification();

        if (!isScreenshotNotification(n)) {
            // Do not apply other kinds of notifications from System UI
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

        // Find out actions
        int deleteActionIndex = -1;
        int shareActionIndex = -1;
        int editActionIndex = -1;
        for (int i = 0; i < n.actions.length; i++) {
            final Notification.Action a = n.actions[i];
            if (isDeleteActionText(this, a.title)) {
                deleteActionIndex = i;
            } else if (isShareActionText(this, a.title)) {
                shareActionIndex = i;
            }
        }

        if (deleteActionIndex != -1) {
            final Notification.Action deleteAction = n.actions[deleteActionIndex];
            // Fix the behavior of delete action
            final Intent intent = new Intent(ACTION_DELETE_SCREENSHOT);
            intent.putExtra(EXTRA_NOTIFICATION_KEY, evolving.getKey());
            intent.putExtra(EXTRA_ORIGINAL_PENDING_INTENT, deleteAction.actionIntent);
            final PendingIntent pi = PendingIntent.getBroadcast(
                    this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            final Notification.Action.Builder builder =
                    new Notification.Action.Builder(Icon.createWithResource(
                            this, R.drawable.ic_delete_black_24dp), deleteAction.title, pi);
            n.actions[deleteActionIndex] = builder.build();
        }
        if (shareActionIndex != -1) {
            if (ScreenshotPreferences.SHARE_EVOLVE_TYPE_DISMISS_AFTER_SHARING ==
                    mPreferences.getShareEvolveType()) {
                final Notification.Action shareAction = n.actions[shareActionIndex];
                final Intent intent = new Intent(ACTION_SHARE_SCREENSHOT);
                intent.putExtra(EXTRA_NOTIFICATION_KEY, evolving.getKey());
                intent.putExtra(EXTRA_ORIGINAL_PENDING_INTENT, shareAction.actionIntent);
                final PendingIntent pi = PendingIntent.getBroadcast(
                        this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                final Notification.Action.Builder builder =
                        new Notification.Action.Builder(Icon.createWithResource(
                                this, R.drawable.ic_delete_black_24dp), shareAction.title, pi);
                n.actions[shareActionIndex] = builder.build();
            }
        }

        // These features require storage permission.
        if (PermissionRequestActivity
                .checkIfPermissionTypeGranted(this, PermissionRequestActivity.TYPE_STORAGE)) {
            final File[] shots = new File(mPreferences.getScreenshotPath()).listFiles();
            if (shots != null && shots.length > 0) {
                // Add screenshots count to notification
                if (mPreferences.isShowScreenshotsCount()) {
                    n.extras.putString(Notification.EXTRA_SUB_TEXT,
                            getString(R.string.screenshots_count_format, shots.length));
                }

                // Get recent screenshot
                final File recentShot = Arrays.stream(shots)
                        .sorted(Comparator.comparing(File::lastModified).reversed())
                        .collect(Collectors.toList())
                        .get(0);
                final String shotMimeType = FileUtils.getMimeTypeFromFile(recentShot);

                // Add screenshot details to notification
                if (mPreferences.isShowScreenshotDetails()) {
                    final String detailsFormat = getString(R.string.screenshot_details_format);
                    final BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(recentShot.getAbsolutePath(), options);
                    final String detailsText = String.format(
                            detailsFormat,
                            shotMimeType,
                            String.valueOf(options.outWidth) + "*" + String.valueOf(options.outHeight),
                            Formatter.formatFileSize(this, recentShot.length())
                    );
                    Log.d(TAG, n.extras.keySet().toString());
                    Log.d(TAG, n.extras.toString());
                    n.extras.putString(Notification.EXTRA_TEXT, detailsText);
                }

                // Add recent shot file to share action
                if (shareActionIndex != -1) {
                    if (ScreenshotPreferences.SHARE_EVOLVE_TYPE_DELETE_SCREENSHOT
                            == mPreferences.getShareEvolveType()) {
                        final Notification.Action shareAction = n.actions[shareActionIndex];
                        final Intent intent = new Intent(ACTION_SHARE_SCREENSHOT);
                        intent.putExtra(EXTRA_NOTIFICATION_KEY, evolving.getKey());
                        intent.putExtra(EXTRA_ORIGINAL_PENDING_INTENT, shareAction.actionIntent);
                        intent.putExtra(EXTRA_RECENT_SHOT, recentShot);
                        final PendingIntent pi = PendingIntent.getBroadcast(
                                this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                        final Notification.Action.Builder builder =
                                new Notification.Action.Builder(Icon.createWithResource(
                                        this, R.drawable.ic_delete_black_24dp), shareAction.title, pi);
                        n.actions[shareActionIndex] = builder.build();
                    }
                }

                // Add edit action to notification
                // Note: I attempted to use own file provider, but receivers wouldn't start to edit
                //       successfully. Maybe some of my code was wrong.
                //       Whatever, getting uri from MediaStore works great.
                final Uri documentUri = getImageContentUri(this, recentShot);
                final Notification.Action[] actions =
                        Arrays.copyOf(n.actions, n.actions.length + 1);
                final Intent intent = new Intent(Intent.ACTION_EDIT);
                intent.setDataAndType(documentUri, shotMimeType);
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
                    final Optional<CharSequence> editorTitle = mPreferences.getPreferredEditorTitle();
                    final String currentFormat = mPreferences.getEditActionTextFormat();
                    if (editorTitle.isPresent()) {
                        editActionText = String.format(currentFormat, editorTitle.get());
                    } else {
                        editActionText = currentFormat.replace("%", "%%");
                    }
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
                if (editActionIndex == -1) {
                    editActionIndex = actions.length - 1;
                }
                actions[editActionIndex] = builder.build();
                n.actions = actions;

                // Replace click intent with preview intent
                if (mPreferences.canPreviewInFloatingWindow() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    final Intent previewIntent = new Intent(intent);
                    previewIntent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                    previewIntent.setAction(Intent.ACTION_MAIN);
                    if (shareActionIndex != -1) {
                        previewIntent.putExtra(PreviewActivity.EXTRA_SHARE_INTENT,
                                actions[shareActionIndex].actionIntent);
                    }
                    if (deleteActionIndex != -1) {
                        previewIntent.putExtra(PreviewActivity.EXTRA_DELETE_INTENT,
                                actions[deleteActionIndex].actionIntent);
                    }
                    if (editActionIndex != -1) {
                        previewIntent.putExtra(PreviewActivity.EXTRA_EDIT_INTENT,
                                actions[editActionIndex].actionIntent);
                    }
                    previewIntent.setComponent(ComponentName.createRelative(this, ".PreviewActivity"));
                    n.contentIntent = PendingIntent.getActivity(this, 0,
                            previewIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                }
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
                        .setContentText(content)
                        .setStyle(new Notification.BigTextStyle().bigText(content))
                        .setSmallIcon(R.drawable.ic_assistant_white_24dp)
                        .setAutoCancel(true)
                        .setShowWhen(false)
                        .setContentIntent(pi);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setChannelId(CHANNEL_ID_PERMISSION);

                    final NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID_PERMISSION, getString(R.string.noti_channel_permission),
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
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        final String[] possibleTranslations = context.getResources()
                .getStringArray(R.array.action_delete_translations);
        return Arrays.stream(possibleTranslations)
                .anyMatch(one -> one.equalsIgnoreCase(text.toString()));
    }

    private static boolean isShareActionText(
            @NonNull Context context, @Nullable CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        final String[] possibleTranslations = context.getResources()
                .getStringArray(R.array.action_share_translations);
        return Arrays.stream(possibleTranslations)
                .anyMatch(one -> one.equalsIgnoreCase(text.toString()));
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

    @Nullable
    private static File backupScreenshot(@NonNull Context context, @NonNull File file) {
        final File screenshotDir = new File(context.getCacheDir(), "screenshot");
        final File backupFile = new File(screenshotDir, file.getName());

        // Clear old files
        for (File oldFile : Optional.ofNullable(screenshotDir.listFiles()).orElse(new File[0])) {
            if (!oldFile.delete()) {
                Log.d(TAG, "Failed to delete " + oldFile.getAbsolutePath());
            }
        }

        // Start backup for sharing
        if (backupFile.exists() && !backupFile.delete()) {
            return null;
        } else if (!FileUtils.ensureDirectory(screenshotDir)) {
            return null;
        } else if (FileUtils.moveFile(file, backupFile)) {
            return backupFile;
        } else {
            return null;
        }
    }
}
