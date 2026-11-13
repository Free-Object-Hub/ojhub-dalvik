package xyz.objecthub.android;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

public final class PageNews {
	private PageNews() {}

	public static void pageNews(final int jId) {
		JailContext J = JailRuntime.live(jId);
		if (J == null) return;
		J.link.set("news");
		J.helperMain.removeAllViews();

		ScrollView sv = new ScrollView(J.context);

		LinearLayout col = new LinearLayout(J.context);
		col.setOrientation(LinearLayout.VERTICAL);

		sv.addView(col, new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));

		J.helperMain.addView(sv, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		more(jId, col, 0);
	}

	private static void more(final int jId, final LinearLayout col, final int page) {
		Http.cached(AppState.sData[0] + "newsAll" + AppState.php + "?page=" + page, new Http.Done() {
			@Override
			public void done(String data) {
				JailContext J = JailRuntime.live(jId);
				if (J == null) return;
				if (!J.link.search().startsWith("?news")) return;

				if (data == null) {
					TextView t = new TextView(J.context);
					t.setText("network");
					col.addView(t);
					return;
				}

				if (!data.startsWith("[")) {
					TextView t = new TextView(J.context);
					t.setText("proto: " + data);
					col.addView(t);
					return;
				}

				try {
					JSONArray a = new JSONArray(data);

					int n = a.length();
					if (n > 10) n = 10;

					// Одна итерация цикла = одна новость на экране.
					for (int i = 0; i < n; i++) {
						JSONArray item = a.optJSONArray(i);

						// Строка новости: вертикальный контейнер
						// с двумя текстами внутри.
						LinearLayout row = new LinearLayout(J.context);
						row.setOrientation(LinearLayout.VERTICAL);

						// Отступы в пикселях (слева, сверху, справа, снизу).
						// На экранах с разной плотностью пиксели разные,
						// это мы поправим позже через dp.
						// самое время объяснить мне как работает dp!
						row.setPadding(24, 16, 24, 16);

						TextView title = new TextView(J.context);
						title.setTextSize(18);
						// optString(индекс, "") = взять строку или "" если нет.
						title.setText(item == null ? "" : item.optString(1, ""));

						TextView text = new TextView(J.context);
						text.setText(item == null ? "" : item.optString(2, ""));

						// Кладём тексты в строку, а строку - в список.
						row.addView(title);
						row.addView(text);
						col.addView(row);
					}

					// Если пришло больше, чем рисуем, значит есть ещё страница:
					// добавляем кнопку "показать больше" в самый конец.
					if (a.length() > 10) {
						// final - потому что используем b внутри onClick.
						final Button b = new Button(J.context);
						b.setText("показать больше");

						// Что делать по нажатию.
						// MIOBOMB: выглядит как замыкание, я не люблю замыкания
						b.setOnClickListener(new View.OnClickListener() {
							@Override
							public void onClick(View v) {
								// Сначала убираем саму кнопку, чтобы её
								// нельзя было нажать дважды, пока грузится.
								col.removeView(b);
								// И просим следующую страницу.
								more(jId, col, page + 1);
							}
						});

						col.addView(b);
					}
				} catch (JSONException e) {
					TextView t = new TextView(J.context);
					t.setText("json: " + e.getMessage());
					col.addView(t);
				}
			}
		});
	}
}
