package moe.feng.nevo.decorators.enscreenshot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ScreenshotPreferences {

    private static final String PREF_NAME = "screenshot";

    private static final String KEY_SCREENSHOT_PATH = "screenshot_path";

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

    // TODO: Provide user interface to set
    public void setScreenshotPath(@Nullable String screenshotPath) {
        final SharedPreferences.Editor editor = mSharedPref.edit();
        if (screenshotPath == null) {
            editor.remove(KEY_SCREENSHOT_PATH);
        } else {
            editor.putString(KEY_SCREENSHOT_PATH, screenshotPath);
        }
        editor.apply();
    }
}
