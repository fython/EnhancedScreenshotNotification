package moe.feng.nevo.decorators.enscreenshot;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import moe.feng.nevo.decorators.enscreenshot.utils.Executors;
import moe.feng.nevo.decorators.enscreenshot.utils.ScreenUtils;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RequiresApi(Build.VERSION_CODES.O)
public class PreviewActivity extends Activity {

    public static final String EXTRA_SHARE_INTENT = BuildConfig.APPLICATION_ID + ".extra.SHARE_INTENT";
    public static final String EXTRA_DELETE_INTENT = BuildConfig.APPLICATION_ID + ".extra.DELETE_INTENT";
    public static final String EXTRA_EDIT_INTENT = BuildConfig.APPLICATION_ID + ".extra.EDIT_INTENT";
    public static final String EXTRA_NOTIFICATION_KEY = BuildConfig.APPLICATION_ID + ".extra.NOTIFICATION_KEY";

    public static final String ACTION_SHARE = BuildConfig.APPLICATION_ID + ".action.PREVIEW_ACTION_SHARE";
    public static final String ACTION_DELETE = BuildConfig.APPLICATION_ID + ".action.PREVIEW_ACTION_DELETE";
    public static final String ACTION_EDIT = BuildConfig.APPLICATION_ID + ".action.PREVIEW_ACTION_EDIT";

    private static final String TAG = "PreviewActivity";

    private PictureInPictureParams mPIPParams;

    private ImageView mImageView;

    private Uri mImageUri;
    private PendingIntent mShareIntent, mDeleteIntent, mEditIntent;
    private String mNotificationKey;

    private CompletableFuture mImageLoadFuture;

    private final RemoteActionReceiver mRemoteActionReceiver = new RemoteActionReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        if (intent == null || intent.getData() == null) {
            Log.e(TAG, "Intent is null.");
            if (!isFinishing()) {
                finish();
            }
            return;
        }
        mImageUri = intent.getData();
        mShareIntent = intent.getParcelableExtra(EXTRA_SHARE_INTENT);
        mDeleteIntent = intent.getParcelableExtra(EXTRA_DELETE_INTENT);
        mEditIntent = intent.getParcelableExtra(EXTRA_EDIT_INTENT);
        mNotificationKey = intent.getStringExtra(EXTRA_NOTIFICATION_KEY);

        setContentView(R.layout.layout_preview);

        mImageView = findViewById(R.id.image_view);

        if (mPIPParams == null) {
            updatePictureInPictureParams();
        }

        if (!isInPictureInPictureMode()) {
            enterPictureInPictureMode(mPIPParams);
        }

        loadImage();

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_SHARE);
        intentFilter.addAction(ACTION_DELETE);
        intentFilter.addAction(ACTION_EDIT);
        registerReceiver(mRemoteActionReceiver, intentFilter);
    }

    private void loadImage() {
        mImageView.setImageBitmap(null);

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
            if (!isFinishing()) {
                finish();
            }
            return;
        }

        final InputStream is = input;
        if (mImageLoadFuture != null) {
            try {
                mImageLoadFuture.cancel(true);
            } catch (Exception ignored) {

            }
        }
        mImageLoadFuture = CompletableFuture.supplyAsync(() -> BitmapFactory.decodeStream(is))
                .whenCompleteAsync((bitmap, err) -> {
                    if (err != null) {
                        err.printStackTrace();
                        if (!isFinishing()) {
                            finish();
                        }
                        return;
                    }
                    updatePictureInPictureParams(new Rational(bitmap.getWidth(), bitmap.getHeight()));
                    mImageView.setImageBitmap(bitmap);
                }, Executors.mainThread());
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "PreviewActivity: onDestroy");
        super.onDestroy();
        if (mImageLoadFuture != null) {
            mImageLoadFuture.cancel(true);
        }
        sendBroadcast(new Intent(ScreenshotDecorator.ACTION_CANCEL_NOTIFICATION)
                .putExtra("key", mNotificationKey));
        try {
            unregisterReceiver(mRemoteActionReceiver);
        } catch (Exception ignored) {

        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        if (!isInPictureInPictureMode) {
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(mImageUri, "image/*");
            startActivity(intent);
            if (!isFinishing()) {
                finish();
            }
        }
    }

    private void updatePictureInPictureParams() {
        updatePictureInPictureParams(null);
    }

    private synchronized void updatePictureInPictureParams(@Nullable Rational aspect) {
        if (aspect == null) {
            aspect = ScreenUtils.getDefaultDisplayRational(this);
        }

        final List<RemoteAction> remoteActions = new ArrayList<>();

        if (mShareIntent != null) {
            final RemoteAction shareAction = new RemoteAction(
                    Icon.createWithResource(this, R.drawable.ic_share_white_24dp),
                    getString(R.string.action_share_screenshot),
                    getString(R.string.action_share_screenshot),
                    PendingIntent.getBroadcast(this, 0,
                            new Intent(ACTION_SHARE), PendingIntent.FLAG_UPDATE_CURRENT)
            );
            remoteActions.add(shareAction);
        }

        if (mDeleteIntent != null) {
            final RemoteAction deleteAction = new RemoteAction(
                    Icon.createWithResource(this, R.drawable.ic_delete_white_24dp),
                    getString(R.string.action_delete_screenshot),
                    getString(R.string.action_delete_screenshot),
                    PendingIntent.getBroadcast(this, 0,
                            new Intent(ACTION_DELETE), PendingIntent.FLAG_UPDATE_CURRENT)
            );
            remoteActions.add(deleteAction);
        }

        if (mEditIntent != null) {
            final RemoteAction editAction = new RemoteAction(
                    Icon.createWithResource(this, R.drawable.ic_edit_white_24dp),
                    getString(R.string.action_edit),
                    getString(R.string.action_edit),
                    PendingIntent.getBroadcast(this, 0,
                            new Intent(ACTION_EDIT), PendingIntent.FLAG_UPDATE_CURRENT)
            );
            remoteActions.add(editAction);
        }

        mPIPParams = new PictureInPictureParams.Builder()
                .setAspectRatio(aspect)
                .setActions(remoteActions)
                .build();

        if (isInPictureInPictureMode()) {
            setPictureInPictureParams(mPIPParams);
        }
    }

    private final class RemoteActionReceiver extends BroadcastReceiver {

        private static final String TAG = "RemoteActionReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                Log.e(TAG, "onReceive intent should not be null and contain an action.");
                return;
            }

            switch (intent.getAction()) {
                case ACTION_SHARE: {
                    try {
                        mShareIntent.send();
                        if (!isFinishing()) {
                            finish();
                        }
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case ACTION_DELETE: {
                    try {
                        mDeleteIntent.send();
                        if (!isFinishing()) {
                            finish();
                        }
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case ACTION_EDIT: {
                    try {
                        mEditIntent.send();
                        if (!isFinishing()) {
                            finish();
                        }
                    } catch (PendingIntent.CanceledException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

    }

}
