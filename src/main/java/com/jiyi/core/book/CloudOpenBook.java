package com.jiyi.core.book;

import com.jiyi.core.model.Board;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CloudOpenBook implements OpenBook {
    private static final Logger log = LoggerFactory.getLogger(CloudOpenBook.class);
    private static final String API_URL = "https://www.chessdb.cn/chessdb.php";

    private final HttpClient client;
    private final int timeoutMs;

    public CloudOpenBook(int timeoutMs) {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .build();
        this.timeoutMs = timeoutMs;
    }

    @Override
    public List<BookEntry> query(Board board, boolean redGo) {
        String fen = board.toFen(redGo);
        return query(fen, false);
    }

    @Override
    public List<BookEntry> query(String fenCode, boolean onlyFinalPhase) {
        var results = new ArrayList<BookEntry>();
        try {
            String action = onlyFinalPhase ? "querybest" : "queryall";
            String encodedFen = URLEncoder.encode(fenCode, StandardCharsets.UTF_8);
            var req = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "?action=" + action + "&board=" + encodedFen))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
            var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Cloud book HTTP {}", resp.statusCode());
                return results;
            }
            String body = resp.body();
            if (body == null || body.isEmpty() || body.startsWith("?")) return results;

            for (String entry : body.split("\\|")) {
                results.add(parseEntry(entry.trim()));
            }
        } catch (Exception e) {
            log.debug("Cloud book query failed: {}", e.getMessage());
        }
        return results;
    }

    @Override
    public void close() {}

    private BookEntry parseEntry(String raw) {
        String move = "";
        int score = 0;
        double winRate = 0;
        String note = "";

        for (String kv : raw.split(",")) {
            kv = kv.trim();
            int eq = kv.indexOf(':');
            if (eq < 0) continue;
            String key = kv.substring(0, eq).trim();
            String val = kv.substring(eq + 1).trim();
            switch (key) {
                case "move" -> move = val;
                case "score" -> { try { score = Integer.parseInt(val); } catch (NumberFormatException ignored) {} }
                case "winrate" -> { try { winRate = Double.parseDouble(val); } catch (NumberFormatException ignored) {} }
                case "note" -> note = val;
            }
        }
        return new BookEntry(move, score, winRate, 0, 0, 0, note, "云库");
    }
}
