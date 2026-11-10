package xyz.objecthub.android;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Глобалы, которые джейл не виртуализирует: thisUser, token, sData, язык.
 * Статика после убийства процесса пустая — load() читает Prefs.
 * Токен лежит отдельно в ключе User, как Slocal.get('User'),
 * и уходит заголовком user-token. Внутри thisUser его не считаем секретом сессии.
 */
public final class AppState {
	public static JSONObject thisUser;
	public static String token = "";
	public static String lang = "";
	public static int langVer;


	// sData как в ojhub.js. Индексы не переставлять и не «улучшать».
	// 0 content, 1 send, 2 корень, 3 search, 4 delete, 5 user, 6 forum,
	// 7 wiki, 8 vacans (опечатка сервера), 9 comms, 10 gdps, 11 news, 12 profile.
	// Протокол V133. Задаётся тут, никаких формирующих вызовов.
	private static final String API = "https://objecthub.xyz/server/133";
	public static String[] sData = {
		API + "/content/",
		API + "/send/",
		API + "/",
		API + "/search/",
		API + "/delete/",
		API + "/user/",
		API + "/forum/",
		API + "/wiki/",
		API + "/vacans/",
		API + "/comms/",
		API + "/gdps/",
		API + "/news/",
		API + "/profile/",
	};

	public static String php = ".php";

	private static boolean loaded;

	private AppState() {}

	public static void load() {
		if (loaded) return;

		String rawUser = Prefs.get("StaticUserData");
		if (rawUser == null || rawUser.length() == 0 || "undefined".equals(rawUser)) {
			thisUser = defaultUser();
		} else {
			try {
				thisUser = new JSONObject(rawUser);
			} catch (JSONException e) {
				thisUser = defaultUser();
			}
		}

		String t = Prefs.get("User");
		token = t == null ? "" : t;
		applyTokenHeader();

		String l = Prefs.get("Lang");
		lang = l == null ? "" : l;
		langVer = 0;
		String lv = Prefs.get("LangVer");
		if (lv != null) {
			try {
				langVer = Integer.parseInt(lv);
			} catch (NumberFormatException ignored) {
				langVer = 0;
			}
		}
		loaded = true;
	}

	/** Одиночная запись. При уходе в фон зови App.saveAll(): там одна пачка на всё. */
	public static void save() {
		Prefs.Batch b = Prefs.batch();
		write(b);
		b.commit();
	}

	/** Кладёт состояние в пачку, commit делает вызывающий. */
	public static void write(Prefs.Batch b) {
		if (!loaded) load();
		b.set("StaticUserData", thisUser.toString());
		b.set("User", token == null ? "" : token);
		if (lang != null && lang.length() > 0) b.set("Lang", lang);
		b.set("LangVer", Integer.toString(langVer));
	}

	public static void setToken(String t) {
		token = t == null ? "" : t;
		applyTokenHeader();
		Prefs.set("User", token);
        Http.dropCache();   // ответы были под другим пользователем
	}

	private static void applyTokenHeader() {
		synchronized (Http.defaultHeaders) {
			if (token == null || token.length() == 0) Http.defaultHeaders.remove("user-token");
			else Http.defaultHeaders.put("user-token", token);
		}
	}

	/** Форма гостя из nhConfig.js, пока loginT не пришёл. */
	public static JSONObject defaultUser() {
		JSONObject o = new JSONObject();
		try {
			o.put("username", "Object Hub");
			o.put("ID", 0);
			o.put("role", 0);
			o.put("isActive", 0);
			o.put("hasAlarms", 0);
			o.put("resume", "");
			o.put("socials", "");
			o.put("token", "");
			o.put("cityData", false);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		return o;
	}
}
