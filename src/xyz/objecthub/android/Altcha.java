package xyz.objecthub.android;

import java.security.DigestException;
import java.security.MessageDigest;

/**
 * Решатель ALTCHA v1 (SHA-256). Порт altcha.c из ojhub-qt.
 * Чистая Java без android.*: гоняется javac без SDK (tools/AltchaTest).
 * hash = SHA256(salt + decimal(n)), ищем n в [0..max], где hash == challenge.
 */
public final class Altcha {
	private Altcha() {}

	/** n, либо -1: не найдено, плохой вход или поток прерван (interrupt). */
	public static int solve(String challengeHex, String salt, int max) {
		if (challengeHex == null || salt == null || challengeHex.length() < 64) return -1;
		byte[] want = new byte[32];
		for (int i = 0; i < 32; i++) {
			int hi = Character.digit(challengeHex.charAt(i * 2), 16);
			int lo = Character.digit(challengeHex.charAt(i * 2 + 1), 16);
			if (hi < 0 || lo < 0) return -1;
			want[i] = (byte) ((hi << 4) | lo);
		}
		byte[] saltB;
		MessageDigest md;
		try {
			saltB = salt.getBytes("UTF-8");
			md = MessageDigest.getInstance("SHA-256");
		} catch (Exception e) {
			return -1;
		}

		byte[] num = new byte[12];
		byte[] got = new byte[32];
		Thread self = Thread.currentThread();
		try {
			for (int n = 0; n <= max; n++) {
				if ((n & 255) == 0 && self.isInterrupted()) return -1;
				int len = dec(n, num);
				md.update(saltB);
				md.update(num, 0, len);
				md.digest(got, 0, 32);
				if (same32(got, want)) return n;
			}
		} catch (DigestException e) {
			return -1;
		}
		return -1;
	}

	/** base64(JSON{algorithm,challenge,number,salt,signature}), как Payload в altcha-lib-go. */
	public static String makePayloadB64(String algorithm, String challengeHex, int number,
			String salt, String signatureHex) {
		StringBuilder j = new StringBuilder(256);
		j.append("{\"algorithm\":");
		str(j, algorithm);
		j.append(",\"challenge\":");
		str(j, challengeHex);
		j.append(",\"number\":").append(number);
		j.append(",\"salt\":");
		str(j, salt);
		j.append(",\"signature\":");
		str(j, signatureHex);
		j.append('}');
		try {
			return b64(j.toString().getBytes("UTF-8"));
		} catch (Exception e) {
			return null;
		}
	}

	private static int dec(int n, byte[] buf) {
		if (n == 0) {
			buf[0] = '0';
			return 1;
		}
		int len = 0;
		for (int v = n; v > 0; v /= 10) len++;
		for (int i = len - 1, v = n; i >= 0; i--, v /= 10) buf[i] = (byte) ('0' + v % 10);
		return len;
	}

	private static boolean same32(byte[] a, byte[] b) {
		for (int i = 0; i < 32; i++) {
			if (a[i] != b[i]) return false;
		}
		return true;
	}

	/** В C экранирования нет. Здесь минимальное: соль может содержать что угодно. */
	private static void str(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '"' || c == '\\') {
				sb.append('\\').append(c);
			} else if (c < 0x20) {
				sb.append(String.format("\\u%04x", (int) c));
			} else {
				sb.append(c);
			}
		}
		sb.append('"');
	}

	private static final char[] TBL =
			"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

	/** Стандартный base64 с padding, как base64.StdEncoding на сервере. */
	private static String b64(byte[] src) {
		StringBuilder o = new StringBuilder(((src.length + 2) / 3) * 4);
		for (int i = 0; i < src.length; i += 3) {
			int left = src.length - i;
			int v = (src[i] & 0xff) << 16;
			if (left > 1) v |= (src[i + 1] & 0xff) << 8;
			if (left > 2) v |= src[i + 2] & 0xff;
			o.append(TBL[(v >> 18) & 63]);
			o.append(TBL[(v >> 12) & 63]);
			o.append(left > 1 ? TBL[(v >> 6) & 63] : '=');
			o.append(left > 2 ? TBL[v & 63] : '=');
		}
		return o.toString();
	}
}
