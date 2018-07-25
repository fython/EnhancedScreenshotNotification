package moe.feng.nevo.decorators.enscreenshot;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.util.Objects;
import java.util.Optional;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ScreenshotPreferences {

    private static final String PREF_NAME = "screenshot";

    private static final String KEY_SCREENSHOT_PATH = "screenshot_path";
    private static final String KEY_PREFERRED_EDITOR_COMPONENT = "preferred_component";

    private static final File DEFAULT_SCREENSHOT_PATH =
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Screenshots");

    private final SharedPreferences mSharedPref;

    public ScreenshotPreferences(@NonNull Context context) {
        mSharedPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public String getScreenshotPath() {
        return mSharedPref.getString(KEY_SCREENSHOT_PATH,
                DEFAULT_SCREENSHOT_PATH.getAbsolutePath());
    }

    public Optional<ComponentName> getPreferredEditorComponentName() {
        final String value = mSharedPref.getString(KEY_PREFERRED_EDITOR_COMPONENT, null);
        if (TextUtils.isEmpty(value)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ComponentName.unflattenFromString(value));
    }

    public boolean isPreferredEditorAvailable(@NonNull Context context) {
        final Optional<ComponentName> cn = getPreferredEditorComponentName();
        if (cn.isPresent()) {
            final PackageManager pm = context.getPackageManager();
            try {
                if (Objects.equals(
                        pm.getPackageInfo(cn.get().getPackageName(), 0).packageName,
                        cn.get().getPackageName())) {
                    return true;
                }
            } catch (PackageManager.NameNotFoundException ignored) {

            }
        }
        return false;
    }

    public Optional<CharSequence> getPreferredEditorTitle(@NonNull Context context) {
        final Optional<ComponentName> cn = getPreferredEditorComponentName();
        if (cn.isPresent()) {
            final PackageManager pm = context.getPackageManager();
            try {
                return Optional.of(
                        pm.getActivityInfo(cn.get(), PackageManager.GET_META_DATA).loadLabel(pm)
                );
            } catch (PackageManager.NameNotFoundException ignored) {

            }
        }
        return Optional.empty();
    }

    public void setScreenshotPath(@Nullable String screenshotPath) {
        final SharedPreferences.Editor editor = mSharedPref.edit();
        if (screenshotPath == null) {
            editor.remove(KEY_SCREENSHOT_PATH);
        } else {
            editor.putString(KEY_SCREENSHOT_PATH, screenshotPath);
        }
        editor.apply();
    }

    public void setPreferredEditorComponentName(@Nullable ComponentName componentName) {
        final SharedPreferences.Editor editor = mSharedPref.edit();
        if (componentName == null) {
            editor.remove(KEY_PREFERRED_EDITOR_COMPONENT);
        } else {
            editor.putString(KEY_PREFERRED_EDITOR_COMPONENT, componentName.flattenToString());
        }
        editor.apply();
    }
}
