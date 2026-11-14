package xyz.objecthub.android;

import android.content.Context;
import android.os.Build;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public final class Tls {
	private Tls() {}

	private static SSLSocketFactory sf;

	// звать на каждом HttpsURLConnection перед connect()
	public static void apply(Context ctx, HttpsURLConnection c) throws Exception {
		if (Build.VERSION.SDK_INT >= 20) return;
		if (sf == null) sf = build(ctx);
		c.setSSLSocketFactory(sf);
	}

	private static SSLSocketFactory build(Context ctx) throws Exception {
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		ks.load(null, null);

		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		String[] names = ctx.getAssets().list("certs");
		if (names != null) {
			for (String n : names) {
				InputStream in = ctx.getAssets().open("certs/" + n);
				try {
					Certificate c = cf.generateCertificate(in);
					ks.setCertificateEntry(n, c);
				} finally {
					in.close();
				}
			}
		}

		TrustManagerFactory tmf = TrustManagerFactory.getInstance(
				TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ks);

		SSLContext sc = SSLContext.getInstance("TLSv1.2");
		sc.init(null, tmf.getTrustManagers(), null);
		return new Tls12(sc.getSocketFactory());
	}

	private static final class Tls12 extends SSLSocketFactory {
		private final SSLSocketFactory d;

		Tls12(SSLSocketFactory d) { this.d = d; }

		// возвращаем тот же самый сокет, не оборачиваем:
		// внутри 4.4 есть рефлексия на методы родного сокета (SNI и т.д.)
		private static Socket fix(Socket s) {
			if (s instanceof SSLSocket)
				((SSLSocket) s).setEnabledProtocols(new String[]{"TLSv1.2"});
			return s;
		}

		@Override public String[] getDefaultCipherSuites() { return d.getDefaultCipherSuites(); }
		@Override public String[] getSupportedCipherSuites() { return d.getSupportedCipherSuites(); }

		@Override public Socket createSocket() throws IOException {
			return fix(d.createSocket());
		}
		@Override public Socket createSocket(Socket s, String h, int p, boolean a) throws IOException {
			return fix(d.createSocket(s, h, p, a));
		}
		@Override public Socket createSocket(String h, int p) throws IOException {
			return fix(d.createSocket(h, p));
		}
		@Override public Socket createSocket(String h, int p, InetAddress la, int lp) throws IOException {
			return fix(d.createSocket(h, p, la, lp));
		}
		@Override public Socket createSocket(InetAddress h, int p) throws IOException {
			return fix(d.createSocket(h, p));
		}
		@Override public Socket createSocket(InetAddress h, int p, InetAddress la, int lp) throws IOException {
			return fix(d.createSocket(h, p, la, lp));
		}
	}

	public static void init(Context ctx) {
		if (Build.VERSION.SDK_INT >= 20) return;
		try {
			HttpsURLConnection.setDefaultSSLSocketFactory(build(ctx));
		} catch (Throwable e) {
			initErr = e;
		}
	}

	public static Throwable initErr;
}
