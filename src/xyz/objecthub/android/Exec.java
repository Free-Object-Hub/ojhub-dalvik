package xyz.objecthub.android;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Фон и UI. AsyncTask не используем.
 * Два пула, чтобы зависшие запросы к API (connect 15 с + read 20 с) не
 * останавливали картинки, и наоборот:
 *   NET — Http к openGo, парсинг JSON.
 *   IMG — загрузка и декодирование картинок (ImageLoader).
 * Только главный поток трогает View: результат уходит через UI.post.
 */
public final class Exec {
    public static final Executor NET = Executors.newFixedThreadPool(2);
    public static final Executor IMG = Executors.newFixedThreadPool(2);
    public static final Handler UI = new Handler(Looper.getMainLooper());

    private Exec() {}
}
