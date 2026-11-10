package xyz.objecthub.android;

import android.app.Activity;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Map джейлов. Root jId = 0 не особенный: это просто первый lastJid++.
 * Вложенных джейлов нет.
 * Статика держит данные, не View и не Activity. host каждый раз берётся
 * через findViewById у текущего context джейла.
 */
public final class JailRuntime {
	private static final Map<Integer, JailContext> jails = new LinkedHashMap<Integer, JailContext>();
	private static int lastJid;
	private static int activeId = -1;
	/** Процесс жив. После убийства статика пустая, и мы читаем Prefs. */
	private static boolean alive;

	private JailRuntime() {}

	public static JailContext Jexec(int jId) {
		return jails.get(Integer.valueOf(jId));
	}

	public static JailContext get(int jId) {
		return Jexec(jId);
	}

	/**
	 * Для колбэков из фона: джейл жив И у него есть root (иначе null).
	 * Только UI-поток: jails — обычный LinkedHashMap, не потокобезопасен.
	 * Типовой вид: UI.post(() -> { JailContext J = JailRuntime.live(jId);
	 * if (J == null) return; mount(...); }). Нет root — результат кладём
	 * в данные джейла, View не трогаем. jId не переиспользуется (lastJid++),
	 * поэтому опоздавший колбэк не попадёт в чужой джейл.
	 */
	public static JailContext live(int jId) {
		JailContext J = jails.get(Integer.valueOf(jId));
		return J != null && J.root != null ? J : null;
	}

	public static int activeId() {
		return activeId;
	}

	public static JailContext active() {
		return Jexec(activeId);
	}

	public static int count() {
		return jails.size();
	}

	public static List<JailContext> list() {
		return new ArrayList<JailContext>(jails.values());
	}

	// это продолжение рестарта наверно
	public static void onActivityCreate(Activity a) {
		AppState.load();
		if (!alive) {
			restore();
			alive = true;
		}
		for (JailContext J : jails.values()) {
			J.context = a;
			// Иерархия умерла вместе с прошлой Activity. Ссылки не оставляем.
			J.dropViews();
		}
		if (jails.isEmpty()) {
			int id = createJail();
			Jexec(id).context = a;
			activeId = id;
		}
		activate(activeId);
	}

	// я ненавижу джаву за gc и невозможность делать free() идиоты а не солнечные микросистемы
	// хотя за zfs спасибо, беру слова назад sun microsystems
	// TODO: удалить эти поэмы когда станет скучно
	public static void onActivityDestroy(Activity a) {
		for (JailContext J : jails.values()) {
			if (J.context == a) {
				J.link.onRender = null;
				J.context = null;
				J.dropViews();
			}
		}
	}

	// джейлы и тут синхронные. Не пытайтесь делать GHE асинхронным, вам проще будет застрелиться
	public static int createJail() {
		int id = lastJid++;
		JailContext J = new JailContext(id);
		J.context = liveContext();
		jails.put(Integer.valueOf(id), J);
		return id;
	}

	/**
	 * Показ = createRoot из данных. Смена джейла и новая Activity
	 * сходятся сюда. Предыдущий root выбрасывается.
	 */
	// мне кажется что постоянно пересоздавать activity при смене джейла дорого по процессору
	// но вообще я все ещё крайне слаб в нативной разработке, может в ранней экосистему Views
	// наоборот боролись за минимизацию кучи чтобы Dalvik не ебал всем мозги
	public static void activate(int jId) {
		JailContext next = Jexec(jId);
		if (next == null) return;
		if (next.root != null && activeId == jId) {
			refreshSwitcher(next.context);
			return;
		}
		if (activeId != jId) {
			JailContext prev = Jexec(activeId);
			if (prev != null) prev.destroyRoot();
		}
		activeId = jId;
		Activity a = next.context;
		ViewGroup host = a == null ? null : (ViewGroup) a.findViewById(Ids.JAIL_HOST);
		if (host != null && next.root == null) next.createRoot(host);
		refreshSwitcher(a);
		// Не пишем в Prefs: App.saveAll() в onStop сделает это один раз.
	}

	/** Последний джейл не уничтожаем: шелу нечего показать. jId = 0 не привилегия. */
	// бля я когда только тестировал джейлы случайно удалил корневой джейл
	public static void destroyJail(int jId) {
		if (jails.size() <= 1) return;
		JailContext J = Jexec(jId);
		if (J == null) return;
		Activity a = J.context != null ? J.context : liveContext();
		J.destroyRoot();
		J.link._destroy();
		J.context = null;
		jails.remove(Integer.valueOf(jId));
		if (activeId == jId) {
			activeId = -1;
			int fallback = jails.keySet().iterator().next().intValue();
			JailContext next = Jexec(fallback);
			if (next != null && next.context == null) next.context = a;
			activate(fallback);
		} else {
			refreshSwitcher(a);
		}
	}

	/**
	 * Кладёт состояние всех джейлов в пачку, commit делает App.saveAll().
	 * Перед сериализацией снимаем состояние с живых View (scrollY).
	 * MIOBOMB: грок не создавал восстановление scrollY, а клод починил
	 *          а я дармаед ебанный вайбкодю вместо изучения целиком и полностью
	 * TODO: найти аналог MDN для Android Views
	 */
	public static void write(Prefs.Batch b) {
		if (App.appCtx == null) return;
		for (JailContext J : jails.values()) J.captureState();
		JSONObject root = new JSONObject();
		try {
			root.put("v", 1);
			root.put("active", activeId);
			root.put("lastJid", lastJid);
			JSONArray arr = new JSONArray();
			for (JailContext J : jails.values()) {
				JSONObject o = new JSONObject();
				o.put("id", J.id);
				JSONArray stack = new JSONArray();
				List<String> hist = J.link.history();
				for (int i = 0; i < hist.size(); i++) stack.put(hist.get(i));
				o.put("stack", stack);
				o.put("pos", J.link.position());
				o.put("helperFindData", J.helperFindData);
				o.put("cacheFinds", J.cacheForDisk());
				o.put("globalWiki", J.globalWiki);
				o.put("guideEditorFrame", J.guideEditorFrame);
				o.put("projectsChannel", J.projectsChannel);
				o.put("lastUsedProfile", J.lastUsedProfile == null ? "" : J.lastUsedProfile);
				o.put("lastChannel", J.lastChannel);
				o.put("headerPhoneSwitcher", J.headerPhoneSwitcher);
				o.put("scrollY", J.scrollY);
				arr.put(o);
			}
			root.put("jails", arr);
		} catch (JSONException e) {
			Log.e("ojhub", "save jails", e);
			return;
		}
		b.set("Jails", root.toString());
	}

	// то есть если не получается восстановить приложение мы просто крашим всё к хуям
	// и запускаем сессию по новой...
	// бля а тут сериализация джейлов есть, а в веб клиенте её все ещё нет
	private static void restore() {
		String raw = Prefs.get("Jails");
		if (raw == null || raw.length() == 0) return;
		try {
			JSONObject root = new JSONObject(raw);
			JSONArray arr = root.optJSONArray("jails");
			if (arr == null) return;
			int maxId = -1;
			for (int i = 0; i < arr.length(); i++) {
				JSONObject o = arr.getJSONObject(i);
				int id = o.getInt("id");
				JailContext J = new JailContext(id);
				List<String> stack = new ArrayList<String>();
				JSONArray st = o.optJSONArray("stack");
				if (st != null) {
					for (int k = 0; k < st.length(); k++) stack.add(st.optString(k, "?"));
				}
				J.link.restore(stack, o.optInt("pos", 0));
				J.helperFindData = JailContext.copyOr(
						o.optJSONArray("helperFindData"), JailContext.defaultFind());
				J.cacheFinds = JailContext.copyOr(
						o.optJSONArray("cacheFinds"), JailContext.defaultCache());
				J.globalWiki = o.optInt("globalWiki", 0);
				J.guideEditorFrame = o.optInt("guideEditorFrame", 0);
				J.projectsChannel = o.optInt("projectsChannel", 1);
				J.lastUsedProfile = o.optString("lastUsedProfile", "");
				J.lastChannel = o.optInt("lastChannel", 0);
				J.headerPhoneSwitcher = o.optInt("headerPhoneSwitcher", 0);
				J.scrollY = o.optInt("scrollY", 0);
				jails.put(Integer.valueOf(id), J);
				if (id > maxId) maxId = id;
			}
			lastJid = root.optInt("lastJid", maxId + 1);
			if (lastJid <= maxId) lastJid = maxId + 1;
			activeId = root.optInt("active", -1);
			if (Jexec(activeId) == null && !jails.isEmpty()) {
				activeId = jails.keySet().iterator().next().intValue();
			}
		} catch (JSONException e) {
			Log.e("ojhub", "restore jails", e);
			jails.clear();
			activeId = -1;
			lastJid = 0;
		}
	}

	private static void refreshSwitcher(Activity a) {
		if (a == null) return;
		LinearLayout bar = (LinearLayout) a.findViewById(Ids.SWITCHER);
		if (bar == null) return;
		DesktopSwitcher.rebuild(bar, activeId);
	}

	private static Activity liveContext() {
		for (JailContext J : jails.values()) {
			if (J.context != null) return J.context;
		}
		return null;
	}
}
