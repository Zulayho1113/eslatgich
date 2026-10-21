package uz.eslatgich;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Qulflangan ekranda ham chiqadigan jiringlash oynasi.
 * Ertalabki budilnikda: inglizcha mavzu beriladi, budilnik faqat belgilangan vaqt gapirgandan keyin o'chadi,
 * so'ng nutq baholanadi.
 */
public class RingActivity extends Activity implements RecognitionListener {
    private static final String BG = "#14233A", CARD = "#1D3050", GOLD = "#FFC857", MUTED = "#9FB3CC";
    private static final long SPEECH_WINDOW_MS = 4000;   // shu vaqt ichida ovoz bo'lsa, "gapiryapti" deb hisoblanadi
    private static final long SILENCE_RING_MS = 60000;   // 1 daqiqa jim tursangiz, qayta jiringlaydi

    private int id, speakSecs, topic = -1, topicChanges = 0;
    private String text;
    private float d;
    private final Handler h = new Handler(Looper.getMainLooper());

    // Nutq holati
    private SpeechRecognizer sr;
    private boolean started, done, reRinging, destroyed;
    private long lastSpeech, lastTick, speakingMs, startedAt;
    private int finalWords;
    private String partial = "";
    private final StringBuilder transcript = new StringBuilder();
    private int errorStreak;

    // Ko'rinish elementlari
    private LinearLayout root;
    private TextView topicEn, topicUz, statusTv, timeTv, wordsTv, liveTv, resultTv;
    private ProgressBar bar;
    private Button startBtn, changeBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        d = getResources().getDisplayMetrics().density;

        id = getIntent().getIntExtra("id", 0);
        text = getIntent().getStringExtra("text");
        if (text == null) text = "Eslatma";
        speakSecs = getIntent().getIntExtra("speakSecs", 0);

        if (speakSecs > 0 && !canListen()) speakSecs = 0; // mikrofon bo'lmasa, oddiy budilnikka o'tadi
        if (speakSecs > 0) buildSpeakUi();
        else buildNormalUi(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Qayta jiringlaganda mavjud holat saqlanadi.
    }

    private boolean canListen() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && SpeechRecognizer.isRecognitionAvailable(this);
    }

    // ---------- Oddiy budilnik ----------
    private void buildNormalUi(String note) {
        root = column();
        root.setGravity(Gravity.CENTER);
        root.addView(clock());
        root.addView(label("Vaqti bo'ldi", MUTED, 18, false, 4, 24));
        if (note != null) root.addView(label(note, GOLD, 14, false, 0, 16));
        root.addView(label(text, "#FFFFFF", 28, true, 0, 48));

        Button stop = button("To'xtatish", GOLD, BG);
        stop.setOnClickListener(v -> {
            done = true;
            AlarmReceiver.stop(this, id);
            finish();
        });
        root.addView(stop);
        root.addView(gap(14));
        Button snooze = button(AlarmReceiver.SNOOZE_MINUTES + " daqiqa keyin eslat", CARD, "#FFFFFF");
        snooze.setOnClickListener(v -> {
            done = true;
            AlarmReceiver.snooze(this, id, text);
            finish();
        });
        root.addView(snooze);
        setContentView(root);
    }

    // ---------- Ertalabki gapirish mashqi ----------
    private void buildSpeakUi() {
        root = column();
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor(BG));
        sv.setFillViewport(true);
        sv.addView(root);

        root.addView(clock());
        root.addView(label("Xayrli tong! Budilnikni o'chirish uchun " + (speakSecs / 60 > 0 ? speakSecs / 60 + " daqiqa" : speakSecs + " soniya")
                + " ingliz tilida gapiring.", MUTED, 16, false, 4, 18));
        if (!text.startsWith("Sinov")) root.addView(label("Bugungi ish: " + text, "#FFFFFF", 16, false, 0, 18));

        LinearLayout card = column();
        card.setGravity(Gravity.START);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(CARD));
        g.setCornerRadius(18 * d);
        card.setBackground(g);
        int p = (int) (18 * d);
        card.setPadding(p, p, p, p);
        card.addView(label("TOPIC", GOLD, 12, true, 0, 6));
        topicEn = label("", "#FFFFFF", 24, true, 0, 6);
        topicEn.setGravity(Gravity.START);
        topicUz = label("", MUTED, 14, false, 0, 0);
        topicUz.setGravity(Gravity.START);
        card.addView(topicEn);
        card.addView(topicUz);
        root.addView(card, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(gap(16));

        statusTv = label("Tayyor bo'lsangiz, tugmani bosing. Jiringlash to'xtaydi va telefon sizni tinglaydi.", MUTED, 15, false, 0, 10);
        root.addView(statusTv);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(GOLD)));
        bar.setVisibility(View.GONE);
        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (14 * d)));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        timeTv = label("", GOLD, 30, true, 8, 0);
        timeTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        wordsTv = label("", "#FFFFFF", 16, false, 8, 0);
        stats.addView(timeTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        stats.addView(wordsTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        stats.setVisibility(View.GONE);
        stats.setTag("stats");
        root.addView(stats);

        liveTv = label("", "#DDE6F2", 16, false, 10, 10);
        liveTv.setGravity(Gravity.START);
        root.addView(liveTv);

        resultTv = label("", "#FFFFFF", 16, false, 6, 16);
        resultTv.setGravity(Gravity.START);
        resultTv.setTextIsSelectable(true);
        resultTv.setVisibility(View.GONE);
        root.addView(resultTv);

        startBtn = button("Gapirishni boshlash", GOLD, BG);
        startBtn.setOnClickListener(v -> startSpeaking());
        root.addView(startBtn);
        root.addView(gap(12));
        changeBtn = button("Boshqa mavzu", CARD, "#FFFFFF");
        changeBtn.setOnClickListener(v -> {
            if (topicChanges >= 2) return;
            topicChanges++;
            setTopic(Topics.random(topic));
            if (topicChanges >= 2) changeBtn.setVisibility(View.GONE);
        });
        root.addView(changeBtn);

        setTopic(Topics.random(-1));
        setContentView(sv);
    }

    private void setTopic(int i) {
        topic = i;
        topicEn.setText(Topics.LIST[i][0]);
        topicUz.setText(Topics.LIST[i][1]);
    }

    private void startSpeaking() {
        if (started) return;
        started = true;
        AlarmReceiver.stop(this, id); // jiringlashni to'xtatish
        startedAt = lastSpeech = lastTick = System.currentTimeMillis();
        startBtn.setVisibility(View.GONE);
        if (topicChanges >= 1) changeBtn.setVisibility(View.GONE);
        else changeBtn.setText("Boshqa mavzu (faqat hozir)");
        bar.setVisibility(View.VISIBLE);
        root.findViewWithTag("stats").setVisibility(View.VISIBLE);
        statusTv.setText("🎙 Gapiring! Jim tursangiz vaqt to'xtaydi, 1 daqiqa jim tursangiz yana jiringlaydi.");
        h.postDelayed(() -> changeBtn.setVisibility(View.GONE), 20000);
        createRecognizer();
        listen();
        h.post(tick);
    }

    private void createRecognizer() {
        if (sr != null) sr.destroy();
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(this);
    }

    private void listen() {
        if (done || destroyed || sr == null) return;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000);
        try {
            sr.startListening(i);
        } catch (Exception e) {
            h.postDelayed(() -> { createRecognizer(); listen(); }, 800);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (done || destroyed) return;
            long now = System.currentTimeMillis();
            boolean speaking = now - lastSpeech < SPEECH_WINDOW_MS;
            if (speaking) speakingMs += now - lastTick;
            lastTick = now;

            if (!speaking && now - lastSpeech > SILENCE_RING_MS && !reRinging) {
                reRinging = true;
                statusTv.setText("⏰ Jim turibsiz! Gapirishni davom ettiring, shunda jiringlash to'xtaydi.");
                AlarmReceiver.ring(RingActivity.this, id, text, speakSecs);
            }

            long need = speakSecs * 1000L;
            long left = Math.max(0, need - speakingMs);
            timeTv.setText(String.format(Locale.US, "%d:%02d", left / 60000, (left / 1000) % 60));
            int words = finalWords + countWords(partial);
            wordsTv.setText(words + " ta so'z\n" + (speaking ? "eshityapman…" : "jim"));
            bar.setProgress((int) Math.min(1000, speakingMs * 1000 / Math.max(1, need)));

            if (speakingMs >= need) {
                finish(words);
                return;
            }
            h.postDelayed(this, 500);
        }
    };

    private void heardSomething() {
        lastSpeech = System.currentTimeMillis();
        errorStreak = 0;
        if (reRinging) {
            reRinging = false;
            AlarmReceiver.stop(this, id);
            statusTv.setText("🎙 Gapiring! Jim tursangiz vaqt to'xtaydi.");
        }
    }

    private static int countWords(String s) {
        if (s == null) return 0;
        String t = s.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    // ---------- RecognitionListener ----------
    @Override public void onReadyForSpeech(Bundle params) {}
    @Override public void onBeginningOfSpeech() { if (!reRinging) lastSpeech = System.currentTimeMillis(); }
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onEvent(int eventType, Bundle params) {}

    @Override
    public void onPartialResults(Bundle b) {
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String p = r != null && !r.isEmpty() ? r.get(0) : "";
        if (p != null && !p.isEmpty() && !p.equals(partial)) heardSomething();
        partial = p == null ? "" : p;
        showLive();
    }

    @Override
    public void onResults(Bundle b) {
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String s = r != null && !r.isEmpty() ? r.get(0) : "";
        if (s != null && !s.trim().isEmpty()) {
            heardSomething();
            finalWords += countWords(s);
            if (transcript.length() > 0) transcript.append(' ');
            String t = s.trim();
            transcript.append(Character.toUpperCase(t.charAt(0))).append(t.substring(1)).append('.');
        }
        partial = "";
        showLive();
        h.postDelayed(this::listen, 150);
    }

    @Override
    public void onError(int error) {
        if (done || destroyed) return;
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            fallbackToNormal("Mikrofonga ruxsat yo'q, shuning uchun oddiy budilnik.");
            return;
        }
        errorStreak++;
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT
                || errorStreak > 5) {
            h.postDelayed(() -> { createRecognizer(); listen(); }, 700);
        } else {
            h.postDelayed(this::listen, 250);
        }
    }

    private void showLive() {
        String all = transcript + (partial.isEmpty() ? "" : " " + partial);
        if (all.length() > 260) all = "…" + all.substring(all.length() - 260);
        liveTv.setText(all.trim());
    }

    private void fallbackToNormal(String note) {
        if (sr != null) { sr.destroy(); sr = null; }
        h.removeCallbacksAndMessages(null);
        speakSecs = 0;
        AlarmReceiver.ring(this, id, text, 0);
        buildNormalUi(note);
    }

    // ---------- Yakun va baho ----------
    private void finish(int words) {
        done = true;
        h.removeCallbacksAndMessages(null);
        if (sr != null) { sr.cancel(); sr.destroy(); sr = null; }
        AlarmReceiver.stop(this, id);

        String full = (transcript + (partial.isEmpty() ? "" : " " + partial)).trim();
        double minutes = Math.max(0.1, speakingMs / 60000.0);
        int wpm = (int) Math.round(words / minutes);
        Set<String> uniq = new HashSet<>();
        int fillers = 0;
        for (String w : full.toLowerCase(Locale.US).replaceAll("[^a-z' ]", " ").split("\\s+")) {
            if (w.isEmpty()) continue;
            uniq.add(w);
            if (w.matches("um+|uh+|er+|erm|hmm+|like|basically|actually")) fillers++;
        }
        if (full.toLowerCase(Locale.US).contains("you know")) fillers++;

        final long time = System.currentTimeMillis();
        JSONObject rep = new JSONObject();
        try {
            rep.put("time", time);
            rep.put("topic", Topics.LIST[topic][0]);
            rep.put("words", words);
            rep.put("wpm", wpm);
            rep.put("unique", uniq.size());
            rep.put("fillers", fillers);
            rep.put("seconds", speakingMs / 1000);
            rep.put("transcript", full);
        } catch (Exception ignored) {}
        Store.addReport(this, rep);

        statusTv.setText("🎉 Barakalla! Budilnik o'chdi.");
        statusTv.setTextColor(Color.parseColor(GOLD));
        statusTv.setTextSize(22);
        changeBtn.setVisibility(View.GONE);
        timeTv.setText("0:00");
        bar.setProgress(1000);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 Natijangiz\n")
          .append("• So'zlar: ").append(words).append('\n')
          .append("• Tezlik: daqiqasiga ").append(wpm).append(" so'z").append(wpm < 70 ? " (sekinroq, bu normal)" : wpm > 140 ? " (juda tez!)" : " (yaxshi)").append('\n')
          .append("• Har xil so'zlar: ").append(uniq.size()).append('\n')
          .append("• To'ldiruvchi so'zlar (um, like…): ").append(fillers).append("\n\n");
        resultTv.setVisibility(View.VISIBLE);

        boolean hasKey = !Store.getApiKey(this).isEmpty();
        if (!hasKey) {
            sb.append("To'liq baho (grammatika, xatolar va maslahatlar) uchun ilova Sozlamalarida AI kalitini kiriting.");
            resultTv.setText(sb.toString());
        } else if (words < 10) {
            sb.append("Baholash uchun gap juda kam eshitildi.");
            resultTv.setText(sb.toString());
        } else {
            final String head = sb.toString();
            resultTv.setText(head + "🤖 AI nutqingizni baholayapti…");
            String system = "You are a friendly, encouraging English speaking coach for an Uzbek learner. "
                    + "You receive an automatic speech-to-text transcript of a short spoken monologue. "
                    + "The transcript may contain recognition errors; if a word looks misrecognised, mention it as a possible pronunciation issue. "
                    + "Do not judge punctuation or capitalisation (they were added automatically). "
                    + "Write the feedback in Uzbek (Latin script), with English examples in quotes. Plain text, no markdown symbols like ** or #. Keep it under 220 words. "
                    + "Format exactly:\n"
                    + "BAHO: X/10 (taxminiy daraja: A2/B1/B2/C1)\n"
                    + "Yaxshi tomonlar: 1-2 sentences.\n"
                    + "Xatolar: up to 4 lines, each \"noto'g'ri\" → \"to'g'ri\" with a 3-6 word reason.\n"
                    + "Yangi so'zlar: 3 useful words or phrases for this topic with Uzbek meaning.\n"
                    + "Talaffuz: one short note, or skip this line if nothing stands out.\n"
                    + "Ertangi maslahat: one concrete tip.";
            String user = "Topic: " + Topics.LIST[topic][0] + "\nSpeaking time: " + (speakingMs / 1000)
                    + " s, words: " + words + ", words per minute: " + wpm + "\n\nTranscript:\n" + full;
            Ai.ask(this, system, user, 900, (res, err) -> runOnUiThread(() -> {
                if (!res.isEmpty()) {
                    resultTv.setText(head + "🤖 AI bahosi\n" + res.trim());
                    Store.updateReport(this, time, "feedback", res.trim());
                } else {
                    resultTv.setText(head + (err.startsWith("http_401") ? "AI kaliti noto'g'ri." : "AI bilan bog'lanib bo'lmadi (internetni tekshiring)."));
                }
            }));
        }

        Button close = button("Yopish", GOLD, BG);
        close.setOnClickListener(v -> finishAndRemoveTask());
        root.addView(close);
    }

    // ---------- Yordamchi ko'rinishlar ----------
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setBackgroundColor(Color.parseColor(BG));
        int pad = (int) (24 * d);
        l.setPadding(pad, (int) (36 * d), pad, pad);
        return l;
    }

    private TextView clock() {
        TextView c = new TextView(this);
        c.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        c.setTextColor(Color.parseColor(GOLD));
        c.setTextSize(56);
        c.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        c.setGravity(Gravity.CENTER);
        return c;
    }

    private TextView label(String t, String color, int sp, boolean bold, int padTop, int padBottom) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.parseColor(color));
        v.setTextSize(sp);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setLineSpacing(0, 1.15f);
        v.setPadding(0, (int) (padTop * d), 0, (int) (padBottom * d));
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return v;
    }

    private View gap(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, (int) (dp * d)));
        return v;
    }

    private Button button(String t, String bg, String fg) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(19);
        b.setTextColor(Color.parseColor(fg));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(bg));
        g.setCornerRadius(18 * d);
        b.setBackground(g);
        b.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (62 * d)));
        return b;
    }

    @Override
    public void onBackPressed() {
        // Orqaga tugmasi budilnikni o'chirmaydi.
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        h.removeCallbacksAndMessages(null);
        if (sr != null) { sr.destroy(); sr = null; }
        if (speakSecs > 0 && !done) {
            // Mashq tugamasdan yopilsa — 1 daqiqadan keyin yana jiringlaydi.
            AlarmReceiver.stop(this, id);
            int newId = (int) (System.currentTimeMillis() / 1000L % 1000000000L);
            long at = System.currentTimeMillis() + 60_000L;
            Store.add(this, newId, at, text);
            Scheduler.schedule(this, newId, at, text);
        }
        super.onDestroy();
    }
}
