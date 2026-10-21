package uz.eslatgich;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Qulflangan ekranda ham chiqadigan katta jiringlash oynasi. */
public class RingActivity extends Activity {
    private int id;
    private String text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setShowWhenLocked(true);
        setTurnScreenOn(true);

        id = getIntent().getIntExtra("id", 0);
        text = getIntent().getStringExtra("text");
        if (text == null) text = "Eslatma";

        float d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#14233A"));
        int pad = (int) (28 * d);
        root.setPadding(pad, pad, pad, pad);

        TextView clock = new TextView(this);
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        clock.setTextColor(Color.parseColor("#FFC857"));
        clock.setTextSize(64);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setGravity(Gravity.CENTER);
        root.addView(clock);

        TextView label = new TextView(this);
        label.setText("Vaqti bo'ldi");
        label.setTextColor(Color.parseColor("#9FB3CC"));
        label.setTextSize(18);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, (int) (4 * d), 0, (int) (24 * d));
        root.addView(label);

        TextView task = new TextView(this);
        task.setText(text);
        task.setTextColor(Color.WHITE);
        task.setTextSize(28);
        task.setTypeface(Typeface.DEFAULT_BOLD);
        task.setGravity(Gravity.CENTER);
        task.setPadding(0, 0, 0, (int) (48 * d));
        root.addView(task);

        Button stop = makeButton("To'xtatish", "#FFC857", "#14233A", d);
        stop.setOnClickListener(v -> {
            AlarmReceiver.stop(this, id);
            finish();
        });
        root.addView(stop);

        View gap = new View(this);
        root.addView(gap, new LinearLayout.LayoutParams(1, (int) (14 * d)));

        Button snooze = makeButton(AlarmReceiver.SNOOZE_MINUTES + " daqiqa keyin eslat", "#24385A", "#FFFFFF", d);
        snooze.setOnClickListener(v -> {
            AlarmReceiver.snooze(this, id, text);
            finish();
        });
        root.addView(snooze);

        setContentView(root);
    }

    private Button makeButton(String t, String bg, String fg, float d) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(20);
        b.setTextColor(Color.parseColor(fg));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(bg));
        g.setCornerRadius(18 * d);
        b.setBackground(g);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (64 * d)));
        return b;
    }

    @Override
    public void onBackPressed() {
        // Orqaga tugmasi budilnikni o'chirmaydi: tugmalardan birini bosing.
    }
}
