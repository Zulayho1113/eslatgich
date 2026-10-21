package uz.eslatgich;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Claude AI bilan aloqa. Fon oqimida ishlaydi; javob callback orqali qaytadi. */
public final class Ai {
    public interface Callback {
        /** text bo'sh bo'lmasa — javob; aks holda err ichida sabab. */
        void done(String text, String err);
    }

    private Ai() {}

    public static void ask(Context c, String system, String user, int maxTokens, Callback cb) {
        final String key = Store.getApiKey(c);
        new Thread(() -> {
            if (key.isEmpty()) {
                cb.done("", "no_key");
                return;
            }
            HttpURLConnection con = null;
            try {
                JSONObject body = new JSONObject();
                body.put("model", "claude-haiku-5-5");
                body.put("max_tokens", maxTokens);
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
                con.setReadTimeout(90000);
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
                    cb.done("", "http_" + code + ": " + sb);
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
                cb.done(text.toString(), text.length() == 0 ? "empty" : "");
            } catch (Exception e) {
                cb.done("", "net: " + e.getMessage());
            } finally {
                if (con != null) con.disconnect();
            }
        }).start();
    }
}
