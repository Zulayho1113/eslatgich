package uz.eslatgich;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Eslatmalarni telefon xotirasida saqlaydi. */
public final class Store {
    private static final String PREFS = "eslatgich";
    private static final String KEY_ALARMS = "alarms";
    private static final String KEY_API = "apiKey";

    private Store() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized JSONArray getAlarms(Context c) {
        try {
            return new JSONArray(prefs(c).getString(KEY_ALARMS, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public static synchronized void setAlarms(Context c, JSONArray arr) {
        prefs(c).edit().putString(KEY_ALARMS, arr.toString()).apply();
    }

    public static synchronized void remove(Context c, int id) {
        JSONArray arr = getAlarms(c);
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.optInt("id") != id) out.put(o);
        }
        setAlarms(c, out);
    }

    public static synchronized void add(Context c, int id, long at, String text) {
        JSONArray arr = getAlarms(c);
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("at", at);
            o.put("text", text);
            arr.put(o);
        } catch (JSONException ignored) {
        }
        setAlarms(c, arr);
    }

    public static boolean isSpeakOn(Context c) {
        return prefs(c).getBoolean("speakOn", true);
    }

    public static void setSpeakOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("speakOn", on).apply();
    }

    /** Ingliz tili mashqlari tarixi (oxirgi 60 tasi, eng yangisi oxirida). */
    public static synchronized JSONArray getReports(Context c) {
        try {
            return new JSONArray(prefs(c).getString("reports", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public static synchronized void addReport(Context c, JSONObject r) {
        JSONArray arr = getReports(c);
        arr.put(r);
        JSONArray out = new JSONArray();
        for (int i = Math.max(0, arr.length() - 60); i < arr.length(); i++) out.put(arr.opt(i));
        prefs(c).edit().putString("reports", out.toString()).apply();
    }

    /** Mavjud hisobotni (masalan, AI bahosi kelganda) yangilaydi. */
    public static synchronized void updateReport(Context c, long time, String key, Object value) {
        JSONArray arr = getReports(c);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && o.optLong("time") == time) {
                try {
                    o.put(key, value);
                } catch (JSONException ignored) {
                }
            }
        }
        prefs(c).edit().putString("reports", arr.toString()).apply();
    }

    public static String getApiKey(Context c) {
        return prefs(c).getString(KEY_API, "");
    }

    public static void setApiKey(Context c, String key) {
        prefs(c).edit().putString(KEY_API, key == null ? "" : key.trim()).apply();
    }
}
