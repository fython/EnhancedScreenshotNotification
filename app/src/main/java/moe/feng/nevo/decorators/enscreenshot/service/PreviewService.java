package moe.feng.nevo.decorators.enscreenshot.service;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.util.Pair;
import android.view.*;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.BuildConfig;
import moe.feng.nevo.decorators.enscreenshot.R;
import moe.feng.nevo.decorators.enscreenshot.ScreenshotPreferences;
import moe.feng.nevo.decorators.enscreenshot.utils.Executors;
import moe.feng.nevo.decorators.enscreenshot.utils.FileUtils;
import moe.feng.nevo.decorators.enscreenshot.utils.IntentUtils;
import moe.feng.nevo.decorators.enscreenshot.widget.PreviewActionForegroundDrawable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static moe.feng.nevo.decorators.enscreenshot.Constants.*;

public class PreviewService extends Service {

    public static final ComponentName COMPONENT_NAME =
            ComponentName.createRelative(BuildConfig.APPLICATION_ID, PreviewService.class.getName());

    public static final String EXTRA_FILE = BuildConfig.APPLICATION_ID + ".extra.FILE";
    public static final String EXTRA_NOTIFICATION_KEY = BuildConfig.APPLICATION_ID + ".extra.NOTIFICATION_KEY";

    public static final String ACTION_START = BuildConfig.APPLICATION_ID + ".action.START_PREVIEW";
    public static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".action.STOP_PREVIEW";

    private static final String TAG = PreviewService.class.getSimpleName();

    public static Intent createStartIntent(@NonNull Uri imageUri,
                                           @NonNull File imageFile,
                                           @Nullable String notificationKey) {
        final Intent intent = new Intent(ACTION_START);
        intent.setComponent(COMPONENT_NAME);
        intent.setData(imageUri);
        intent.putExtra(EXTRA_FILE, imageFile);
        intent.putExtra(EXTRA_NOTIFICATION_KEY, notificationKey);
        return intent;
    }

    public static Intent createStopIntent() {
        final Intent intent = new Intent(ACTION_STOP);
        intent.setComponent(COMPONENT_NAME);
        return intent;
    }

    private ScreenshotPreferences mPreferences;

    private WindowManager mWindowManager;
    private NotificationManager mNotiManager;

    private Notification mForegroundNotification;

    private WindowManager.LayoutParams mLayoutParams;
    private ViewHolder mViewHolder;

    private Uri mImageUri;
    private File mImageFile;
    @Nullable
    private String mNotificationKey;

    private Pair<Uri, Bitmap> mLoadedData;

    private Future mStartPreviewTask;

    @Override
    public void onCreate() {
        mPreferences = new ScreenshotPreferences(this);

        mWindowManager = Objects.requireNonNull(getSystemService(WindowManager.class));
        mNotiManager = Objects.requireNonNull(getSystemService(NotificationManager.class));

        if (mForegroundNotification == null) {
            final Notification.Builder notificationBuilder = new Notification.Builder(this)
                    .setContentTitle(getString(R.string.noti_preview_service_title))
                    .setContentText(getString(R.string.noti_preview_service_text))
                    .setSmallIcon(R.drawable.ic_assistant_white_24dp)
                    .setColor(getColor(R.color.material_blue_grey_500))
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(this, R.drawable.ic_delete_black_24dp),
                            getString(R.string.action_stop),
                            PendingIntent.getService(
                                    this, 0, createStopIntent(), PendingIntent.FLAG_CANCEL_CURRENT)
                    ).build());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationBuilder.setChannelId(CHANNEL_ID_PREVIEW_SERVICE);

                NotificationChannel channel = new NotificationChannel(CHANNEL_ID_PREVIEW_SERVICE,
                        getString(R.string.noti_channel_preview_service),
                        NotificationManager.IMPORTANCE_MIN);
                channel.setShowBadge(false);
                channel.setSound(null, null);
                mNotiManager.createNotificationChannel(channel);
            }
            mForegroundNotification = notificationBuilder.build();
        }

        if (mLayoutParams == null) {
            initLayoutParams();
        }

        if (mViewHolder == null) {
            initView();
        }

        startForeground(NOTIFICATION_ID_PREVIEW_SERVICE, mForegroundNotification);
    }

    private void initLayoutParams() {
        mLayoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            mLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }
        // Show at right(end)-bottom corner
        mLayoutParams.gravity = Gravity.END | Gravity.BOTTOM;
        mLayoutParams.x = 0;
        mLayoutParams.y = 0;
        // FLAG_NOT_FOCUSABLE is for allowing users to touch outside of floating window.
        // FLAG_ALT_FOCUSABLE_IM is for preventing from being blocked by input method.
        mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        mLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mLayoutParams.format = PixelFormat.TRANSPARENT;
    }

    private void initView() {
        mViewHolder = new ViewHolder(
                this,
                LayoutInflater.from(this).inflate(R.layout.preview_window_content, null));
        mViewHolder.mShareButton.setOnClickListener(v -> {
            final Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(FileUtils.getMimeTypeFromFileName(mImageFile.getName()));
            shareIntent.putExtra(Intent.EXTRA_STREAM, mImageUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share_screenshot))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            stopSelf();
        });
        mViewHolder.mDeleteButton.setOnClickListener(v -> {
            mImageFile.delete();
            stopSelf();
        });
        mViewHolder.mEditButton.setOnClickListener(v -> {
            final Intent editIntent = new Intent(Intent.ACTION_EDIT);
            editIntent.setDataAndType(mImageUri, FileUtils.getMimeTypeFromFileName(mImageFile.getName()));
            editIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (mPreferences.isPreferredEditorAvailable()) {
                Log.d(TAG, "Your preferred editor is available.");
                editIntent.setComponent(mPreferences.getPreferredEditorComponentName()
                        .orElseGet(() -> { throw new IllegalArgumentException("bky"); }));
                editIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(editIntent);
            } else {
                startActivity(Intent.createChooser(editIntent, getString(R.string.chooser_title_edit))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            stopSelf();
        });
    }

    private void startPreview() {
        if (mStartPreviewTask != null && !mStartPreviewTask.isCancelled()) {
            mStartPreviewTask.cancel(true);
        }
        mStartPreviewTask = CompletableFuture.runAsync(() -> {
            if (mLoadedData == null || !mImageUri.equals(mLoadedData.first)) {
                InputStream input = null;
                if (mImageUri.toString().startsWith("content://")) {
                    try {
                        input = getContentResolver().openInputStream(mImageUri);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported uri: " + mImageUri);
                }

                if (input == null) {
                    Log.e(TAG, "Cannot open input stream for " + mImageUri);
                    return;
                }

                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                options.inPreferredConfig = Bitmap.Config.RGB_565;

                Bitmap result = BitmapFactory.decodeStream(input, null, options);
                if (result == null) {
                    throw new NullPointerException("Cannot decode screenshot to bitmap.");
                }

                mLoadedData = Pair.create(mImageUri, result);
            }
        }).whenCompleteAsync((res, err) -> {
            mViewHolder.setImageBitmap(mLoadedData.second);
            if (mViewHolder.getRootView().isAttachedToWindow()) {
                mWindowManager.removeViewImmediate(mViewHolder.getRootView());
            }
            mWindowManager.addView(mViewHolder.getRootView(), mLayoutParams);
        }, Executors.mainThread());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START: {
                    mImageUri = intent.getData();
                    mImageFile = (File) intent.getSerializableExtra(EXTRA_FILE);
                    mNotificationKey = intent.getStringExtra(EXTRA_NOTIFICATION_KEY);
                    startPreview();
                    break;
                }
                case ACTION_STOP: {
                    stopSelf();
                    break;
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        if (mViewHolder.getRootView().isAttachedToWindow()) {
            mWindowManager.removeViewImmediate(mViewHolder.getRootView());
        }
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class ViewHolder {

        private final PreviewService mService;

        private final View mRootView, mViewContainer;
        private final ImageView mImageView;
        private final LinearLayout mButtonBar;
        private final View mEditButton, mShareButton, mDeleteButton;

        private final PreviewActionForegroundDrawable mForeground;

        private final ArisuGestureListener mGestureListener;

        private final ColorMatrix mColorMatrix;
        private final float[] mColorMatrixArray;

        private boolean mExpanded;

        private float mScale = 0.5f;
        private float mExpandedScale = 0.8f;

        private int mRawImageWidth, mRawImageHeight;

        @SuppressLint("ClickableViewAccessibility")
        ViewHolder(@NonNull PreviewService service, @NonNull View rootView) {
            mService = service;
            mRootView = rootView;
            mViewContainer = mRootView.findViewById(R.id.view_container);
            mImageView = mRootView.findViewById(R.id.image_view);
            mButtonBar = mRootView.findViewById(R.id.button_bar);
            mEditButton = mRootView.findViewById(R.id.edit_button);
            mShareButton = mRootView.findViewById(R.id.share_button);
            mDeleteButton = mRootView.findViewById(R.id.delete_button);
            mExpanded = false;

            mForeground = new PreviewActionForegroundDrawable(getContext());
            mViewContainer.setForeground(mForeground);

            mColorMatrix = new ColorMatrix();
            mColorMatrixArray = new float[] {
                    1f, 0, 0, 0, 0,
                    0, 1f, 0, 0, 0,
                    0, 0, 1f, 0, 0,
                    0, 0, 0, 1f, 0
            };
            setColorPercent(1f);

            mGestureListener = new ArisuGestureListener(this);
            final GestureDetector gestureDetector = new GestureDetector(rootView.getContext(), mGestureListener);
            mImageView.setOnClickListener(v -> toggleExpandState());
            mImageView.setOnTouchListener((v, event) -> {
                if (MotionEvent.ACTION_UP == event.getActionMasked()) {
                    if (mGestureListener.onUp(event)) {
                        return true;
                    }
                }
                return gestureDetector.onTouchEvent(event);
            });
        }

        @NonNull
        Context getContext() {
            return mRootView.getContext();
        }

        @NonNull
        View getRootView() {
            return mRootView;
        }

        @NonNull
        View getViewContainer() {
            return mViewContainer;
        }

        void setImageBitmap(@NonNull Bitmap bitmap) {
            mRawImageWidth = bitmap.getWidth();
            mRawImageHeight = bitmap.getHeight();
            mImageView.setImageBitmap(bitmap);
            setImageViewExpandState(mExpanded);
        }

        void toggleExpandState() {
            mExpanded = !mExpanded;
            setImageViewExpandState(mExpanded);
            if (mExpanded) {
                mButtonBar.setVisibility(View.VISIBLE);
            } else {
                mButtonBar.setVisibility(View.GONE);
            }
        }

        void setImageViewSize(int width, int height) {
            final ViewGroup.LayoutParams layoutParams = mImageView.getLayoutParams();
            layoutParams.width = width;
            layoutParams.height = height;
            mImageView.setLayoutParams(layoutParams);
        }

        void setImageViewExpandState(boolean expanded) {
            setImageViewSize(
                    (int) (mRawImageWidth * (expanded ? mExpandedScale : mScale)),
                    (int) (mRawImageHeight * (expanded ? mExpandedScale : mScale))
            );
        }

        void setButtonsEnabled(boolean enabled) {
            mEditButton.setEnabled(enabled);
            mShareButton.setEnabled(enabled);
            mDeleteButton.setEnabled(enabled);
        }

        void setColorPercent(float percent) {
            float scale = percent + 1f;
            float translate = (-0.5f * scale + 0.5f) * 255f;
            mColorMatrixArray[0] = scale;
            mColorMatrixArray[5 + 1] = scale;
            mColorMatrixArray[10 + 2] = scale;
            mColorMatrixArray[4] = translate;
            mColorMatrixArray[5 + 4] = translate;
            mColorMatrixArray[10 + 4] = translate;
            mColorMatrix.set(mColorMatrixArray);
            mColorMatrix.setSaturation(percent);
            mImageView.setColorFilter(new ColorMatrixColorFilter(mColorMatrix));
        }

        float getColorPercent() {
            return mColorMatrixArray[0] - 1f;
        }

        void setForegroundProgress(float percent) {
            mForeground.setProgress(percent);
        }

        float getForegroundProgress() {
            return mForeground.getProgress();
        }

        private static class ArisuGestureListener extends GestureDetector.SimpleOnGestureListener {

            static final float DEFAULT_DISMISS_PERCENT_THRESHOLD = 0.5f;
            static final float DEFAULT_OPEN_PERCENT_THRESHOLD = 1f;

            private static final int SCROLL_DIRECTION_UP = 0;
            private static final int SCROLL_DIRECTION_DOWN = 1;

            @IntDef({SCROLL_DIRECTION_DOWN, SCROLL_DIRECTION_UP})
            @Retention(RetentionPolicy.SOURCE)
            private @interface ScrollDirection {}

            private final ViewHolder mParent;

            private boolean isScrolling = false;
            @ScrollDirection
            private int scrollDirection = 0;
            private float progress = 0f;

            ArisuGestureListener(@NonNull ViewHolder parent) {
                mParent = parent;
            }

            @Override
            public boolean onScroll(MotionEvent downEvent,
                                    MotionEvent currentEvent,
                                    float distanceX, float distanceY) {
                mParent.setButtonsEnabled(false);

                isScrolling = true;
                final float totalDistance = currentEvent.getY() - downEvent.getY();
                final View view = mParent.getRootView();
                final float lastProgress = progress;
                if (totalDistance > 0) {
                    // Set direction
                    scrollDirection = SCROLL_DIRECTION_DOWN;

                    // Restore irrelevant view states
                    view.setTranslationX(0);
                    mParent.setForegroundProgress(0);

                    // Set up view states
                    view.setTranslationY(totalDistance);
                    progress = totalDistance / view.getHeight();
                    view.setAlpha(1 - Math.min(progress, 0.5f) / 0.5f);
                    mParent.setColorPercent(1 - Math.min(progress, 0.3f) / 0.3f);
                } else {
                    // Set direction
                    scrollDirection = SCROLL_DIRECTION_UP;

                    // Restore irrelevant view states
                    view.setTranslationX(0);
                    view.setTranslationY(0);
                    view.setAlpha(1f);
                    mParent.setColorPercent(1f);

                    // Set up view states
                    progress = Math.abs(totalDistance) / view.getHeight();
                    mParent.setForegroundProgress(Math.min(progress, DEFAULT_OPEN_PERCENT_THRESHOLD));

                    // Haptic feedback
                    if ((progress >= DEFAULT_OPEN_PERCENT_THRESHOLD && lastProgress < DEFAULT_OPEN_PERCENT_THRESHOLD)
                            || (progress < DEFAULT_OPEN_PERCENT_THRESHOLD
                            && lastProgress >= DEFAULT_OPEN_PERCENT_THRESHOLD)) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                }
                return true;
            }

            public boolean onUp(@NonNull MotionEvent event) {
                mParent.setButtonsEnabled(true);

                if (isScrolling) {
                    isScrolling = false;
                    if (SCROLL_DIRECTION_DOWN == scrollDirection && progress >= DEFAULT_DISMISS_PERCENT_THRESHOLD) {
                        mParent.getContext().stopService(createStopIntent());
                    } else if (SCROLL_DIRECTION_UP == scrollDirection && progress >= DEFAULT_OPEN_PERCENT_THRESHOLD) {
                        mParent.getContext().stopService(createStopIntent());
                        mParent.getContext().startActivity(
                                IntentUtils.createViewIntent(mParent.mService.mImageUri)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        );
                    } else {
                        restoreViewStates();
                    }
                    return true;
                }

                return false;
            }

            private void restoreViewStates() {
                final View view = mParent.getRootView();
                final float lastColorPercent = mParent.getColorPercent();
                final float lastForegroundProgress = mParent.getForegroundProgress();
                view.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setUpdateListener(animation -> {
                            final float fraction = animation.getAnimatedFraction();
                            mParent.setColorPercent(Math.min(1f, lastColorPercent + 1f * fraction));
                            mParent.setForegroundProgress(Math.max(0f, lastForegroundProgress - 1f * fraction));
                        }).start();
                progress = 0;
            }

        }

    }

}
