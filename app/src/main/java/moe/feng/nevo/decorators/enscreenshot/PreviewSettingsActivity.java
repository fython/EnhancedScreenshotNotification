package moe.feng.nevo.decorators.enscreenshot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import moe.feng.nevo.decorators.enscreenshot.utils.PermissionUtils;
import moe.feng.nevo.decorators.enscreenshot.widget.SwitchBar;

import java.util.Objects;

import static moe.feng.nevo.decorators.enscreenshot.ScreenshotPreferences.PREVIEW_TYPE_ARISU;
import static moe.feng.nevo.decorators.enscreenshot.ScreenshotPreferences.PREVIEW_TYPE_NONE;
import static moe.feng.nevo.decorators.enscreenshot.ScreenshotPreferences.PREVIEW_TYPE_PIP;

public class PreviewSettingsActivity extends Activity implements SwitchBar.OnCheckedChangeListener {

    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1;

    private ScreenshotPreferences mPreferences;

    private SwitchBar mSwitchBar;
    private RadioButton mArisuRadio, mPiPRadio;
    private LinearLayout mArisuLayout, mPiPLayout;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mPreferences = new ScreenshotPreferences(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_settings);

        Objects.requireNonNull(getActionBar()).setDisplayHomeAsUpEnabled(true);

        mSwitchBar = findViewById(R.id.switch_bar);
        mArisuRadio = findViewById(R.id.radio_button_arisu_mode);
        mPiPRadio = findViewById(R.id.radio_button_pip_mode);
        mArisuLayout = findViewById(R.id.choice_arisu_mode);
        mPiPLayout = findViewById(R.id.choice_pip_mode);

        mSwitchBar.setOnCheckedChangeListener(this);
        mArisuLayout.setOnClickListener(v -> {
            if (!PermissionUtils.canDrawOverlays(this)) {
                PermissionUtils.requestOverlayPermission(this, REQUEST_CODE_OVERLAY_PERMISSION);
                return;
            }
            mArisuRadio.setChecked(true);
            mPiPRadio.setChecked(false);
            mPreferences.setPreviewType(PREVIEW_TYPE_ARISU);
        });
        mPiPLayout.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    !getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.pref_preview_in_floating_window_summary_unsupported)
                        .setNegativeButton(android.R.string.ok, null)
                        .show();
                return;
            }
            mArisuRadio.setChecked(false);
            mPiPRadio.setChecked(true);
            mPreferences.setPreviewType(PREVIEW_TYPE_PIP);
        });

        if (savedInstanceState == null) {
            mSwitchBar.setChecked(mPreferences.getPreviewType() != PREVIEW_TYPE_NONE);
        }

        mArisuRadio.setChecked(mPreferences.getPreviewType() == PREVIEW_TYPE_ARISU);
        mPiPRadio.setChecked(mPreferences.getPreviewType() == PREVIEW_TYPE_PIP);

        setChoicesEnabled(mSwitchBar.isChecked());
    }

    private void setChoicesEnabled(boolean enabled) {
        mArisuLayout.setEnabled(enabled);
        mPiPLayout.setEnabled(enabled);
    }

    @Override
    public void onBackPressed() {
        if (mSwitchBar.isChecked() && mPreferences.getPreviewType() == PREVIEW_TYPE_NONE) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.pref_preview_type_dialog_not_save)
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> super.onBackPressed())
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (REQUEST_CODE_OVERLAY_PERMISSION == requestCode && RESULT_OK == resultCode) {
            if (PermissionUtils.canDrawOverlays(this)) {
                mArisuLayout.performClick();
            }
        }
    }

    @Override
    public void onCheckedChanged(@NonNull SwitchBar view, boolean isChecked) {
        if (isChecked) {
            if (mPreferences.getPreviewType() == PREVIEW_TYPE_NONE) {
                mPiPRadio.setChecked(false);
                mArisuRadio.setChecked(false);
            }
            setChoicesEnabled(true);
        } else {
            mPiPRadio.setChecked(false);
            mArisuRadio.setChecked(false);
            mSwitchBar.setChecked(false);
            setChoicesEnabled(false);
            mPreferences.setPreviewType(PREVIEW_TYPE_NONE);
        }
    }

}
