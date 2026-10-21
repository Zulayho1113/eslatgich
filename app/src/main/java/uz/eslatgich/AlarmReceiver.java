package uz.eslatgich;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;

/** Vaqti kelganda jiringlatadi; "To'xtatish" va "10 daqiqa keyin" tugmalarini ham shu boshqaradi. */
public class AlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_FIRE = "uz.eslatgich.FIRE";
    public static final String ACTION_STOP = "uz.eslatgich.STOP";
    public static final String ACTION_SNOOZE = "uz.eslatgich.SNOOZE";
    public static final String CHANNEL = "eslatma_jiringlash";
    public static final int SNOOZE_MINUTES = 10;

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        int id = intent.getIntExtra("id", 0);
        String text = intent.getStringExtra("text");
        if (text == null) text = "Eslatma";

        if (ACTION_STOP.equals(action)) {
            stop(c, id);
        } else if (ACTION_SNOOZE.equals(action)) {
            snooze(c, id, text);
        } else if (ACTION_FIRE.equals(action)) {
            Store.remove(c, id);
            ring(c, id, text);
        }
    }

    public static void stop(Context c, int id) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(id);
    }

    public static void snooze(Context c, int id, String text) {
        stop(c, id);
        int newId = (int) (System.currentTimeMillis() / 1000L % 1000000000L);
        long at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L;
        Store.add(c, newId, at, text);
        Scheduler.schedule(c, newId, at, text);
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Eslatma budilniklari",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Ishlaringizni eslatib jiringlaydi");
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        ch.setSound(sound, aa);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 800, 400, 800, 400, 800});
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private static PendingIntent actionPending(Context c, String action, int id, String text, int code) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(action);
        i.putExtra("id", id);
        i.putExtra("text", text);
        return PendingIntent.getBroadcast(c, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void ring(Context c, int id, String text) {
        ensureChannel(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent ringIntent = new Intent(c, RingActivity.class);
        ringIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ringIntent.putExtra("id", id);
        ringIntent.putExtra("text", text);
        PendingIntent full = PendingIntent.getActivity(c, id, ringIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ Vaqti bo'ldi!")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(full)
                .setFullScreenIntent(full, true)
                .setDeleteIntent(actionPending(c, ACTION_STOP, id, text, id + 300000))
                .addAction(new Notification.Action.Builder(null, "To'xtatish",
                        actionPending(c, ACTION_STOP, id, text, id + 100000)).build())
                .addAction(new Notification.Action.Builder(null, SNOOZE_MINUTES + " daqiqa keyin",
                        actionPending(c, ACTION_SNOOZE, id, text, id + 200000)).build());

        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT; // to'xtatmaguningizcha jiringlayveradi
        try {
            nm.notify(id, n);
        } catch (SecurityException ignored) {
            // Bildirishnomaga ruxsat berilmagan
        }
    }
}
