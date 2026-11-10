package xyz.objecthub.android;

import android.app.ActionBar;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * Данные одного джейла. root есть только пока джейл активен.
 * Поворот, смена языка и возврат на джейл — один и тот же createRoot.
 * context — Activity, его обновляет JailRuntime и обнуляет в onDestroy.
 */
// блять что за идиот разделил джейлы на 2 файла?
public final class JailContext {
	public final int id;
	public Activity context;
	public ViewGroup root;
	/** Куда страницы монтируют содержимое. Аналог J.helperMain. */
	public FrameLayout helperMain;
	public final VirtualLink link;

	/** [method, tags, platforms, channel, custom]. Как helperFindData в createJail. */
	public JSONArray helperFindData;
	/** [channel, cache, query, page]. Тело cache в Prefs не пишем. */
	public JSONArray cacheFinds;
	public int globalWiki;
	public int guideEditorFrame;
	public int projectsChannel = 1;
	/**
	 * В вебе стартует строкой "getShow(jId,117)". Пока страниц нет — пусто,
	 * магический 117 не тащим.
	 */
	// имхо эта строка является пережитком GDPS Helper прошлого, не думал что она доберётся аж до сюда
	public String lastUsedProfile = "";
	public int lastChannel;
	public int headerPhoneSwitcher;
	public int scrollY;
	/**
	 * Вертикально прокручиваемый View текущей страницы (ScrollView).
	 * Страница регистрирует его через setScroller. Для ListView getScrollY()
	 * всегда 0: там нужна позиция первого видимого элемента, добавим
	 * вместе с первым списком.
	 */
	public View scroller;
	private boolean scrollPending;

	public JailContext(int id) {
		this.id = id;
		this.link = new VirtualLink();
		this.helperFindData = defaultFind();
		this.cacheFinds = defaultCache();
		bind(this);
		this.link._init("?");
	}

	public View findViewById(int viewId) {
		if (root == null) return null;
		return root.findViewById(viewId);
	}

	/** Аналог J.q / J.id: искать негде, если рут снят. */
	// разве портировать DOM модель 1 в 1 на Android Views - хорошая идея? чую это будет пиздец и я потом буду на стримах крыть всё матом
	public View q(int viewId) {
		return findViewById(viewId);
	}

	/**
	 * Контракт страниц: createRoot зовёт link.get(), то есть функцию страницы,
	 * при КАЖДОМ показе (смена стола, поворот, новая Activity). Страница
	 * обязана рисовать из данных джейла (cacheFinds и т.п.) и идти в сеть
	 * только если данных нет, иначе каждое переключение даст запрос.
	 */
	// а в readme написать нельзя было? блять ты даже ридми не создал говна кусок
	public void createRoot(ViewGroup host) {
		destroyRoot();
		if (context == null || host == null) return;
		scrollPending = scrollY > 0;

		helperMain = new FrameLayout(context);
		helperMain.setLayoutParams(new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		root = helperMain;
		host.addView(root);

		final JailContext self = this;
		link.onRender = new VirtualLink.Render() {
			@Override
			public void onRender(VirtualLink vl) {
				// Колбэк мог прийти после destroyRoot. Чужую Activity не красим.
				if (self.root == null) return;
				self.paintSubtitle();
			}
		};
		link._render();

		// _i true: страница внутри get() не пушит адрес, он уже в стеке.
		link._i = true;
		try {
			link.get();
		} catch (RuntimeException e) {
			// Веб пробрасывает ошибку маршрута после basePage.
			// На onCreate это убило бы Activity, поэтому только лог.
			// а как логи вообще смотреть тут? Мне клод удалил gradle потому что он знает что android studio ни один из моих пяти компов не потянет
			Log.e("ojhub", "link.get j" + id + " " + link.search(), e);
		}
	}

	public void destroyRoot() {
		captureState(); // до того, как View уйдут: иначе позиция потеряна
		link.onRender = null;
		clearSubtitle();
		ViewGroup dead = root;
		dropViews();
		if (dead == null) return;
		ViewParent parent = dead.getParent();
		if (parent instanceof ViewGroup) {
			((ViewGroup) parent).removeView(dead);
		}
	}

	/** Снять ссылки на View. Сами View не трогает: их владелец уже умер или удалён. */
	void dropViews() {
		root = null;
		helperMain = null;
		scroller = null;
	}

	/** Снять состояние с живых View в поля данных. Зови перед save и перед destroyRoot. */
	public void captureState() {
		if (root != null && scroller != null) scrollY = scroller.getScrollY();
	}

	/**
	 * Страница отдаёт свой скроллер. Сохранённая позиция применяется один раз
	 * после createRoot (возврат на стол, поворот). Новая страница по навигации
	 * начинается с нуля. Если контент ещё не пришёл, scrollTo упрётся в 0:
	 * для асинхронных страниц зови setScroller после mount данных.
	 */
	// а вот это уже клод мне навайбил
	public void setScroller(final View v) {
		scroller = v;
		if (v == null) return;
		if (scrollPending) {
			scrollPending = false;
			final int y = scrollY;
			v.post(new Runnable() {
				@Override
				public void run() {
					if (scroller == v) v.scrollTo(0, y);
				}
			});
		} else {
			scrollY = 0;
		}
	}

	/** Адрес джейла — subtitle системного ActionBar, не своя шапка. */
	private void paintSubtitle() {
		ActionBar bar = context == null ? null : context.getActionBar();
		if (bar == null) return;
		String addr = link.search();
		bar.setSubtitle(addr == null || "?".equals(addr) ? null : addr);
	}

	private void clearSubtitle() {
		ActionBar bar = context == null ? null : context.getActionBar();
		if (bar != null) bar.setSubtitle(null);
	}

	/**
	 * Пустой маршрут, пока нет pageMain. set('') снимает _i,
	 * как это делает страница в GHE после отрисовки.
	 * JITlink из nhConfig.js повесим, когда поедут сами страницы.
	 */
	// стоп нахуя вообще андроид приложению роутер?
	// БЛЯЯЯ это и есть буквально link.actions
	// КАКОГО ХУЯ МНЕ НИКТО ЭТО НЕ СКАЗАЛ БЛЯТЬ
	private static void bind(final JailContext J) {
		// э а где if renderBeta => defTitle = 'OJHUB BUILD' + date
		J.link.defTitle = "Object Hub";
		J.link.basePage = new Runnable() {
			@Override
			public void run() {
				J.link.set("");
				J.clearMain();
			}
		};

		// это моя главная страница бля
		J.link.actions.put("", new VirtualLink.Call() {
			@Override
			public void call(Object value) {
				PageMain.pageMain(J.id);
			}
		});

		J.link.actions.put("news", new VirtualLink.Call() {
			@Override
			public void call(Object value) {
				PageNews.pageNews(J.id);
			}
		});

		// ещё грок не позаботился о моей навигации в виме, мне неудобно прыгать на { и }
	}

	private void clearMain() {
		if (helperMain == null) return;
		helperMain.removeAllViews();
	}

	// дальше идут сбросы состояний

	static JSONArray defaultFind() {
		JSONArray a = new JSONArray();
		a.put(0);
		a.put(new JSONArray());
		a.put(new JSONArray());
		a.put(1);
		a.put("");
		return a;
	}

	static JSONArray defaultCache() {
		JSONArray a = new JSONArray();
		a.put(1);
		a.put("");
		a.put("");
		a.put(1);
		return a;
	}

	/** Канал, строка поиска и страница. Слот кэша пустой: это кэш, не состояние. */
	JSONArray cacheForDisk() {
		JSONArray src = cacheFinds == null ? defaultCache() : cacheFinds;
		JSONArray out = new JSONArray();
		out.put(src.optInt(0, 1));
		out.put("");
		out.put(src.optString(2, ""));
		out.put(src.optInt(3, 1));
		return out;
	}

	static JSONArray copyOr(JSONArray src, JSONArray fallback) {
		if (src == null) return fallback;
		try {
			return new JSONArray(src.toString());
		} catch (JSONException e) {
			return fallback;
		}
	}
}
