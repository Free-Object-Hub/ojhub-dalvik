package xyz.objecthub.android;

import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.security.Provider;
import java.security.Security;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import static xyz.objecthub.android.Dp.dp;

public final class PageMain {
	private PageMain() {}

	public static void pageMain(final int jId) {
		JailContext J = JailRuntime.live(jId);
		if (J == null) return;

		J.link.set("");
		J.helperMain.removeAllViews();

		final LinearLayout col = new LinearLayout(J.context);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setGravity(Gravity.CENTER);

		ImageView icon = new ImageView(J.context);
		icon.setImageResource(J.context.getApplicationInfo().icon);
		col.addView(icon, new LinearLayout.LayoutParams(
				dp(48), dp(48)));

		TextView t = new TextView(J.context);
		t.setText("OjHub Dalvik: Ikarus Edition");
		t.setTextSize(20);
		t.setGravity(Gravity.CENTER);
		col.addView(t);

		TextView d = new TextView(J.context);
		d.setText("Build: " + BuildInfo.DATE + " (" + BuildInfo.REV + ")");
		d.setTextSize(12);
		d.setGravity(Gravity.CENTER);
		col.addView(d);

		TextView hello = new TextView(J.context);
		hello.setText("You are " + AppState.thisUser.optString("username", "").trim());
		hello.setTextSize(16);
		hello.setGravity(Gravity.CENTER);
		hello.setPadding(0, 0, 0, dp(32));
		col.addView(hello);

		Button news = new Button(J.context);
		news.setText("Открыть новости");
		news.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				PageNews.pageNews(jId);
			}
		});
		col.addView(news);

		Button login = new Button(J.context);
		login.setText("LOGIN ???");
		login.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (AppState.thisUser.optInt("ID") == 0) {
					PageLogin.pageLogin(jId);
				} else {
					PageMyProfile.pageMyProfile(jId);
				}
			}
		});
		col.addView(login);

		// debug info
		TextView dbg = new TextView(J.context);
		dbg.setTypeface(Typeface.MONOSPACE);
		dbg.setTextSize(11);
		dbg.setPadding(dp(8), dp(8), dp(8), dp(8));
		dbg.setText(dbg());
		col.addView(dbg, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));

		ScrollView sv = new ScrollView(J.context);
		sv.addView(col, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));

		J.helperMain.addView(sv, new FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
	}

	private static String dbg() {
		StringBuilder b = new StringBuilder();
		b.append("android: ").append(Build.VERSION.RELEASE)
				.append(" (sdk ").append(Build.VERSION.SDK_INT).append(")\n");
		b.append("device: ").append(Build.MANUFACTURER).append(' ')
				.append(Build.MODEL).append('\n');
		b.append("abi: ").append(Build.CPU_ABI).append(' ')
				.append(Build.CPU_ABI2).append('\n');
		b.append("vm: ").append(System.getProperty("java.vm.name")).append(' ')
				.append(System.getProperty("java.vm.version")).append('\n');

		b.append("\n-- providers --\n");
		for (Provider p : Security.getProviders())
			b.append(p.getName()).append(' ').append(p.getVersion()).append('\n');

		b.append("\n-- default ssl --\n");
		try {
			SSLSocket s = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket();
			b.append("supported: ").append(Arrays.toString(s.getSupportedProtocols())).append('\n');
			b.append("enabled: ").append(Arrays.toString(s.getEnabledProtocols())).append('\n');
			b.append("suites supported: ").append(s.getSupportedCipherSuites().length)
					.append(", enabled: ").append(s.getEnabledCipherSuites().length).append('\n');
			s.close();
		} catch (Throwable e) {
			b.append("default ssl fail: ").append(e).append('\n');
		}

		b.append("\n-- TLSv1.2 context --\n");
		try {
			SSLContext c = SSLContext.getInstance("TLSv1.2");
			c.init(null, null, null);
			SSLSocket s = (SSLSocket) c.getSocketFactory().createSocket();
			b.append("ctx ok, enabled: ").append(Arrays.toString(s.getEnabledProtocols())).append('\n');
			s.close();
		} catch (Throwable e) {
			b.append("ctx fail: ").append(e).append('\n');
		}

		return b.toString();
	}
}
