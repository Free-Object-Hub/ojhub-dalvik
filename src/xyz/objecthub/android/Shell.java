package xyz.objecthub.android;

import android.app.Activity;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/**
 * Шелл кодом, без layout XML: сверху jail_host (рут активного джейла),
 * снизу прокручиваемый ряд переключателя. Собирается заново на каждую Activity.
 */
public final class Shell {
	private Shell() {}

	public static View build(Activity a) {
		LinearLayout shell = new LinearLayout(a);
		shell.setOrientation(LinearLayout.VERTICAL);

		TypedArray ta = a.obtainStyledAttributes(new int[] { android.R.attr.listDivider });
		Drawable div = ta.getDrawable(0);
		ta.recycle();
		if (div != null) {
			shell.setDividerDrawable(div);
			shell.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
		}

		FrameLayout host = new FrameLayout(a);
		host.setId(Ids.JAIL_HOST);
		shell.addView(host, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

		HorizontalScrollView scroll = new HorizontalScrollView(a);
		scroll.setId(Ids.SWITCHER_SCROLL);
		scroll.setFillViewport(true);
		scroll.setHorizontalScrollBarEnabled(false);

		LinearLayout switcher = new LinearLayout(a);
		switcher.setId(Ids.SWITCHER);
		switcher.setOrientation(LinearLayout.HORIZONTAL);
		switcher.setGravity(Gravity.CENTER_VERTICAL);
		scroll.addView(switcher, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		shell.addView(scroll, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		return shell;
	}
}
