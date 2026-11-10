package xyz.objecthub.android;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Как Slocal = new _.storage(localStorage, 'oschub').
 * Ключ на диске: "oschub" + key, файл тоже oschub.
 *
 * commit(), не apply(): запись при уходе в фон не должна оставаться
 * «когда-нибудь». Но каждый commit переписывает весь файл, поэтому всё,
 * что пишется вместе (onStop), идёт через Batch: одна пачка, один commit,
 * и только если хоть одно значение реально изменилось.
 */
public final class Prefs {
    public static final String PREFIX = "oschub";

    private Prefs() {}

    private static SharedPreferences sp() {
        Context c = App.appCtx;
        if (c == null) throw new IllegalStateException("App.appCtx is null");
        return c.getSharedPreferences(PREFIX, Context.MODE_PRIVATE);
    }

    public static String get(String key) {
        return sp().getString(PREFIX + key, null);
    }

    public static void set(String key, String value) {
        sp().edit().putString(PREFIX + key, value).commit();
    }

    public static void remove(String key) {
        sp().edit().remove(PREFIX + key).commit();
    }

    public static void clear() {
        sp().edit().clear().commit();
    }

    public static Batch batch() {
        return new Batch();
    }

    /** Пачка записей. UI-поток. Неизменённые значения не пишутся. */
    public static final class Batch {
        private final SharedPreferences sp = sp();
        private final SharedPreferences.Editor ed = sp.edit();
        private boolean dirty;

        Batch() {}

        public void set(String key, String value) {
            String k = PREFIX + key;
            String old = sp.getString(k, null);
            if (value == null ? old == null : value.equals(old)) return;
            if (value == null) ed.remove(k);
            else ed.putString(k, value);
            dirty = true;
        }

        public void commit() {
            if (dirty) ed.commit();
            dirty = false;
        }
    }
}
