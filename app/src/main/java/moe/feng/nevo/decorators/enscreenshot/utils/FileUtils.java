package moe.feng.nevo.decorators.enscreenshot.utils;

import android.util.Log;
import android.webkit.MimeTypeMap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public final class FileUtils {

    private static final MimeTypeMap sMimeTypeMap = MimeTypeMap.getSingleton();

    private FileUtils() {
        throw new InstantiationError("Cannot instantiate class FileUtils");
    }

    public static boolean ensureDirectory(@NonNull File dir) {
        if (dir.isFile() && !dir.delete()) {
            return false;
        } else if (dir.isDirectory()) {
            return true;
        }
        return dir.mkdirs();
    }

    public static boolean moveFile(@NonNull File source, @NonNull File target) {
        try {
            if (source.renameTo(target)) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!target.exists()) {
            try {
                target.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try (FileChannel outputChannel = new FileOutputStream(target).getChannel();
             FileChannel inputChannel = new FileInputStream(source).getChannel()) {
            inputChannel.transferTo(0, inputChannel.size(), outputChannel);
        } catch (IOException ignored) {
            return false;
        }
        if (!source.delete()) {
            Log.d("FileUtils", "source failed to delete");
        }
        return true;
    }

    @Nullable
    public static String getMimeTypeFromFile(@NonNull File file) {
        return sMimeTypeMap.getMimeTypeFromExtension(getExtensionFromFileName(file.getName()));
    }

    @Nullable
    public static String getMimeTypeFromFileName(@NonNull String file) {
        return sMimeTypeMap.getMimeTypeFromExtension(getExtensionFromFileName(file));
    }

    @Nullable
    public static String getExtensionFromFileName(@NonNull String name) {
        if (name.contains(".")) {
            return name.substring(name.lastIndexOf(".") + 1);
        }
        return null;
    }

}
