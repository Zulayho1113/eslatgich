package uz.eslatgich;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon qayta yoqilganda eslatmalarni qaytadan qo'yadi. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Scheduler.rescheduleAll(c);
    }
}
