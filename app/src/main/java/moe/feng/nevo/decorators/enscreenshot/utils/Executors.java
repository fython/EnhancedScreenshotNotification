package moe.feng.nevo.decorators.enscreenshot.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

import androidx.annotation.NonNull;

public final class Executors {

    private static final Executor MAIN_THREAD_EXECUTOR =
            new HandlerExecutor(new Handler(Looper.getMainLooper()));

    @NonNull
    public static Executor mainThread() {
        return MAIN_THREAD_EXECUTOR;
    }

    private Executors() {
        throw new InstantiationError("Cannot instantiate class Executors");
    }

    public static final class HandlerExecutor implements Executor {

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
