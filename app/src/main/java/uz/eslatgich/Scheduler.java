package uz.eslatgich;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Budilniklarni telefon tizimiga qo'yadi va bekor qiladi. */
public final class Scheduler {
    private Scheduler() {}

    private static PendingIntent firePending(Context c, int id, String text) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(AlarmReceiver.ACTION_FIRE);
        i.putExtra("id", id);
        i.putExtra("text", text);
        return PendingIntent.getBroadcast(c, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c, int id, long at, String text) {
        if (at <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = firePending(c, id, text);

        boolean exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
        if (exactOk) {
            Intent open = new Intent(c, MainActivity.class);
            PendingIntent show = PendingIntent.getActivity(c, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static void cancel(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(firePending(c, id, ""));
    }

    /** Saqlangan barcha kelajakdagi eslatmalarni qayta qo'yadi (masalan, telefon o'chib yonganda). */
    public static void rescheduleAll(Context c) {
        JSONArray arr = Store.getAlarms(c);
        long now = System.currentTimeMillis();
        JSONArray keep = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            long at = o.optLong("at");
            if (at > now) {
                schedule(c, o.optInt("id"), at, o.optString("text"));
                keep.put(o);
            }
        }
        Store.setAlarms(c, keep);
    }
}
