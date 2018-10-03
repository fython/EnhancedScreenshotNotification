package moe.feng.nevo.decorators.enscreenshot.service;

import android.app.*;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.Pair;
import android.view.*;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.BuildConfig;
import moe.feng.nevo.decorators.enscreenshot.R;
import moe.feng.nevo.decorators.enscreenshot.utils.Executors;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static moe.feng.nevo.decorators.enscreenshot.Constants.*;

public class PreviewService extends Service {

    public static final ComponentName COMPONENT_NAME =
            ComponentName.createRelative(BuildConfig.APPLICATION_ID, PreviewService.class.getName());

    public static final String ACTION_START = BuildConfig.APPLICATION_ID + ".action.START_PREVIEW";
    public static final String ACTION_STOP = BuildConfig.APPLICATION_ID + ".action.STOP_PREVIEW";

    private static final String TAG = PreviewService.class.getSimpleName();

    public static Intent createStartIntent(@NonNull Uri imageUri) {
        final Intent intent = new Intent(ACTION_START);
        intent.setComponent(COMPONENT_NAME);
        intent.setData(imageUri);
        return intent;
    }

    public static Intent createStopIntent() {
        final Intent intent = new Intent(ACTION_STOP);
        intent.setComponent(COMPONENT_NAME);
        return intent;
    }

    private WindowManager mWindowManager;
    private NotificationManager mNotiManager;

    private Notification mForegroundNotification;

    private WindowManager.LayoutParams mLayoutParams;
    private View mRootView;
    private ImageView mImageView;
    private LinearLayout mViewContainer, mButtonBar;

    private boolean mExpanded = false;

    private Uri mImageUri;

    private Pair<Uri, Bitmap> mLoadedData;

    private Future mStartPreviewTask;

    private float mScale = 0.5f;
    private float mExpandedScale = 0.8f;

    @Override
    public void onCreate() {
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

        if (mRootView == null) {
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
        final int margin = getResources().getDimensionPixelSize(R.dimen.floating_window_margin);
        mLayoutParams.x = margin;
        mLayoutParams.y = margin;
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
        mRootView = LayoutInflater.from(this).inflate(R.layout.preview_window_content, null);
        mViewContainer = mRootView.findViewById(R.id.view_container);
        mImageView = mRootView.findViewById(R.id.image_view);
        mButtonBar = mRootView.findViewById(R.id.button_bar);
        mExpanded = false;

        mImageView.setOnClickListener(v -> toggleExpandState());
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
                result = Bitmap.createScaledBitmap(result,
                        (int) (result.getWidth()),
                        (int) (result.getHeight()),
                        false);

                mLoadedData = Pair.create(mImageUri, result);
            }
        }).whenCompleteAsync((res, err) -> {
            mImageView.setImageBitmap(mLoadedData.second);
            setImageViewExpandState(mExpanded);
            if (mRootView.isAttachedToWindow()) {
                mWindowManager.removeViewImmediate(mRootView);
            }
            mWindowManager.addView(mRootView, mLayoutParams);
        }, Executors.mainThread());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START: {
                    mImageUri = intent.getData();
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
        mWindowManager.removeViewImmediate(mRootView);
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void toggleExpandState() {
        mExpanded = !mExpanded;
        // final AutoTransition autoTransition = new AutoTransition();
        // autoTransition.setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime) / 2);
        // TransitionManager.beginDelayedTransition(mViewContainer, autoTransition);
        setImageViewExpandState(mExpanded);
        if (mExpanded) {
            mButtonBar.setVisibility(View.VISIBLE);
        } else {
            mButtonBar.setVisibility(View.GONE);
        }
    }

    private void setImageViewSize(int width, int height) {
        final ViewGroup.LayoutParams layoutParams = mImageView.getLayoutParams();
        layoutParams.width = width;
        layoutParams.height = height;
        mImageView.setLayoutParams(layoutParams);
    }

    private void setImageViewExpandState(boolean expanded) {
        setImageViewSize(
                (int) (mLoadedData.second.getWidth() * (expanded ? mExpandedScale : mScale)),
                (int) (mLoadedData.second.getHeight() * (expanded ? mExpandedScale : mScale))
        );
    }

}
