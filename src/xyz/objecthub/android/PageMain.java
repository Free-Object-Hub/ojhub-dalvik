package xyz.objecthub.android;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;

import static xyz.objecthub.android.Dp.dp;

public final class PageMain {
	private PageMain() {}

	public static void pageMain(final int jId) {
		JailContext J = JailRuntime.live(jId);
		if (J == null) return;

		J.link.set("");
		J.helperMain.removeAllViews();

		LinearLayout col = new LinearLayout(J.context);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setGravity(Gravity.CENTER);

		ImageView icon = new ImageView(J.context);
		icon.setImageResource(J.context.getApplicationInfo().icon);
		col.addView(icon, new LinearLayout.LayoutParams(
				dp(48), dp(48)));

		TextView t = new TextView(J.context);
		t.setText("U Bahnhof Ruhleben - Nervenklinik");
		t.setTextSize(16);
		t.setGravity(Gravity.CENTER);
		col.addView(t);

		Button news = new Button(J.context);
		news.setText("Открыть новости");
		news.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				PageNews.pageNews(jId);
			}
		});
		col.addView(news);

		Button hof = new Button(J.context);
		hof.setText("SEGFAULT!");
		hof.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				JailContext J = JailRuntime.live(jId);
				if (J == null) return;

				TextView t = new TextView(J.context);
				t.setText("spandau");
				t.setTextSize(16);
				t.setGravity(Gravity.CENTER);
				col.addView(t);
			}
		});
		col.addView(hof);

		J.helperMain.addView(col, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
	}
}
