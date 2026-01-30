import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class TelegramUtils {

    private static final int TELEGRAM_MAX_CHARS = 4096; // valor típico

    public static void sendTelegramMessageSplit(String message, String token, String chatId) {
        if (message == null) return;
        int max = TELEGRAM_MAX_CHARS;
        int len = message.length();
        if (len <= max) {
            sendTelegramMessagePost(message, token, chatId);
            return;
        }
        int part = 1;
        for (int i = 0; i < len; i += max) {
            String chunk = message.substring(i, Math.min(len, i + max));
            String header = "Parte " + part + " / " + ((len + max - 1) / max) + ":\n";
            sendTelegramMessagePost(header + chunk, token, chatId);
            part++;
            try {
                Thread.sleep(500); // pequeña pausa para evitar rate limits
            } catch (InterruptedException ignored) {}
        }
    }

    public static void sendTelegramMessagePost(String message, String token, String chatId) {
        HttpURLConnection conn = null;
        try {
            String urlString = "https://api.telegram.org/bot" + token + "/sendMessage";
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            StringBuilder postData = new StringBuilder();
            postData.append("chat_id=").append(URLEncoder.encode(chatId, "UTF-8"));
            postData.append("&text=").append(URLEncoder.encode(message, "UTF-8"));
            byte[] postBytes = postData.toString().getBytes("UTF-8");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(postBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                try (InputStream is = conn.getInputStream();
                     BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    StringBuilder resp = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        resp.append(line);
                    }
                    // opcional: procesar resp
                }
            } else {
                String errorMsg = "";
                try (InputStream es = conn.getErrorStream()) {
                    if (es != null) {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(es))) {
                            String line;
                            StringBuilder err = new StringBuilder();
                            while ((line = br.readLine()) != null) {
                                err.append(line);
                            }
                            errorMsg = err.toString();
                        }
                    }
                }
                System.err.println("Telegram API responded with code " + responseCode + ". Error: " + errorMsg);
            }
        } catch (Exception e) {
            System.err.println("Error enviando mensaje a Telegram: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
