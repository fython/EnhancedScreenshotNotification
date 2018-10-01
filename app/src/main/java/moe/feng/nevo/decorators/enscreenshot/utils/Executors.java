package moe.feng.nevo.decorators.enscreenshot.utils;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

import android.os.Message;
import androidx.annotation.NonNull;

public final class Executors {

    private static final Executor MAIN_THREAD_EXECUTOR = new AsyncLooperExecutor(Looper.getMainLooper());

    @NonNull
    public static Executor mainThread() {
        return MAIN_THREAD_EXECUTOR;
    }

    private Executors() {
        throw new InstantiationError("Cannot instantiate class Executors");
    }

    private static final class AsyncLooperExecutor implements Executor {

        private final Handler mHandler;

        AsyncLooperExecutor(@NonNull Looper looper) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mHandler = Handler.createAsync(looper);
            } else {
                mHandler = new Handler(looper);
            }
        }

        @Override
        public void execute(@NonNull Runnable command) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mHandler.post(command);
            } else {
                Message message = Message.obtain(mHandler, command);
                message.setAsynchronous(true);
                mHandler.sendMessage(message);
            }
        }

    }

    public static class HandlerExecutor implements Executor {

        private final Handler mHandler;

        public HandlerExecutor(@NonNull Handler handler) {
            mHandler = handler;
        }

        @Override
        public void execute(@NonNull Runnable command) {
            mHandler.post(command);
        }
    }
}
