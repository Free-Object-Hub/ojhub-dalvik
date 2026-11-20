package xyz.objecthub.android;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import static xyz.objecthub.android.Dp.dp;

public final class PageMyProfile {
	private PageMyProfile() {}

	// порт gProfileMini, только просмотр
	public static void pageMyProfile(final int jId) {
		JailContext J = JailRuntime.live(jId);
		if (J == null) return;

		J.link.set("profile");
		J.helperMain.removeAllViews();

		JSONObject u = AppState.thisUser; // ответ loginT.php

		LinearLayout col = new LinearLayout(J.context);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(12), dp(12), dp(12), dp(12));

		TextView h1 = new TextView(J.context);
		h1.setText(tr("yourProf"));
		h1.setTextSize(24);
		h1.setTypeface(null, Typeface.BOLD);
		col.addView(h1);

		col.addView(row(J, tr("profName"), u.optString("username", "")));

		col.addView(row(J, tr("profSocials"), ""));
		String socials = u.optString("socials", "");
		if (!socials.isEmpty()) {
			// сервер хранит литерал \n (бэкслэш + n), как в JS replaceAll('\\n', ...)
			TextView s = new TextView(J.context);
			s.setText(socials.replace("\\n", "\n"));
			s.setPadding(dp(7), dp(7), dp(7), dp(7));
			GradientDrawable frame = new GradientDrawable();
			frame.setStroke(dp(1), Color.GRAY);
			s.setBackground(frame);
			col.addView(s, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT));
		}

		col.addView(row(J, tr("profId"), String.valueOf(u.optInt("ID"))));
		col.addView(row(J, tr("profRole"), role(u.optInt("role"))));

		int active = u.optInt("isActive", 1);
		col.addView(row(J, tr("profAccs"), tr(active != 0 ? "isActive" : "isNotact")));

		if (active == 0) {
			TextView warn = new TextView(J.context);
			warn.setText(tr("needEmailVerify"));
			warn.setTextSize(20);
			warn.setTypeface(null, Typeface.BOLD);
			warn.setPadding(0, dp(12), 0, 0);
			col.addView(warn);
			// TODO: кнопка enterCode1 -> verifyWindow, когда будет страница верификации
		}

		ScrollView sv = new ScrollView(J.context);
		sv.addView(col, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));

		J.helperMain.addView(sv, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
	}

	private static View row(JailContext J, String label, String value) {
		TextView t = new TextView(J.context);
		t.setText(value.isEmpty() ? label + ":" : label + ": " + value);
		t.setTextSize(16);
		t.setPadding(0, dp(8), 0, dp(4));
		return t;
	}

	// TODO: заменить на реальный механизм переводов
	private static String tr(String key) {
		return key;
	}

	// TODO: порт toStringRole
	private static String role(int r) {
		return String.valueOf(r);
	}
}
