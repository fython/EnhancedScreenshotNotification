package moe.feng.nevo.decorators.enscreenshot.utils;

import android.content.Context;
import android.os.Build;
import android.os.LocaleList;
import android.util.DisplayMetrics;
import androidx.annotation.NonNull;

public final class DeviceInfoPrinter {

    private static final String FORMAT = "" +
            "Manufacturer: " + Build.MANUFACTURER + "\n" +
            "Model: " + Build.MODEL + "\n" +
            "Android Version: " + Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")\n" +
            "Screen resolution: " + "{screenWidth}*{screenHeight}" + "\n" +
            "Language: " + LocaleList.getDefault().toString() + "\n" +
            "";

    public static final String DIVIDER = "---- Device information can help us easier to improve app ----\n";

    private DeviceInfoPrinter() {
        throw new InstantiationError();
    }

    @NonNull
    public static String print(@NonNull Context context) {
        final DisplayMetrics realDm = ScreenUtils.getDefaultDisplayRealMetrics(context);

        final String result = FORMAT.replace("{screenWidth}", String.valueOf(realDm.widthPixels))
                .replace("{screenHeight}", String.valueOf(realDm.heightPixels));

        return result;
    }

}
