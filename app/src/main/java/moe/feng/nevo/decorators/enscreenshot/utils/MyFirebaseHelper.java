package moe.feng.nevo.decorators.enscreenshot.utils;

import com.google.android.gms.vision.barcode.Barcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;

import java.lang.reflect.Field;

public final class MyFirebaseHelper {

    private MyFirebaseHelper() {}

    private static final Field sField_FirebaseVisionBarcode_barcode;

    static {
        Field _barcode = null;
        for (Field field : FirebaseVisionBarcode.class.getDeclaredFields()) {
            if (field.getType().isAssignableFrom(Barcode.class)) {
                field.setAccessible(true);
                _barcode = field;
                break;
            }
        }
        if (_barcode == null) {
            throw new NullPointerException("Cannot find Barcode field of FirebaseVisionBarcode");
        }
        sField_FirebaseVisionBarcode_barcode = _barcode;
    }

    public static Barcode getBarcode(FirebaseVisionBarcode firebaseBarcode) {
        try {
            return (Barcode) sField_FirebaseVisionBarcode_barcode.get(firebaseBarcode);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
