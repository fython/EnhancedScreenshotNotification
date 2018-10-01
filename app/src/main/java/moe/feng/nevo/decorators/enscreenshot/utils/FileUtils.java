package moe.feng.nevo.decorators.enscreenshot.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.MimeTypeMap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Optional;

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

    @Nullable
    public static Uri getImageContentUri(@NonNull Context context, @NonNull File imageFile) {
        String filePath = imageFile.getAbsolutePath();
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Images.Media._ID },
                MediaStore.Images.Media.DATA + "=? ",
                new String[] { filePath }, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor
                        .getColumnIndex(MediaStore.MediaColumns._ID));
                Uri baseUri = Uri.parse("content://media/external/images/media");
                return Uri.withAppendedPath(baseUri, "" + id);
            } else {
                if (imageFile.exists()) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DATA, filePath);
                    return context.getContentResolver().insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                } else {
                    return null;
                }
            }
        }
    }

    @Nullable
    public static File backupScreenshot(@NonNull Context context, @NonNull File file) {
        final File screenshotDir = new File(context.getCacheDir(), "screenshot");
        final File backupFile = new File(screenshotDir, file.getName());

        // Clear old files
        for (File oldFile : Optional.ofNullable(screenshotDir.listFiles()).orElse(new File[0])) {
            if (!oldFile.delete()) {
                Log.d("FileUtils", "Failed to delete " + oldFile.getAbsolutePath());
            }
        }

        // Start backup for sharing
        if (backupFile.exists() && !backupFile.delete()) {
            return null;
        } else if (!FileUtils.ensureDirectory(screenshotDir)) {
            return null;
        } else if (FileUtils.moveFile(file, backupFile)) {
            return backupFile;
        } else {
            return null;
        }
    }

}
