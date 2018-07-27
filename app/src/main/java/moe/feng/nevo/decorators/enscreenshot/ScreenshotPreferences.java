package moe.feng.nevo.decorators.enscreenshot;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.LocaleList;
import android.text.TextUtils;

import androidx.annotation.IntDef;
import moe.feng.nevo.decorators.enscreenshot.utils.FormatUtils;
import net.grandcentrix.tray.TrayPreferences;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.Optional;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ScreenshotPreferences {

    public static final int SHARE_EVOLVE_TYPE_NONE = 0;
    public static final int SHARE_EVOLVE_TYPE_DISMISS_AFTER_SHARING = 1;

    @IntDef({ SHARE_EVOLVE_TYPE_NONE, SHARE_EVOLVE_TYPE_DISMISS_AFTER_SHARING })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ShareEvolveType {}

    private static final String PREF_NAME = "screenshot";

    private static final String KEY_SCREENSHOT_PATH = "screenshot_path";
    private static final String KEY_PREFERRED_EDITOR_COMPONENT = "preferred_component";
    private static final String KEY_SHARE_EVOLVE_TYPE = "share_evolve_type";
    private static final String KEY_EDIT_ACTION_TEXT_FORMAT = "edit_action_text_format";
    private static final String KEY_SHOW_SCREENSHOTS_COUNT = "show_screenshots_count";
    private static final String KEY_SHOW_SCREENSHOT_DETAILS = "show_screenshot_details";

    private static final File DEFAULT_SCREENSHOT_PATH =
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Screenshots");

    private static final ComponentName LAUNCH_COMPONENT_NAME =
            ComponentName.createRelative(BuildConfig.APPLICATION_ID, ".LaunchActivity");

    private final TrayPreferences mPreferences;
    private final PackageManager mPackageManager;

    public ScreenshotPreferences(@NonNull Context context) {
        mPreferences = new TrayPreferences(context, PREF_NAME, 1);
        mPackageManager = context.getPackageManager();
    }

    @NonNull
    public String getScreenshotPath() {
        return Optional.ofNullable(mPreferences.getString(KEY_SCREENSHOT_PATH, null))
                .orElse(DEFAULT_SCREENSHOT_PATH.getAbsolutePath());
    }

    public Optional<ComponentName> getPreferredEditorComponentName() {
        final String value = mPreferences.getString(KEY_PREFERRED_EDITOR_COMPONENT, null);
        if (TextUtils.isEmpty(value)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ComponentName.unflattenFromString(value));
    }

    public boolean isPreferredEditorAvailable() {
        final Optional<ComponentName> cn = getPreferredEditorComponentName();
        if (cn.isPresent()) {
            try {
                if (Objects.equals(
                        mPackageManager.getPackageInfo(
                                cn.get().getPackageName(), 0).packageName,
                        cn.get().getPackageName())) {
                    return true;
                }
            } catch (PackageManager.NameNotFoundException ignored) {

            }
        }
        return false;
    }

    public Optional<CharSequence> getPreferredEditorTitle() {
        final Optional<ComponentName> cn = getPreferredEditorComponentName();
        if (cn.isPresent()) {
            try {
                return Optional.of(
                        mPackageManager.getActivityInfo(cn.get(), PackageManager.GET_META_DATA)
                                .loadLabel(mPackageManager));
            } catch (PackageManager.NameNotFoundException ignored) {

            }
        }
        return Optional.empty();
    }

    public boolean isHideLauncherIcon() {
        return mPackageManager.getComponentEnabledSetting(LAUNCH_COMPONENT_NAME)
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    @ShareEvolveType
    public int getShareEvolveType() {
        return mPreferences.getInt(KEY_SHARE_EVOLVE_TYPE, SHARE_EVOLVE_TYPE_NONE);
    }

    @NonNull
    public String getEditActionTextFormat() {
        return Optional.ofNullable(mPreferences.getString(KEY_EDIT_ACTION_TEXT_FORMAT, null))
                .orElseGet(() -> FormatUtils.getEditActionTextFormats(LocaleList.getDefault()).second.get(1));
    }

    public boolean isShowScreenshotsCount() {
        return mPreferences.getBoolean(KEY_SHOW_SCREENSHOTS_COUNT, false);
    }

    public boolean isShowScreenshotDetails() {
        return mPreferences.getBoolean(KEY_SHOW_SCREENSHOT_DETAILS, false);
    }

    public void setScreenshotPath(@Nullable String screenshotPath) {
        if (screenshotPath == null) {
            mPreferences.remove(KEY_SCREENSHOT_PATH);
        } else {
            mPreferences.put(KEY_SCREENSHOT_PATH, screenshotPath);
        }
    }

    public void setPreferredEditorComponentName(@Nullable ComponentName componentName) {
        if (componentName == null) {
            mPreferences.remove(KEY_PREFERRED_EDITOR_COMPONENT);
        } else {
            mPreferences.put(KEY_PREFERRED_EDITOR_COMPONENT, componentName.flattenToString());
        }
    }

    public void setHideLauncherIcon(boolean shouldHide) {
        mPackageManager.setComponentEnabledSetting(
                LAUNCH_COMPONENT_NAME,
                shouldHide ?
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    public void setShareEvolveType(@ShareEvolveType int type) {
        mPreferences.put(KEY_SHARE_EVOLVE_TYPE, type);
    }

    public void setEditActionTextFormat(@NonNull String format) {
        mPreferences.put(KEY_EDIT_ACTION_TEXT_FORMAT, format);
    }

    public void setShowScreenshotsCount(boolean bool) {
        mPreferences.put(KEY_SHOW_SCREENSHOTS_COUNT, bool);
    }

    public void setShowScreenshotDetails(boolean bool) {
        mPreferences.put(KEY_SHOW_SCREENSHOT_DETAILS, bool);
    }
}
