package moe.feng.nevo.decorators.enscreenshot.utils;

import android.content.Context;
import android.util.TypedValue;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import java.util.Objects;

public final class ResourcesUtils {

   private ResourcesUtils() {}

   public static TypedValue resolveAttribute(@NonNull Context context, @AttrRes int attrId) {
       final TypedValue value = new TypedValue();
       if (context.getTheme().resolveAttribute(attrId, value, true)) {
           return value;
       } else {
           return null;
       }
   }

   @ColorInt
   public static int resolveColorAttr(@NonNull Context context, @AttrRes int attrId) {
       return Objects.requireNonNull(resolveAttribute(context, attrId)).data;
   }

}
