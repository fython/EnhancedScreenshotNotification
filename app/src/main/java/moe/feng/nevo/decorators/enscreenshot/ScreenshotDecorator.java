package moe.feng.nevo.decorators.enscreenshot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.*;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;

import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.oasisfeng.nevo.sdk.MutableNotification;
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification;
import com.oasisfeng.nevo.sdk.NevoDecoratorService;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.utils.*;

public final class ScreenshotDecorator extends NevoDecoratorService {

    private static final String TAG = ScreenshotDecorator.class.getSimpleName();

    private static final String TARGET_PACKAGE = "com.android.systemui";

    private static final String EXTRA_NOTIFICATION_KEY =
            BuildConfig.APPLICATION_ID + ".extra.NOTIFICATION_KEY";
    private static final String EXTRA_ORIGINAL_PENDING_INTENT =
            BuildConfig.APPLICATION_ID + ".extra.ORIGINAL_PENDING_INTENT";
    private static final String EXTRA_RECENT_SHOT =
            BuildConfig.APPLICATION_ID + ".extra.RECENT_SHOT";
    private static final String EXTRA_DATA =
            BuildConfig.APPLICATION_ID + ".extra.DATA";

    private static final String CHANNEL_ID_SCREENSHOT = "screenshot";
    private static final String CHANNEL_ID_PREVIEWED_SCREENSHOT = "previewed";
    private static final String CHANNEL_ID_OTHER = "other";
    private static final String CHANNEL_ID_PERMISSION = "permission";
    private static final String CHANNEL_ID_ASSISTANT = "assistant";

    private static final int NOTIFICATION_ID_REQUEST_PERMISSION = 10;
    private static final int NOTIFICATION_ID_BARCODE = 11;

    private static final String ACTION_SHARE_SCREENSHOT =
            BuildConfig.APPLICATION_ID + ".action.SHARE_SCREENSHOT";
    private static final String ACTION_DELETE_SCREENSHOT =
            BuildConfig.APPLICATION_ID + ".action.DELETE_SCREENSHOT";
    public static final String ACTION_CANCEL_NOTIFICATION =
            BuildConfig.APPLICATION_ID + ".action.CANCEL_NOTIFICATION";

    private final BroadcastReceiver mShareReceiver = new ShareActionReceiver();
    private final BroadcastReceiver mDeleteReceiver = new DeleteActionReceiver();
    private final BroadcastReceiver mCancelNotiReceiver = new CancelActionReceiver();

    private FirebaseApp mFirebase;
    private FirebaseVisionBarcodeDetector mBarcodeDetector;

    @SuppressWarnings("NullableProblems")
    @NonNull
    private ScreenshotPreferences mPreferences;

    @Nullable
    private Uri mLastPreviewedShotUri = null;

    @Nullable
    private CompletableFuture mDetectBarcodeTask = null;

    private final Singleton<Icon> mDeleteIcon = Singleton.by(() ->
            Icon.createWithResource(this, R.drawable.ic_delete_black_24dp));
    private final Singleton<Icon> mShareIcon = Singleton.by(() ->
            Icon.createWithResource(this, R.drawable.ic_delete_black_24dp));

    @Override
    protected void onConnected() {
        mPreferences = new ScreenshotPreferences(this);

        mFirebase = DecoratorApplication.getFirebaseApp();
        mBarcodeDetector = FirebaseVision.getInstance(mFirebase).getVisionBarcodeDetector();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureEvolvedNotificationChannel();
        }

        // Register action receivers
        try {
            registerReceiver(mShareReceiver, new IntentFilter(ACTION_SHARE_SCREENSHOT));
            registerReceiver(mDeleteReceiver, new IntentFilter(ACTION_DELETE_SCREENSHOT));
            registerReceiver(mCancelNotiReceiver, new IntentFilter(ACTION_CANCEL_NOTIFICATION));
        } catch (Exception ignored) {

        }
    }

    @Override
    public void onDestroy() {
        // Unregister receivers safely
        try {
            unregisterReceiver(mShareReceiver);
            unregisterReceiver(mDeleteReceiver);
            unregisterReceiver(mCancelNotiReceiver);
        } catch (Exception ignored) {

        }
        super.onDestroy();
    }

    @Override
    protected void apply(MutableStatusBarNotification evolving) {
        final ScreenshotProcessor processor = new ScreenshotProcessor(this, evolving);
        processor.process();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void ensureEvolvedNotificationChannel() {
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

        final NotificationChannel previewedChannel = new NotificationChannel(
                CHANNEL_ID_PREVIEWED_SCREENSHOT,
                getString(R.string.noti_channel_screenshot_preview),
                NotificationManager.IMPORTANCE_MIN
        );
        previewedChannel.setSound(Uri.EMPTY, new AudioAttributes.Builder().build());
        previewedChannel.enableLights(false);

        createNotificationChannels(
                TARGET_PACKAGE, Arrays.asList(screenshotChannel, otherChannel, previewedChannel));
    }

    private void notifyRequiredPermission() {
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

    private static boolean isEditActionText(
            @NonNull Context context, @Nullable CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        final String[] possibleTranslations = context.getResources()
                .getStringArray(R.array.action_edit_translations);
        return Arrays.stream(possibleTranslations)
                .anyMatch(one -> one.equalsIgnoreCase(text.toString()));
    }

    private class ScreenshotProcessor {

        private final Context context;
        private final MutableStatusBarNotification evolving;
        private final MutableNotification n;

        private final boolean grantedPermission;

        private int deleteActionIndex = -1;
        private int shareActionIndex = -1;
        private int editActionIndex = -1;

        private File[] shots;
        private File recentShot;
        private Uri recentShotUri;
        private String recentShotMimeType;

        private Intent editIntent;
        private Intent previewIntent;

        ScreenshotProcessor(Context context, MutableStatusBarNotification evolving) {
            this.context = context;
            this.evolving = evolving;
            this.n = evolving.getNotification();

            grantedPermission = PermissionRequestActivity
                    .checkIfPermissionTypeGranted(context, PermissionRequestActivity.TYPE_STORAGE);
        }

        public boolean isGrantedPermission() {
            return grantedPermission;
        }

        public void process() {
            if (!isScreenshotNotification(n)) {
                // Do not apply other kinds of notifications from System UI
                Log.d(TAG, "Detect non-screenshot notification from System UI");
                return;
            } else {
                Log.d(TAG, "Detect screenshot notification from System UI");
            }

            findOutActionsIndex();
            if (deleteActionIndex != -1) {
                evolveDeleteAction();
            }
            if (shareActionIndex != -1) {
                evolveShareAction();
            }

            if (isGrantedPermission()) {
                findOutRecentShot();
                // Add screenshots count to notification
                if (shots != null && mPreferences.isShowScreenshotsCount()) {
                    n.extras.putString(Notification.EXTRA_SUB_TEXT,
                            getString(R.string.screenshots_count_format, shots.length));
                }
                if (recentShot != null) {
                    if (mPreferences.shouldDetectBarcode()) {
                        detectBarcode();
                    }

                    // Add screenshot details to notification
                    if (mPreferences.isShowScreenshotDetails()) {
                        setupDetails();
                    }

                    // Add recent shot file to share action
                    if (shareActionIndex != -1) {
                        evolveShareActionWhenGrantedPermission();
                    }

                    // Add edit action to notification
                    // Note: I attempted to use own file provider, but receivers wouldn't start to edit
                    //       successfully. Maybe some of my code was wrong.
                    //       Whatever, getting uri from MediaStore works great.
                    evolveEditAction();

                    evolveClickIntent();

                    if (mPreferences.canPreviewInFloatingWindow()
                            && mPreferences.isReplaceNotificationWithPreview()
                            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            && previewIntent != null) {
                        startFloatingPreviewAndMuteNotification();
                    }
                }

                // Set notification channel
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && TextUtils.isEmpty(n.getChannelId())) {
                    n.setChannelId(CHANNEL_ID_SCREENSHOT);
                }
            } else {
                notifyRequiredPermission();
            }
        }

        private void findOutActionsIndex() {
            for (int i = 0; i < n.actions.length; i++) {
                final Notification.Action a = n.actions[i];
                if (isDeleteActionText(context, a.title)) {
                    deleteActionIndex = i;
                } else if (isShareActionText(context, a.title)) {
                    shareActionIndex = i;
                } else if (isEditActionText(context, a.title)) {
                    editActionIndex = i;
                }
            }
        }

        private void findOutRecentShot() {
            shots = new File(mPreferences.getScreenshotPath()).listFiles();
            if (shots != null && shots.length > 0) {
                recentShot = Arrays.stream(shots)
                        .sorted(Comparator.comparing(File::lastModified).reversed())
                        .collect(Collectors.toList())
                        .get(0);
                recentShotUri = FileUtils.getImageContentUri(context, recentShot);
                recentShotMimeType = FileUtils.getMimeTypeFromFile(recentShot);
            }
        }

        private void evolveDeleteAction() {
            final Notification.Action deleteAction = n.actions[deleteActionIndex];
            // Fix the behavior of delete action
            final Intent intent = new Intent(ACTION_DELETE_SCREENSHOT);
            intent.putExtra(EXTRA_NOTIFICATION_KEY, evolving.getKey());
            intent.putExtra(EXTRA_ORIGINAL_PENDING_INTENT, deleteAction.actionIntent);
            final PendingIntent pi = PendingIntent.getBroadcast(
                    context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            final Notification.Action.Builder builder = new Notification.Action.Builder(
                    mShareIcon.get(),
                    deleteAction.title,
                    pi);
            n.actions[deleteActionIndex] = builder.build();
        }

        private void evolveShareAction() {
            if (ScreenshotPreferences.SHARE_EVOLVE_TYPE_DISMISS_AFTER_SHARING ==
                    mPreferences.getShareEvolveType()) {
                final Notification.Action shareAction = n.actions[shareActionIndex];
                final Intent intent = new Intent(ACTION_SHARE_SCREENSHOT);
                intent.putExtra(EXTRA_NOTIFICATION_KEY, evolving.getKey());
                intent.putExtra(EXTRA_ORIGINAL_PENDING_INTENT, shareAction.actionIntent);
                final PendingIntent pi = PendingIntent.getBroadcast(
                        context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                final Notification.Action.Builder builder = new Notification.Action.Builder(
                        mDeleteIcon.get(),
                        shareAction.title,
                        pi);
                n.actions[shareActionIndex] = builder.build();
            }
        }

        private void evolveShareActionWhenGrantedPermission() {
            if (ScreenshotPreferences.SHARE_EVOLVE_TYPE_DELETE_SCREENSHOT
                    == mPreferences.getShareEvolveType()) {
                final Notification.Action shareAction = n.actions[shareActionIndex];
                final Intent intent = new Intent(ACTION_SHARE_SCREENSHOT);
                intent.putExtra(EXTRA_NOTIFICATION_KEY, evolving.getKey());
                intent.putExtra(EXTRA_ORIGINAL_PENDING_INTENT, shareAction.actionIntent);
                intent.putExtra(EXTRA_RECENT_SHOT, recentShot);
                final PendingIntent pi = PendingIntent.getBroadcast(
                        context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                final Notification.Action.Builder builder = new Notification.Action.Builder(
                        mShareIcon.get(),
                        shareAction.title,
                        pi);
                n.actions[shareActionIndex] = builder.build();
            }
        }

        private void evolveEditAction() {
            // Ensure actions array capacity
            if (editActionIndex == -1) {
                n.actions = Arrays.copyOf(n.actions, n.actions.length + 1);
            }
            editIntent = new Intent(Intent.ACTION_EDIT);
            editIntent.setDataAndType(recentShotUri, recentShotMimeType);
            editIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            PendingIntent editPendingIntent = null;
            CharSequence editActionText = null;
            boolean needReplace = false;
            if (mPreferences.isPreferredEditorAvailable()) {
                Log.d(TAG, "Your preferred editor is available.");
                editIntent.setComponent(mPreferences.getPreferredEditorComponentName()
                        .orElseGet(() -> { throw new IllegalArgumentException("bky"); }));
                editPendingIntent = PendingIntent.getActivity(
                        context, 0,
                        editIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                final Optional<CharSequence> editorTitle = mPreferences.getPreferredEditorTitle();
                final String currentFormat = mPreferences.getEditActionTextFormat();
                if (editorTitle.isPresent()) {
                    editActionText = String.format(currentFormat, editorTitle.get());
                } else {
                    editActionText = currentFormat.replace("%", "%%");
                }

                needReplace = true;
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                editPendingIntent = PendingIntent.getActivity(
                        context, 0,
                        Intent.createChooser(editIntent, getString(R.string.chooser_title_edit)),
                        PendingIntent.FLAG_UPDATE_CURRENT);
                editActionText = getString(R.string.action_edit);

                needReplace = true;
            }

            if (needReplace) {
                final Notification.Action.Builder builder = new Notification.Action.Builder(
                        mShareIcon.get(),
                        editActionText,
                        editPendingIntent);
                if (editActionIndex == -1) {
                    editActionIndex = n.actions.length - 1;
                }
                n.actions[editActionIndex] = builder.build();
            }
        }

        private void evolveClickIntent() {
            if (mPreferences.canPreviewInFloatingWindow() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                previewIntent = new Intent(editIntent);
                previewIntent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                previewIntent.setAction(Intent.ACTION_MAIN);
                previewIntent.putExtra(PreviewActivity.EXTRA_NOTIFICATION_KEY, evolving.getKey());
                if (shareActionIndex != -1) {
                    previewIntent.putExtra(PreviewActivity.EXTRA_SHARE_INTENT,
                            n.actions[shareActionIndex].actionIntent);
                }
                if (deleteActionIndex != -1) {
                    previewIntent.putExtra(PreviewActivity.EXTRA_DELETE_INTENT,
                            n.actions[deleteActionIndex].actionIntent);
                }
                if (editActionIndex != -1) {
                    previewIntent.putExtra(PreviewActivity.EXTRA_EDIT_INTENT,
                            n.actions[editActionIndex].actionIntent);
                }
                previewIntent.setComponent(ComponentName.createRelative(context, ".PreviewActivity"));
                n.contentIntent = PendingIntent.getActivity(context, 0,
                        previewIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
        }

        private void detectBarcode() {
            if (mDetectBarcodeTask != null && !mDetectBarcodeTask.isCancelled()) {
                mDetectBarcodeTask.cancel(true);
            }
            mDetectBarcodeTask = CompletableFuture.runAsync(() -> {
                try {
                    final FirebaseVisionImage image = FirebaseVisionImage.fromFilePath(context, recentShotUri);
                    mBarcodeDetector.detectInImage(image)
                            .addOnSuccessListener(Executors.mainThread(), res -> {
                                final ArrayList<Barcode> values = (ArrayList<Barcode>) res.stream()
                                        .map(MyFirebaseHelper::getBarcode)
                                        .collect(Collectors.toList());
                                if (!values.isEmpty()) {
                                    showBarcodeAction(values);
                                }
                            });
                } catch (IOException e) {
                    Log.e(TAG, "Failed to detect barcode.", e);
                }
            });
        }

        private void showBarcodeAction(@NonNull ArrayList<Barcode> values) {
            final Intent viewIntent = new Intent(ViewBarcodeActivity.ACTION);
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            viewIntent.putParcelableArrayListExtra(ViewBarcodeActivity.EXTRA_BARCODE, values);
            final PendingIntent viewBarcodePi = PendingIntent
                    .getActivity(context, 0, viewIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            final Notification.Builder builder = new Notification.Builder(context);
            final NotificationManager manager = Objects.requireNonNull(
                    context.getSystemService(NotificationManager.class));

            builder.setSmallIcon(R.drawable.ic_assistant_white_24dp);
            builder.setColor(context.getColor(R.color.blue_grey_500));
            builder.setAutoCancel(true);
            builder.setShowWhen(true);
            builder.setWhen(System.currentTimeMillis());
            builder.setSubText(context.getString(R.string.noti_detect_barcode_subtitle));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(CHANNEL_ID_ASSISTANT);

                NotificationChannel channel = new NotificationChannel(CHANNEL_ID_ASSISTANT,
                        context.getString(R.string.noti_channel_assistant),
                        NotificationManager.IMPORTANCE_HIGH);
                manager.createNotificationChannel(channel);
            }

            boolean unset = true;
            if (values.size() == 1) {
                final Barcode value = values.get(0);
                switch (value.valueFormat) {
                    case Barcode.URL: {
                        builder.setContentTitle(context.getString(R.string.noti_view_barcode_title_url));
                        builder.setContentText(context.getString(R.string.noti_view_barcode_text_url, value.rawValue));

                        builder.addAction(new Notification.Action.Builder(
                                mShareIcon.get(),
                                context.getString(R.string.action_open_link),
                                PendingIntent.getActivity(context, 0,
                                        Intent.createChooser(IntentUtils.createViewIntent(Uri.parse(value.rawValue)),
                                                context.getString(R.string.action_view_barcode)),
                                        PendingIntent.FLAG_UPDATE_CURRENT)
                        ).build());
                        builder.addAction(new Notification.Action.Builder(
                                mShareIcon.get(),
                                context.getString(android.R.string.copyUrl),
                                PendingIntent.getBroadcast(context, 1,
                                        IntentUtils.createCopyIntent(value.rawValue),
                                        PendingIntent.FLAG_CANCEL_CURRENT)
                        ).build());
                        unset = false;
                        break;
                    }
                    case Barcode.PHONE: {
                        final String telNumber = value.phone.number;
                        builder.setContentTitle(context.getString(R.string.noti_view_barcode_title_phone));
                        builder.setContentText(context.getString(R.string.noti_view_barcode_text_phone, telNumber));

                        builder.addAction(new Notification.Action.Builder(
                                mShareIcon.get(),
                                context.getString(R.string.action_call),
                                PendingIntent.getActivity(context, 0,
                                        IntentUtils.createDialIntent(Uri.parse(value.rawValue)),
                                        PendingIntent.FLAG_CANCEL_CURRENT)
                        ).build());
                        builder.addAction(new Notification.Action.Builder(
                                mShareIcon.get(),
                                context.getString(android.R.string.copy),
                                PendingIntent.getBroadcast(context, 1,
                                        IntentUtils.createCopyIntent(value.phone.number),
                                        PendingIntent.FLAG_CANCEL_CURRENT)
                        ).build());
                        unset = false;
                        break;
                    }
                    case Barcode.CONTACT_INFO: {
                        String phoneNumber = null;
                        if (value.contactInfo.phones != null && value.contactInfo.phones.length > 0) {
                            phoneNumber = Arrays.stream(value.contactInfo.phones)
                                    .map(phone -> phone.number)
                                    .reduce((total, acc) -> total + ", " + acc).get();
                        }
                        builder.setContentTitle(context.getString(R.string.noti_view_barcode_title_contact));
                        builder.setContentText(context.getString(R.string.noti_view_barcode_text_contact,
                                Optional.ofNullable(phoneNumber).orElse(context.getString(R.string.undefined))));

                        final Intent addContactIntent = IntentUtils.createAddContactFromBarcode(value);
                        builder.addAction(new Notification.Action.Builder(
                                mShareIcon.get(),
                                context.getString(R.string.action_add_to_contacts),
                                PendingIntent.getActivity(context, 1,
                                        addContactIntent, PendingIntent.FLAG_CANCEL_CURRENT)
                        ).build());
                        builder.addAction(new Notification.Action.Builder(
                                mShareIcon.get(),
                                context.getString(android.R.string.copy),
                                PendingIntent.getBroadcast(context, 2,
                                        IntentUtils.createCopyIntent(value.rawValue),
                                        PendingIntent.FLAG_CANCEL_CURRENT)
                        ).build());
                        unset = false;
                        break;
                    }
                    case Barcode.EMAIL: {
                        // TODO Support email barcode
                        break;
                    }
                    case Barcode.GEO: {
                        // TODO Support geo
                        break;
                    }
                    case Barcode.WIFI: {
                        // TODO Support Wi-Fi
                        break;
                    }
                    default: {
                        builder.setContentTitle(context.getString(R.string.noti_view_barcode_title_default));
                        builder.setContentText(context.getString(R.string.noti_view_barcode_text_single, value.rawValue));

                        builder.addAction(new Notification.Action.Builder(
                                mShareIcon.get(),
                                context.getString(android.R.string.copy),
                                PendingIntent.getBroadcast(context, 1,
                                        IntentUtils.createCopyIntent(value.rawValue),
                                        PendingIntent.FLAG_CANCEL_CURRENT)
                        ).build());
                        unset = false;
                    }
                }
            }
            if (unset) {
                builder.setContentTitle(context.getString(R.string.noti_view_barcode_title_default));
                builder.setContentText(context.getString(R.string.noti_view_barcode_text_multi, values.size()));
                builder.setContentIntent(viewBarcodePi);
            }

            manager.notify(NOTIFICATION_ID_BARCODE, builder.build());
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        private void startFloatingPreviewAndMuteNotification() {
            // Avoid duplicated preview
            if (mLastPreviewedShotUri == null || !mLastPreviewedShotUri.equals(recentShotUri)) {
                mLastPreviewedShotUri = recentShotUri;
                previewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Log.d(TAG, "PreviewActivity: should be started");
                startActivity(previewIntent);
            }
            n.setChannelId(CHANNEL_ID_PREVIEWED_SCREENSHOT);
        }

        private void setupDetails() {
            final String detailsFormat = getString(R.string.screenshot_details_format);
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(recentShot.getAbsolutePath(), options);
            final String detailsText = String.format(
                    detailsFormat,
                    recentShotMimeType,
                    String.valueOf(options.outWidth) + "*" + String.valueOf(options.outHeight),
                    Formatter.formatFileSize(context, recentShot.length())
            );
            n.extras.putString(Notification.EXTRA_TEXT, detailsText);
        }

    }

    private class ShareActionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_SHARE_SCREENSHOT.equals(intent.getAction())) {
                return;
            }

            if (mPreferences.getShareEvolveType() != ScreenshotPreferences.SHARE_EVOLVE_TYPE_NONE) {
                final String key = intent.getStringExtra(EXTRA_NOTIFICATION_KEY);
                cancelNotification(key);
            }

            if (mPreferences.getShareEvolveType() == ScreenshotPreferences.SHARE_EVOLVE_TYPE_DISMISS_AFTER_SHARING) {
                dismissAfterSharing(intent.getParcelableExtra(EXTRA_ORIGINAL_PENDING_INTENT));
            } else if (mPreferences.getShareEvolveType() == ScreenshotPreferences.SHARE_EVOLVE_TYPE_DELETE_SCREENSHOT) {
                deleteAfterSharing(context, (File) intent.getSerializableExtra(EXTRA_RECENT_SHOT));
            }

            IntentUtils.closeSystemDialogs(context);
        }

        private void dismissAfterSharing(PendingIntent originalIntent) {
            try {
                originalIntent.send();
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

        private void deleteAfterSharing(Context context, File recentShot) {
            try {
                if (recentShot != null) {
                    final File backup = FileUtils.backupScreenshot(context, recentShot);
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

    }

    private class DeleteActionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
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

    }

    private class CancelActionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !intent.hasExtra("key")) {
                Log.e(TAG, "Cancel notification intent should not be empty and contains a \"key\"");
                return;
            }
            cancelNotification(intent.getStringExtra("key"));
        }

    }

    public static class CopyUrlReceiver extends BroadcastReceiver {

        public static final ComponentName COMPONENT_NAME = ComponentName.createRelative(BuildConfig.APPLICATION_ID, CopyUrlReceiver.class.getName());

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !intent.hasExtra("data")) {
                return;
            }

            Toast.makeText(context, R.string.toast_copied_url, Toast.LENGTH_LONG).show();

            final ClipboardManager manager = Objects.requireNonNull(context.getSystemService(ClipboardManager.class));
            final ClipData clipData = ClipData.newPlainText("url", intent.getStringExtra("data"));
            manager.setPrimaryClip(clipData);

            IntentUtils.closeSystemDialogs(context);
        }
    }
}
