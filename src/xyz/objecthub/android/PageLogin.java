package xyz.objecthub.android;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;

import static xyz.objecthub.android.Dp.dp;

/**
 * Страница логина. Одна фоновая цепочка: GET challenge -> решение ALTCHA -> POST логина.
 * Ответ логина это Init: [Current_user, [проекты, вики], главная, новости].
 * Данных в джейле нет: поля при повороте не сохраняются, для логина это нормально.
 */
public final class PageLogin {
	// ПРОВЕРЬ: если в твоей рабочей версии другой путь или поле, оставь свои
	private static final String URL_LOGIN = AppState.sData[5] + "login" + AppState.php;
	private static final String URL_CHALLENGE = AppState.sData[2] + "challenge" + AppState.php;
	private static final String FIELD_ALTCHA = "altcha";

	private PageLogin() {}

	public static void pageLogin(final int jId) {
		JailContext J = JailRuntime.live(jId);
		if (J == null) return;
		Context c = J.context;

		J.link.set("login"); // ровно один раз и в начале: см. правило _i
		J.helperMain.removeAllViews();

		final FrameLayout mounted = J.helperMain;
		int p = (int) (dp(16));

		ScrollView scroll = new ScrollView(c);
		LinearLayout col = new LinearLayout(c);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(p, p, p, p);

		// setInputType без multiline-флага сам делает поле однострочным
		final EditText user = new EditText(c);
		user.setHint("Username");
		user.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		col.addView(user);

		final EditText pass = new EditText(c);
		pass.setHint("Password");
		pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		col.addView(pass);

		final TextView status = new TextView(c);
		status.setPadding(0, p, 0, p);
		col.addView(status);

		final Button go = new Button(c);
		go.setText("Log in");
		col.addView(go);

		go.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String u = user.getText().toString().trim();
				String pw = pass.getText().toString();
				if (u.length() == 0 || pw.length() == 0) {
					status.setText("Enter username and password");
					return;
				}
				go.setEnabled(false);
				status.setText("...");
				login(jId, mounted, go, status, u, pw);
			}
		});

		scroll.addView(col);
		J.helperMain.addView(scroll);
		J.setScroller(scroll); // после mount: контент уже на месте
	}

	/** Всё в одном фоновом проходе. В UI уходит готовый user или текст ошибки. */
	private static void login(final int jId, final FrameLayout mounted, final Button go,
			final TextView status, final String u, final String pw) {
		Exec.NET.execute(new Runnable() {
			@Override
			public void run() {
				String err = null;
				JSONObject user = null;
				String raw = ""; // последний ответ сервера, для текста ошибки
				try {
					raw = Http.req("GET", URL_CHALLENGE, null, null);
					JSONObject ch = new JSONObject(raw);
					String algo = ch.optString("algorithm", "SHA-256");
					String challenge = ch.getString("challenge");
					String salt = ch.getString("salt"); // org.json сам превращает \u0026 в '&'
					int max = ch.optInt("maxnumber", ch.optInt("maxNumber", 100000));
					int n = "SHA-256".equals(algo) ? Altcha.solve(challenge, salt, max) : -1;
					if (n < 0) {
						err = "Captcha failed";
					} else {
						String alt = Altcha.makePayloadB64(algo, challenge, n, salt,
								ch.getString("signature"));
						raw = Http.req("POST", URL_LOGIN,
								"username=" + URLEncoder.encode(u, "UTF-8")
								+ "&password=" + URLEncoder.encode(pw, "UTF-8")
								+ "&" + FIELD_ALTCHA + "=" + URLEncoder.encode(alt, "UTF-8"),
								Http.URLENC);
						user = new JSONArray(raw).optJSONObject(0);
						if (user == null) err = "Unexpected response";
					}
				} catch (IOException e) {
					err = "Network error";
				} catch (JSONException e) {
					err = "Error: " + raw; // не JSON-массив: числовой код ошибки строкой
				}
				final String fErr = err;
				final JSONObject fUser = user;
				Exec.UI.post(new Runnable() {
					@Override
					public void run() {
						done(jId, mounted, go, status, fUser, fErr);
					}
				});
			}
		});
	}

	/** UI-поток. Токен сохраняем всегда, View трогаем только если страница ещё на экране. */
	private static void done(int jId, FrameLayout mounted, Button go, TextView status,
			JSONObject user, String err) {
		String msg = err;
		if (msg == null) { // err == null значит user != null
			String token = user.optString("token", "");
			if ("false".equals(token)) {
				msg = "Token rejected (token:false)"; // гость, токен отклонён (строка, не bool)
			} else if (token.length() == 0) {
				msg = "Login failed"; // гость без поля token
			} else {
				AppState.thisUser = user; // на диск уйдёт в onStop через saveAll
				AppState.setToken(token); // сразу: Prefs, заголовок user-token, сброс кеша Http
			}
		}

		JailContext J = JailRuntime.live(jId);
		if (J == null || J.helperMain != mounted) return; // страницу уже сменили или пересоздали
		if (msg == null) {
			PageMain.pageMain(jId); // сама сделает set("") и запишется в историю
			return;
		}
		status.setText(msg);
		go.setEnabled(true);
	}
}
