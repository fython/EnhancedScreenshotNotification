package moe.feng.nevo.decorators.enscreenshot.utils;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.concurrent.Callable;

class SingletonImpl<T> implements Singleton<T> {

    private final Callable<T> callable;

    private final Object lock = new Object();

    private volatile T value;

    SingletonImpl(@NonNull Callable<T> callable) {
        this.callable = callable;
    }

    @NonNull
    @Override
    public T get() {
        if (isInitialized()) {
            synchronized (lock) {
                if (isInitialized()) {
                    try {
                        value = Objects.requireNonNull(callable.call());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return value;
    }

    @Override
    public boolean isInitialized() {
        return value == null;
    }

}
