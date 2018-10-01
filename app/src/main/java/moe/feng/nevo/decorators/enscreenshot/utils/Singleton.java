package moe.feng.nevo.decorators.enscreenshot.utils;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.concurrent.Callable;

public interface Singleton<T> {

    static <T> Singleton<T> by(@NonNull Callable<T> callable) {
        return new SingletonImpl<>(Objects.requireNonNull(callable));
    }

    T get();

    boolean isInitialized();

}
