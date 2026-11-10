package xyz.objecthub.android;

import android.app.Activity;
import android.os.Bundle;

/**
 * Шелл: рут активного джейла и переключатель снизу (Shell.build, без XML).
 * Назад: диалог, затем VirtualLink.back(), и только потом finish.
 */
public class MainActivity extends Activity {
	@Override
	// это же вроде как аналог reStart, да?
	protected void onCreate(Bundle savedInstanceState) {
		// блять какого хуя тут пробелы вместо табов грок я тебя уебашу
		super.onCreate(savedInstanceState);
		setContentView(Shell.build(this));
		JailRuntime.onActivityCreate(this);
	}

	@Override
	protected void onStop() {
		// С API 11 процесс не убивают до конца onStop, onPause не нужен.
		// Один commit на всё; ничего не изменилось — ничего не пишется.
		App.saveAll();
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		Dialogs.dropAll();
		JailRuntime.onActivityDestroy(this);
		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		if (Dialogs.back()) return;
		JailContext J = JailRuntime.active();
		if (J != null && J.link.back()) return;
		super.onBackPressed();
	}
}
