package xyz.objecthub.android;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.StrictMode;
import android.util.Log;

/**
 * onCreate почти пустой. Тяжёлое — лениво.
 * appCtx — application context, не Activity. Его можно держать статикой.
 */
public class App extends Application {
    public static Context appCtx;

    @Override
    public void onCreate() {
        super.onCreate();
        appCtx = getApplicationContext();

        // VirtualLink — чистая Java (гоняется javac без SDK), лог подставляем снаружи.
        VirtualLink.log = new VirtualLink.Log() {
            @Override
            public void w(String msg, Throwable t) {
                Log.w("ojhub", msg, t);
            }
        };

        // Только в debug-сборке (build.sh с DEBUG=1 ставит debuggable).
        // Нарушения идут в logcat: фильтр "StrictMode". Одна запись Prefs
        // в onStop здесь ожидаема (commit осознанно синхронный).
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites().detectNetwork()
                    .penaltyLog().build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks().detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog().build());
        }
    }

    /** Всё состояние одной пачкой: один commit вместо десятка. Только UI-поток. */
    public static void saveAll() {
        Prefs.Batch b = Prefs.batch();
        AppState.write(b);
        JailRuntime.write(b);
        b.commit();
    }
}
