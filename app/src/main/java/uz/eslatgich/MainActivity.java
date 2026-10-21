package uz.eslatgich;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Asosiy oyna: reja yoziladigan sahifa (assets/index.html) va uning telefon bilan aloqasi. */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlarmReceiver.ensureChannel(this);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.refresh && window.refresh()", null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (web != null) web.evaluateJavascript("window.refresh && window.refresh()", null);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void sendToPage(String fn, String a, String b, String c) {
        final String js = "window." + fn + " && window." + fn + "("
                + JSONObject.quote(a) + "," + JSONObject.quote(b) + "," + JSONObject.quote(c) + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    /** Sahifadagi JavaScript chaqiradigan funksiyalar. */
    public class Bridge {
        @JavascriptInterface
        public String getAlarms() {
            return Store.getAlarms(MainActivity.this).toString();
        }

        /** Butun ro'yxatni yangisiga almashtiradi va budilniklarni qayta qo'yadi. */
        @JavascriptInterface
        public String saveAlarms(String json) {
            try {
                JSONArray old = Store.getAlarms(MainActivity.this);
                for (int i = 0; i < old.length(); i++) {
                    JSONObject o = old.optJSONObject(i);
                    if (o != null) Scheduler.cancel(MainActivity.this, o.optInt("id"));
                }
                JSONArray arr = new JSONArray(json);
                Store.setAlarms(MainActivity.this, arr);
                Scheduler.rescheduleAll(MainActivity.this);
                return "ok";
            } catch (Exception e) {
                return "xato: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public void testRing() {
            int id = 999999;
            long at = System.currentTimeMillis() + 5000;
            Scheduler.cancel(MainActivity.this, id);
            Store.remove(MainActivity.this, id);
            Store.add(MainActivity.this, id, at, "Sinov: budilnik ishlayapti!");
            Scheduler.schedule(MainActivity.this, id, at, "Sinov: budilnik ishlayapti!");
        }

        @JavascriptInterface
        public String getApiKey() {
            return Store.getApiKey(MainActivity.this);
        }

        @JavascriptInterface
        public void setApiKey(String key) {
            Store.setApiKey(MainActivity.this, key);
        }

        /** Ruxsatlar holati: bildirishnoma, aniq vaqt, to'liq ekran. */
        @JavascriptInterface
        public String status() {
            JSONObject o = new JSONObject();
            try {
                boolean notif = Build.VERSION.SDK_INT < 33
                        || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null && !nm.areNotificationsEnabled()) notif = false;
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                boolean exact = Build.VERSION.SDK_INT < 31 || (am != null && am.canScheduleExactAlarms());
                boolean full = Build.VERSION.SDK_INT < 34 || (nm != null && nm.canUseFullScreenIntent());
                o.put("notif", notif);
                o.put("exact", exact);
                o.put("full", full);
            } catch (Exception ignored) {
            }
            return o.toString();
        }

        @JavascriptInterface
        public void openSettings(String kind) {
            runOnUiThread(() -> {
                try {
                    Intent i;
                    Uri pkg = Uri.parse("package:" + getPackageName());
                    if ("exact".equals(kind) && Build.VERSION.SDK_INT >= 31) {
                        i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg);
                    } else if ("full".equals(kind) && Build.VERSION.SDK_INT >= 34) {
                        i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg);
                    } else if ("notif".equals(kind)) {
                        i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    } else {
                        i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg);
                    }
                    startActivity(i);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
                }
            });
        }

        /** AI'ga savol yuboradi. Javob window.onAI(reqId, matn, xato) orqali qaytadi. */
        @JavascriptInterface
        public void askAI(final String reqId, final String system, final String user) {
            final String key = Store.getApiKey(MainActivity.this);
            new Thread(() -> {
                if (key.isEmpty()) {
                    sendToPage("onAI", reqId, "", "no_key");
                    return;
                }
                HttpURLConnection con = null;
                try {
                    JSONObject body = new JSONObject();
                    body.put("model", "claude-haiku-5-5");
                    body.put("max_tokens", 1500);
                    body.put("system", system);
                    JSONArray msgs = new JSONArray();
                    JSONObject m = new JSONObject();
                    m.put("role", "user");
                    m.put("content", user);
                    msgs.put(m);
                    body.put("messages", msgs);

                    con = (HttpURLConnection) new URL("https://api.anthropic.com/v1/messages").openConnection();
                    con.setRequestMethod("POST");
                    con.setConnectTimeout(20000);
                    con.setReadTimeout(60000);
                    con.setDoOutput(true);
                    con.setRequestProperty("content-type", "application/json");
                    con.setRequestProperty("x-api-key", key);
                    con.setRequestProperty("anthropic-version", "2023-06-01");
                    try (OutputStream os = con.getOutputStream()) {
                        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                    int code = con.getResponseCode();
                    InputStream is = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
                    StringBuilder sb = new StringBuilder();
                    if (is != null) {
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = r.readLine()) != null) sb.append(line).append('\n');
                        }
                    }
                    if (code < 200 || code >= 300) {
                        sendToPage("onAI", reqId, "", "http_" + code + ": " + sb);
                        return;
                    }
                    JSONObject res = new JSONObject(sb.toString());
                    JSONArray content = res.optJSONArray("content");
                    StringBuilder text = new StringBuilder();
                    if (content != null) {
                        for (int i = 0; i < content.length(); i++) {
                            JSONObject part = content.optJSONObject(i);
                            if (part != null && "text".equals(part.optString("type"))) text.append(part.optString("text"));
                        }
                    }
                    sendToPage("onAI", reqId, text.toString(), "");
                } catch (Exception e) {
                    sendToPage("onAI", reqId, "", "net: " + e.getMessage());
                } finally {
                    if (con != null) con.disconnect();
                }
            }).start();
        }
    }
}
