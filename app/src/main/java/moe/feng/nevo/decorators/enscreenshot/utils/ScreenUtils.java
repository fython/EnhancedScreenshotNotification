package moe.feng.nevo.decorators.enscreenshot.utils;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Rational;
import android.view.WindowManager;
import androidx.annotation.NonNull;

import java.util.Objects;

public final class ScreenUtils {

    private static Rational sDefaultDisplayRational = null;

    private ScreenUtils() {
        throw new InstantiationError();
    }

    @NonNull
    public static Rational getDefaultDisplayRational(@NonNull Context context) {
        if (sDefaultDisplayRational == null) {
            synchronized (ScreenUtils.class) {
                if (sDefaultDisplayRational == null) {
                    final DisplayMetrics dm = new DisplayMetrics();
                    final WindowManager wm = Objects.requireNonNull(context.getSystemService(WindowManager.class));
                    wm.getDefaultDisplay().getRealMetrics(dm);

                    sDefaultDisplayRational = new Rational(dm.widthPixels, dm.heightPixels);
                }
            }
        }

        return sDefaultDisplayRational;
    }

}
