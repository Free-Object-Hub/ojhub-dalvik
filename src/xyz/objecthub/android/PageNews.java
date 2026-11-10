package xyz.objecthub.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * Читалка новостей. Как globalNews в GHE: просто функция.
 * Запрос, разбор, отрисовка. Ни кеша, ни глобалов, ни полей джейла.
 */
public final class PageNews {
    private PageNews() {}

    // ЗАПОЛНИ по ojhub.js: имя эндпоинта и индексы полей новости.
    // sData[11] — база news. Не угадывай, сверяй с RenderNews.
	// MIODOMB: клайд... ты слишком оптимистичный, скажи это /content/newsAll.php
    private static final String ENDPOINT = "newsAll";
    private static final int F_TITLE = 1;
    private static final int F_TEXT = 2;

    public static void pageNews(final int jId) {
        JailContext J0 = JailRuntime.live(jId);
        if (J0 == null) return;

        J0.link.set("news");          // страница сама пушит адрес, как в GHE
        text(J0, AppState.sData[0] + ENDPOINT + AppState.php);

        Http.cached(AppState.sData[0] + ENDPOINT + AppState.php, new Http.Done() {
            @Override
            public void done(String data) {
                JailContext J = JailRuntime.live(jId);   // нет джейла или root снят: рисовать некуда
                if (J == null) return;
                // пока шёл запрос, могли уйти на другую страницу: не затираем её
                if (!J.link.search().startsWith("?news")) return;

                if (data == null) { text(J, "network"); return; }          // аналог .catch
                if (!data.startsWith("[")) { text(J, "proto: " + data); return; }  // числовой код строкой
                try {
					// клайд ещё не сделал рендер похоже
                    mount(J, new JSONArray(data));
                } catch (JSONException e) {
                    text(J, "json: " + e.getMessage());
                }
            }
        });
    }

    private static void mount(JailContext J, JSONArray data) {
        J.helperMain.removeAllViews();
        ListView list = new ListView(J.context);
        list.setAdapter(new Adapter(J.context, data));
        J.helperMain.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static void text(JailContext J, String s) {
        J.helperMain.removeAllViews();
        TextView t = new TextView(J.context);
        t.setText(s);
        J.helperMain.addView(t);
    }

    /** ListView + convertView + Holder: View только для видимых строк. */
    private static final class Adapter extends BaseAdapter {
        private final Context cx;
        private final JSONArray data;

        Adapter(Context cx, JSONArray data) {
            this.cx = cx;
            this.data = data;
        }

        @Override public int getCount() { return data.length(); }
        @Override public Object getItem(int i) { return data.optJSONArray(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            Holder h;
            if (v == null) {
                LinearLayout row = new LinearLayout(cx);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(24, 16, 24, 16);
                h = new Holder();
                h.title = new TextView(cx);
                h.title.setTextSize(18);
                h.text = new TextView(cx);
                row.addView(h.title);
                row.addView(h.text);
                row.setTag(h);
                v = row;
            } else {
                h = (Holder) v.getTag();
            }
            JSONArray n = data.optJSONArray(pos);
            h.title.setText(n == null ? "" : n.optString(F_TITLE, ""));
            h.text.setText(n == null ? "" : n.optString(F_TEXT, ""));
            return v;
        }
    }

    private static final class Holder {
        TextView title, text;
    }
}
