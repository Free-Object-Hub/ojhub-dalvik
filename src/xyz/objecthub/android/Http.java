package xyz.objecthub.android;

import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Сеть. Страницы зовут только Http.go: запрос в фоне (Exec.NET), ответ сырой
 * строкой в UI-поток. Разбор ответа и коды вроде "-4" пишутся прямо в done().
 * req() синхронный, звать только из фона (в UI-потоке бросит исключение).
 *
 * Заголовок user-token кладёт AppState в defaultHeaders, а Http добавляет
 * defaultHeaders только к URL нашего сервера (Api.isOurs). На чужой хост
 * (картинки, внешние ссылки) токен не уходит. Остаточный риск: редирект
 * нашего сервера на чужой хост; сервер так не делает, но помни об этом.
 * Свой TrustManager сознательно не ставим: на API 21–24 системное
 * хранилище сертификатов старое, и если прод objecthub.xyz на 5.x/6.x/7.0
 * не откроется, это надо решать явно, а не прятать.
 */
// нихуя подобного - щас стандарт это звать Http.cached
public final class Http {
	public static final Map<String, String> defaultHeaders = new LinkedHashMap<String, String>();
	public static final int CONNECT_MS = 15000;
	public static final int READ_MS = 20000;

	/** Content-Type для POST с телом формы (как urlEncoded в GHE). */
	public static final Map<String, String> URLENC = new LinkedHashMap<String, String>();
	static {
		URLENC.put("Content-Type", "application/x-www-form-urlencoded");
	}

	/** Аналог .then(data=>...): вызывается в UI-потоке. data == null — сеть упала (.catch). */
	public interface Done {
		void done(String data);
	}

	private Http() {}

	// ---- Кеш GET-ответов. Страницы о нём не знают: звали go, зовут cached. ----
	// Только UI-поток (go и done и так там), поэтому без синхронизации.
	// Ограничен 32 записями (LRU) и живёт CACHE_MS. Только память.
	public static final long CACHE_MS = 60000;
	private static final class Hit {
		final String body;
		final long t = System.currentTimeMillis();
		Hit(String body) { this.body = body; }
	}
	private static final LinkedHashMap<String, Hit> cache =
			new LinkedHashMap<String, Hit>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Hit> e) {
					return size() > 32;
				}
			};

	/** Сбросить кеш: смена токена, кнопка «обновить». Только UI-поток. */
	public static void dropCache() {
		cache.clear();
	}

	/**
	 * GET с кешем. Тот же контракт, что у go: done в UI-потоке, null — сеть упала.
	 * В кеш кладётся только JSON ("[" или "{"): голые числовые коды ошибок
	 * и сырой текст не запоминаются. Любой не-GET через go() сбрасывает кеш целиком.
	 */
	public static void cached(final String url, final Done d) {
		Hit h = cache.get(url);
		if (h != null && System.currentTimeMillis() - h.t < CACHE_MS) {
			final String body = h.body;
			Exec.UI.post(new Runnable() {   // ответ всегда асинхронный, как у сети
				@Override
				public void run() {
					d.done(body);
				}
			});
			return;
		}
		go("GET", url, null, new Done() {
			@Override
			public void done(String data) {
				if (data != null && (data.startsWith("[") || data.startsWith("{"))) {
					cache.put(url, new Hit(data));
				}
				d.done(data);
			}
		});
	}

	public static void go(final String method, final String url, final String body, final Done d) {
		if (!"GET".equals(method)) cache.clear();   // запись: прежние ответы могли устареть

		Exec.NET.execute(new Runnable() {
			@Override
			public void run() {
				String r = null;
				try {
					r = req(method, url, body, body == null ? null : URLENC);
				} catch (IOException e) {
					Log.w("ojhub", method + " " + url + " failed", e);
				}
				final String data = r;
				Exec.UI.post(new Runnable() {
					@Override
					public void run() {
						d.done(data);
					}
				});
			}
		});
	}

	public static String req(String method, String url, String body, Map<String, String> headers)
			throws IOException {
		Looper main = Looper.getMainLooper();
		if (main != null && main == Looper.myLooper()) {
			throw new IllegalStateException("Http on UI thread");
		}

		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		try {
			c.setConnectTimeout(CONNECT_MS);
			c.setReadTimeout(READ_MS);
			c.setInstanceFollowRedirects(true);
			c.setRequestMethod(method);

			Map<String, String> all = new LinkedHashMap<String, String>();
			if (Api.isOurs(url)) {
				synchronized (defaultHeaders) {
					all.putAll(defaultHeaders);
				}
			}
			if (headers != null) all.putAll(headers);
			for (Map.Entry<String, String> h : all.entrySet()) {
				c.setRequestProperty(h.getKey(), h.getValue());
			}

			if (body != null) {
				byte[] bytes = body.getBytes("UTF-8");
				c.setDoOutput(true);
				OutputStream os = c.getOutputStream();
				try {
					os.write(bytes);
				} finally {
					os.close();
				}
			}

			int code = c.getResponseCode();
			InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
			String text = read(in);
			if (code < 200 || code >= 300) {
				String snippet = text.length() > 500 ? text.substring(0, 500) : text;
				throw new IOException(code + " " + c.getResponseMessage() + " " + snippet);
			}
			return text;
		} finally {
			c.disconnect();
		}
	}

	private static String read(InputStream in) throws IOException {
		if (in == null) return "";
		try {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			byte[] tmp = new byte[8192];
			int n;
			while ((n = in.read(tmp)) >= 0) buf.write(tmp, 0, n);
			return buf.toString("UTF-8");
		} finally {
			in.close();
		}
	}
}
