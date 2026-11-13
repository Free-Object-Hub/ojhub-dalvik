package xyz.objecthub.android;

public final class Dp {
	private Dp() {}

	public static int dp(int v) {
		return (int) (v * App.appCtx.getResources().getDisplayMetrics().density + 0.5f);
	}
}
