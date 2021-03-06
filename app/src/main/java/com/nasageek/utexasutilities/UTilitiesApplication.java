
package com.nasageek.utexasutilities;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.webkit.CookieSyncManager;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.nasageek.utexasutilities.fragments.UTilitiesPreferenceFragment;
import com.securepreferences.SecurePreferences;
import com.squareup.leakcanary.LeakCanary;
import com.tozny.crypto.android.AesCbcWithIntegrity;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import io.fabric.sdk.android.Fabric;

public class UTilitiesApplication extends Application {
    private static UTilitiesApplication sInstance;

    public static final String UTD_AUTH_COOKIE_KEY = "utd_auth_cookie";

    private Map<String, AuthCookie> authCookies = new HashMap<>();
    private static OkHttpClient client = new OkHttpClient();
    private OkHttpClient.Builder builder = new OkHttpClient.Builder();
    private Map<String, AsyncTask> asyncTaskCache = new HashMap<>();
    private SharedPreferences securePreferences;
    private SharedPreferences sharedPreferences;

    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean sendCrashes = sharedPreferences.getBoolean("sendcrashes", false);
        Crashlytics crashlytics = new Crashlytics.Builder()
                .core(new CrashlyticsCore.Builder().disabled(!sendCrashes).build())
                .build();
        Fabric.with(this, crashlytics);
        sInstance = this;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                // Attempt to remedy a system memory leak
                UserManager.class.getMethod("get", Context.class).invoke(null, this);
            } catch (Exception e) {
                // Catch Exception because we need API 19 to do multi-catch for the necessary
                // exceptions.
                // Do nothing on failure.
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // Bug in P causes excessive leaks, just disable for now
            LeakCanary.install(this);
        }
        try {
            AesCbcWithIntegrity.SecretKeys myKey = AesCbcWithIntegrity.generateKeyFromPassword(
                    Utility.id(this), Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID).getBytes(), 10);
            securePreferences = new SecurePreferences(this, myKey, null);
        } catch (GeneralSecurityException gse) {
            // no clue what to do here
            gse.printStackTrace();
            Crashlytics.logException(gse);
        }
        upgradePasswordEncryption();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // UT's servers only support TLS 1.2 now. TLS 1.2 is supported but not enabled by
            // default in Android 4.1 to 5.0. Here we enable it. Unfortunately, this means we have
            // jto drop support for 4.0.
            try {
                SSLContext sc = SSLContext.getInstance("TLSv1.2");
                sc.init(null, null, null);
                builder.socketFactory(new Tls12SocketFactory(sc.getSocketFactory()));
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                e.printStackTrace();
            }
        }

        authCookies.put(UTD_AUTH_COOKIE_KEY, new UtdAuthCookie(this));

        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);
        builder.connectTimeout(10, TimeUnit.SECONDS);
        builder.cookieJar(new JavaNetCookieJar(cookieManager));
        client = builder.build();
        CookieSyncManager.createInstance(this);

        AnalyticsHandler.initTrackerIfNeeded(this,
                sharedPreferences.getBoolean(getString(R.string.pref_analytics_key), false));
    }

    private void upgradePasswordEncryption() {
        LegacySecurePreferences lsp = new LegacySecurePreferences(this,
                UTilitiesPreferenceFragment.OLD_PASSWORD_PREF_FILE, false);
        if (lsp.containsKey("password")) {
            try {
                String password = lsp.getString("password");
                securePreferences.edit().putString("password", password).apply();
            } catch (LegacySecurePreferences.SecurePreferencesException spe) {
                spe.printStackTrace();
                Crashlytics.logException(spe);
                Toast.makeText(this, "Your saved password has been wiped for security purposes" +
                        " and will need to be re-entered just this once", Toast.LENGTH_LONG).show();
            } finally {
                lsp.clear();
            }
        }
    }

    public AuthCookie getAuthCookie(String key) {
        return authCookies.get(key);
    }

    public void putAuthCookie(String key, AuthCookie cookie) {
        authCookies.put(key, cookie);
    }

    public boolean allCookiesSet() {
        for (AuthCookie cookie : authCookies.values()) {
            if (!cookie.hasCookieBeenSet()) {
                return false;
            }
        }
        return !authCookies.isEmpty();
    }

    public boolean anyCookiesSet() {
        for (AuthCookie cookie : authCookies.values()) {
            if (cookie.hasCookieBeenSet()) {
                return true;
            }
        }
        return false;
    }

    public String getUtdAuthCookieVal() {
        return authCookies.get(UTD_AUTH_COOKIE_KEY).getAuthCookieVal();
    }

    public void logoutAll() {
        for (AuthCookie authCookie : authCookies.values()) {
            authCookie.logout();
        }

        CookieStore cookies = ((CookieManager) CookieHandler.getDefault()).getCookieStore();
        cookies.removeAll();
    }

    public OkHttpClient getHttpClient() {
        return client;
    }

    public void cacheTask(String tag, AsyncTask task) {
        if (asyncTaskCache.containsKey(tag)) {
            throw new IllegalStateException("Task already cached");
        }
        asyncTaskCache.put(tag, task);
    }

    public AsyncTask getCachedTask(String tag) {
        return asyncTaskCache.get(tag);
    }

    public void removeCachedTask(String tag) {
        asyncTaskCache.remove(tag);
    }

    public SharedPreferences getSecurePreferences() {
        return securePreferences;
    }

    public static UTilitiesApplication getInstance() {
        return sInstance;
    }

    public static UTilitiesApplication getInstance(Context context) {
        return context != null ? (UTilitiesApplication) context.getApplicationContext() : sInstance;
    }
}
