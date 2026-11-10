package xyz.objecthub.android;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Главная страница. Аналог pageMain в GHE. */
public final class PageMain {
	private PageMain() {}

	public static void pageMain(final int jId) {
		JailContext J = JailRuntime.live(jId); // нет джейла или нет root -> ничего не рисуем
		if (J == null) return;

		J.link.set("");			   // страница сама снимает _i, как в GHE
		J.helperMain.removeAllViews();

		LinearLayout col = new LinearLayout(J.context);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setGravity(Gravity.CENTER);

		TextView t = new TextView(J.context);
		t.setText("grok говна кусок");
		t.setTextSize(24);			// sp
		t.setGravity(Gravity.CENTER);
		col.addView(t);

		Button news = new Button(J.context);
		news.setText("Открыть новости");
		news.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				PageNews.pageNews(jId);   // как onclick="pageNews(jId)" в GHE
			}
		});
		col.addView(news);

		J.helperMain.addView(col, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
	}
}
