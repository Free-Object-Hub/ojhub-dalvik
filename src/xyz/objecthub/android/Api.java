package xyz.objecthub.android;

/** Адрес сервера и версия протокола. Пути (sData) лежат в AppState. */
public final class Api {
    public static final int URL_BUILD = 133;
    public static final String ORIGIN = "https://objecthub.xyz";

    private Api() {}

    /** Наш ли это сервер. user-token уходит только на него. */
    public static boolean isOurs(String url) {
        return url != null && (url.equals(ORIGIN) || url.startsWith(ORIGIN + "/"));
    }
}
