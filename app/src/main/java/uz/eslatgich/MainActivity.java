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
import android.speech.SpeechRecognizer;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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

        List<String> ask = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ask.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ask.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!ask.isEmpty()) requestPermissions(ask.toArray(new String[0]), 1);
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

        private void testAlarm(int id, String text) {
            long at = System.currentTimeMillis() + 5000;
            Scheduler.cancel(MainActivity.this, id);
            Store.remove(MainActivity.this, id);
            Store.add(MainActivity.this, id, at, text);
            Scheduler.schedule(MainActivity.this, id, at, text);
        }

        @JavascriptInterface
        public void testRing() {
            testAlarm(999999, "Sinov: budilnik ishlayapti!");
        }

        @JavascriptInterface
        public void testSpeak() {
            testAlarm(AlarmReceiver.TEST_SPEAK_ID, "Sinov: ingliz tili mashqi");
        }

        @JavascriptInterface
        public boolean getSpeakOn() {
            return Store.isSpeakOn(MainActivity.this);
        }

        @JavascriptInterface
        public void setSpeakOn(boolean on) {
            Store.setSpeakOn(MainActivity.this, on);
        }

        @JavascriptInterface
        public String getReports() {
            return Store.getReports(MainActivity.this).toString();
        }

        @JavascriptInterface
        public String getApiKey() {
            return Store.getApiKey(MainActivity.this);
        }

        @JavascriptInterface
        public void setApiKey(String key) {
            Store.setApiKey(MainActivity.this, key);
        }

        /** Ruxsatlar holati: bildirishnoma, aniq vaqt, to'liq ekran, mikrofon, nutqni tanish. */
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
                o.put("mic", checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
                o.put("speech", SpeechRecognizer.isRecognitionAvailable(MainActivity.this));
            } catch (Exception ignored) {
            }
            return o.toString();
        }

        @JavascriptInterface
        public void openSettings(String kind) {
            runOnUiThread(() -> {
                try {
                    Uri pkg = Uri.parse("package:" + getPackageName());
                    if ("mic".equals(kind)) {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                                || !getSharedPreferences("eslatgich", MODE_PRIVATE).getBoolean("micAsked", false)) {
                            getSharedPreferences("eslatgich", MODE_PRIVATE).edit().putBoolean("micAsked", true).apply();
                            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2);
                            return;
                        }
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg));
                        return;
                    }
                    if ("speech".equals(kind)) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.google.android.googlequicksearchbox")));
                        return;
                    }
                    Intent i;
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
            Ai.ask(MainActivity.this, system, user, 1500, (text, err) -> sendToPage("onAI", reqId, text, err));
        }
    }
}
