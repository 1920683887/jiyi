package com.jiyi.service;

import com.jiyi.core.model.ManualRecord;
import com.jiyi.core.model.Move;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ManualService {
    private static final Logger log = LoggerFactory.getLogger(ManualService.class);

    private ManualRecord currentRecord;
    private int currentIndex = -1;

    public ManualService() {
        currentRecord = new ManualRecord();
    }

    public ManualRecord getRecord() { return currentRecord; }

    public void startNewRecord() {
        currentRecord = new ManualRecord();
        currentRecord.setDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")));
        currentIndex = -1;
    }

    public void addMove(String moveUci, String chineseMove, String remark) {
        currentRecord.addMove(moveUci, chineseMove, remark);
        currentIndex = currentRecord.mainLine().size() - 1;
    }

    public void addMoveFromGameService(Move move) {
        String remark = "";
        // Check if last move had a capture for auto-remark
        addMove(move.toUci(), "", remark);
    }

    public int currentIndex() { return currentIndex; }
    public void setCurrentIndex(int i) { currentIndex = i; }

    public boolean canGoPrev() { return currentIndex > 0; }
    public boolean canGoNext() { return currentIndex < currentRecord.mainLine().size() - 1; }

    public int totalMoves() { return currentRecord.mainLine().size(); }

    public void savePgn(Path path) throws IOException {
        var sb = new StringBuilder();
        var r = currentRecord;
        sb.append("[Event \"").append(r.eventName()).append("\"]\n");
        sb.append("[Site \"").append(r.site()).append("\"]\n");
        sb.append("[Date \"").append(r.date()).append("\"]\n");
        sb.append("[Round \"").append(r.round()).append("\"]\n");
        sb.append("[Red \"").append(r.redPlayer()).append("\"]\n");
        sb.append("[Black \"").append(r.blackPlayer()).append("\"]\n");
        sb.append("[Result \"").append(r.result()).append("\"]\n");
        sb.append("[FEN \"").append("rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1").append("\"]\n");
        sb.append("\n");

        var moves = r.mainLine();
        for (int i = 0; i < moves.size(); i++) {
            int moveNum = i / 2 + 1;
            if (i % 2 == 0) {
                sb.append(moveNum).append(". ");
            }
            String m = moves.get(i).moveUci();
            sb.append(m).append(" ");
            if ((i + 1) % 10 == 0) sb.append("\n");
        }
        sb.append(r.result().isEmpty() || r.result().equals("*") ? "*" : r.result());
        sb.append("\n");

        Files.writeString(path, sb.toString());
    }

    public ManualRecord loadPgn(Path path) throws IOException {
        String content = Files.readString(path);
        var record = new ManualRecord();
        String[] lines = content.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("[") && line.contains("\"")) {
                String key = line.substring(1, line.indexOf(" "));
                String val = line.substring(line.indexOf("\"") + 1, line.lastIndexOf("\""));
                switch (key) {
                    case "Event" -> record.setEventName(val);
                    case "Site" -> record.setSite(val);
                    case "Date" -> record.setDate(val);
                    case "Red" -> record.setRedPlayer(val);
                    case "Black" -> record.setBlackPlayer(val);
                    case "Result" -> record.setResult(val);
                }
            }
        }

        int moveStart = content.lastIndexOf("\n\n");
        if (moveStart < 0) moveStart = 0;
        String moveText = content.substring(moveStart).trim();
        moveText = moveText.replaceAll("\\{.*?\\}", "");
        moveText = moveText.replaceAll("\\$\\d+", "");
        moveText = moveText.replaceAll("\\d+\\.\\.\\.", "");
        moveText = moveText.replaceAll("\\d+\\.", "");
        moveText = moveText.replaceAll("[\\*\\?!/=+]", "");
        moveText = moveText.replaceAll("\\s+", " ").trim();

        String resultStr = record.result();
        if (!resultStr.equals("*")) {
            moveText = moveText.replace(resultStr, "");
        }

        String[] tokens = moveText.split("\\s+");
        for (String t : tokens) {
            if (t.isEmpty() || t.equals(resultStr)) continue;
            if (t.length() >= 4 && !t.contains(".")) {
                try {
                    record.addMove(t, "", "");
                } catch (Exception e) {
                    log.warn("Skipping invalid move: {}", t);
                }
            }
        }

        currentRecord = record;
        currentIndex = -1;
        return record;
    }

    public void setMetadata(String event, String site, String red, String black, String result) {
        currentRecord.setEventName(event);
        currentRecord.setSite(site);
        currentRecord.setRedPlayer(red);
        currentRecord.setBlackPlayer(black);
        if (result != null && !result.isEmpty()) currentRecord.setResult(result);
    }
}
