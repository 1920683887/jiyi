package com.jiyi.service;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.ManualRecord;
import com.jiyi.core.model.Move;
import com.jiyi.core.rule.ChineseTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ManualService {
    private static final Logger log = LoggerFactory.getLogger(ManualService.class);
    private static final String INITIAL_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";

    private ManualRecord currentRecord;
    private int currentIndex = -1;
    private final ChineseTranslator chineseTranslator;
    private final List<Board> boardStates = new ArrayList<>();

    public ManualService() {
        currentRecord = new ManualRecord();
        chineseTranslator = new ChineseTranslator();
        boardStates.add(Board.STANDARD);
    }

    public ManualRecord getRecord() { return currentRecord; }

    public void startNewRecord() {
        currentRecord = new ManualRecord();
        currentRecord.setDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")));
        currentIndex = -1;
        boardStates.clear();
        boardStates.add(Board.STANDARD);
    }

    /** 以指定 FEN 开始新棋谱（用于编辑局面/粘贴 FEN 后记录） */
    public void startNewRecord(String startFen) {
        currentRecord = new ManualRecord();
        currentRecord.setDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")));
        currentRecord.setStartFen(startFen);
        currentIndex = -1;
        boardStates.clear();
        try {
            boardStates.add(Board.fromFen(startFen));
        } catch (Exception e) {
            log.warn("Invalid startFen '{}', using standard", startFen);
            boardStates.add(Board.STANDARD);
        }
    }

    public void addMove(String moveUci, String chineseMove, String remark) {
        // ★ 浏览到中间步续走：新着插入 currentIndex+1 并截断后续（原实现恒追加尾部，
        //   导致 mainLine 与 boardStates 语义错位——新着被 apply 到旧末尾局面上）
        int insertAt = Math.max(0, currentIndex + 1);
        currentRecord.insertMove(insertAt, moveUci, chineseMove, remark);
        currentIndex = insertAt;

        // 重建 boardStates：保留 [0..insertAt]（下标 0=初始局面），在其后 apply 新着
        if (insertAt + 1 < boardStates.size()) {
            boardStates.subList(insertAt + 1, boardStates.size()).clear();
        }
        Board prevBoard = boardStates.isEmpty() ? Board.STANDARD : boardStates.get(boardStates.size() - 1);
        try {
            Move move = Move.fromUci(moveUci);
            Board newBoard = prevBoard.apply(move);
            boardStates.add(newBoard);
        } catch (Exception e) {
            log.warn("Failed to update board state for move: {}", moveUci, e);
        }
    }

    /** 删除指定索引的着法并重建局面历史（保持 mainLine 与 boardStates 同步） */
    public void deleteMoveAt(int index) {
        var line = currentRecord.mainLine();
        if (index < 0 || index >= line.size()) return;
        line.remove(index);
        if (!boardStates.isEmpty()) {
            boardStates.subList(line.size() + 1, boardStates.size()).clear();
        }
        if (currentIndex >= line.size()) {
            currentIndex = line.size() - 1;
        }
    }

    public void addMoveFromGameService(Move move) {
        String remark = "";
        Board currentBoard = getCurrentBoard();
        String chineseMove = "";

        try {
            chineseMove = chineseTranslator.toChinese(currentBoard, move);
            if (move.isCapture(currentBoard)) {
                char capturedPiece = currentBoard.pieceAt(move.toRow(), move.toCol());
                remark = "吃" + getPieceName(capturedPiece);
            }
        } catch (Exception e) {
            log.warn("Failed to generate Chinese notation for move: {}", move.toUci(), e);
        }

        addMove(move.toUci(), chineseMove, remark);
    }

    /**
     * 撤销最后一着（与 GameService.undo 联动）：删除主线最后一着并重建 boardStates。
     */
    public void removeLastMoveFromGameService() {
        var line = currentRecord.mainLine();
        if (line.isEmpty()) {
            log.warn("Cannot remove move: manual record is empty");
            return;
        }
        line.remove(line.size() - 1);
        // 重建 boardStates：与当前主线长度对应（第 0 项为初始局面）
        if (!boardStates.isEmpty()) {
            boardStates.subList(line.size() + 1, boardStates.size()).clear();
        }
        currentIndex = line.size() - 1;
        log.info("Removed last move from manual record, mainLine={}", line.size());
    }

    private String getPieceName(char piece) {
        return switch (Character.toUpperCase(piece)) {
            case 'K' -> Character.isUpperCase(piece) ? "帅" : "将";
            case 'A' -> Character.isUpperCase(piece) ? "仕" : "士";
            case 'B' -> Character.isUpperCase(piece) ? "相" : "象";
            case 'N' -> "马";
            case 'R' -> "车";
            case 'C' -> "炮";
            case 'P' -> Character.isUpperCase(piece) ? "兵" : "卒";
            default -> "子";
        };
    }

    public int currentIndex() { return currentIndex; }

    public void setCurrentIndex(int i) {
        if (i >= -1 && i < currentRecord.mainLine().size()) {
            currentIndex = i;
        }
    }

    public Board getCurrentBoard() {
        if (currentIndex < 0 || boardStates.isEmpty()) {
            return Board.STANDARD;
        }
        // currentIndex=N 表示走完第 N+1 着，对应 boardStates[N+1]（下标 0=初始局面）
        int idx = currentIndex + 1;
        if (idx < boardStates.size()) {
            return boardStates.get(idx);
        }
        return boardStates.get(boardStates.size() - 1);
    }

    /** 走完第 index 着后的局面（index 从 0 起；越界返回 null） */
    public Board getBoardAt(int index) {
        if (index < 0 || index + 1 >= boardStates.size()) return null;
        return boardStates.get(index + 1);
    }

    public boolean canGoPrev() { return currentIndex >= 0; }
    public boolean canGoNext() { return currentIndex < currentRecord.mainLine().size() - 1; }

    public int totalMoves() { return currentRecord.mainLine().size(); }

    public void goPrev() {
        if (canGoPrev()) {
            currentIndex--;
        }
    }

    public void goNext() {
        if (canGoNext()) {
            currentIndex++;
        }
    }

    public void goToMove(int index) {
        if (index >= -1 && index < currentRecord.mainLine().size()) {
            currentIndex = index;
        }
    }

    public void goToStart() {
        currentIndex = -1;
    }

    public void goToEnd() {
        currentIndex = currentRecord.mainLine().size() - 1;
    }

    public ManualRecord.RecordNode getCurrentMove() {
        if (currentIndex < 0 || currentIndex >= currentRecord.mainLine().size()) {
            return null;
        }
        return currentRecord.mainLine().get(currentIndex);
    }

    public void updateMoveRemark(int index, String remark) {
        if (index >= 0 && index < currentRecord.mainLine().size()) {
            currentRecord.mainLine().get(index).setRemark(remark);
        }
    }

    public void updateCurrentMoveRemark(String remark) {
        if (currentIndex >= 0 && currentIndex < currentRecord.mainLine().size()) {
            currentRecord.mainLine().get(currentIndex).setRemark(remark);
        }
    }

    public void deleteMove(int index) {
        if (index >= 0 && index < currentRecord.mainLine().size()) {
            currentRecord.mainLine().remove(index);
            if (index < boardStates.size()) {
                boardStates.subList(index + 1, boardStates.size()).clear();
            }
            if (currentIndex >= currentRecord.mainLine().size()) {
                currentIndex = currentRecord.mainLine().size() - 1;
            }
        }
    }

    public void deleteMovesFrom(int index) {
        if (index >= 0 && index < currentRecord.mainLine().size()) {
            currentRecord.mainLine().subList(index, currentRecord.mainLine().size()).clear();
            if (index < boardStates.size()) {
                boardStates.subList(index + 1, boardStates.size()).clear();
            }
            if (currentIndex >= currentRecord.mainLine().size()) {
                currentIndex = currentRecord.mainLine().size() - 1;
            }
        }
    }

    public void savePgn(Path path) throws IOException {
        var sb = new StringBuilder();
        var r = currentRecord;

        // Write headers
        sb.append("[Event \"").append(r.eventName().isEmpty() ? "?" : r.eventName()).append("\"]\n");
        sb.append("[Site \"").append(r.site().isEmpty() ? "?" : r.site()).append("\"]\n");
        sb.append("[Date \"").append(r.date().isEmpty() ? "????.??.??" : r.date()).append("\"]\n");
        sb.append("[Round \"").append(r.round() == 0 ? "?" : String.valueOf(r.round())).append("\"]\n");
        sb.append("[Red \"").append(r.redPlayer().isEmpty() ? "?" : r.redPlayer()).append("\"]\n");
        sb.append("[Black \"").append(r.blackPlayer().isEmpty() ? "?" : r.blackPlayer()).append("\"]\n");
        sb.append("[Result \"").append(r.result().isEmpty() ? "*" : r.result()).append("\"]\n");
        // [FEN] 头：写实际起始局面（若为空视为标准开局）
        sb.append("[FEN \"").append(r.startFen().isEmpty() ? INITIAL_FEN : r.startFen()).append("\"]\n");
        // 着法统一用 ICCS 格式（与 TCHESS 互通），中文着法保留为注释
        sb.append("[Format \"ICCS\"]\n");

        sb.append("\n");

        // Write moves (ICCS: h2-e2)
        var moves = r.mainLine();
        for (int i = 0; i < moves.size(); i++) {
            int moveNum = i / 2 + 1;
            if (i % 2 == 0) {
                sb.append(moveNum).append(". ");
            }

            var node = moves.get(i);
            String m = toIccs(node.moveUci());
            sb.append(m);

            // Add Chinese notation as comment if available
            if (!node.chineseMove().isEmpty()) {
                sb.append(" {").append(node.chineseMove()).append("}");
            }

            // Add remark as comment
            if (!node.remark().isEmpty()) {
                sb.append(" {").append(node.remark()).append("}");
            }

            sb.append(" ");

            // Line break every 5 moves for readability
            if ((i + 1) % 10 == 0) {
                sb.append("\n");
            }
        }

        // Add result
        String result = r.result();
        if (result.isEmpty() || result.equals("*")) {
            sb.append("*");
        } else {
            sb.append(result);
        }
        sb.append("\n");

        Files.writeString(path, sb.toString());
        log.info("Saved PGN to: {}", path);
    }

    /** UCI (h2e2) → ICCS (h2-e2) */
    private String toIccs(String uci) {
        if (uci == null || uci.length() < 4) return uci;
        return uci.substring(0, 2) + "-" + uci.substring(2, 4);
    }

    public ManualRecord loadPgn(Path path) throws IOException {
        String content = Files.readString(path);
        var record = new ManualRecord();
        String[] lines = content.split("\n");

        // Parse headers
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
                    case "Round" -> {
                        try {
                            record.setRound(Integer.parseInt(val));
                        } catch (NumberFormatException e) {
                            record.setRound(0);
                        }
                    }
                    case "FEN" -> record.setStartFen(val);
                }
            }
        }

        // Find move section (after last header)
        int moveStart = content.lastIndexOf("\n\n");
        if (moveStart < 0) moveStart = 0;
        String moveText = content.substring(moveStart).trim();

        // Extract Chinese moves from comments
        List<String> chineseMoves = new ArrayList<>();
        String workingText = moveText;
        while (workingText.contains("{") && workingText.contains("}")) {
            int start = workingText.indexOf("{");
            int end = workingText.indexOf("}");
            if (start < end) {
                String comment = workingText.substring(start + 1, end).trim();
                chineseMoves.add(comment);
                workingText = workingText.substring(0, start) + workingText.substring(end + 1);
            } else {
                break;
            }
        }

        // Clean move text
        moveText = workingText;
        moveText = moveText.replaceAll("\\{.*?\\}", ""); // Remove comments
        moveText = moveText.replaceAll("\\$\\d+", ""); // Remove NAGs
        moveText = moveText.replaceAll("\\d+\\.\\.\\.", ""); // Remove ...
        moveText = moveText.replaceAll("\\d+\\.", ""); // Remove move numbers
        moveText = moveText.replaceAll("[\\*\\?!/=+#]", ""); // Remove annotations
        moveText = moveText.replaceAll("\\s+", " ").trim();

        // Remove result from move text
        String resultStr = record.result();
        if (!resultStr.equals("*") && !resultStr.isEmpty()) {
            moveText = moveText.replace(resultStr, "");
        }

        // Parse moves（支持 ICCS `h2-e2` 与纯 UCI `h2e2`）
        String[] tokens = moveText.split("\\s+");
        int chineseIndex = 0;
        for (String t : tokens) {
            if (t.isEmpty() || t.equals(resultStr) || t.equals("*")) continue;

            // 去掉 ICCS 连字符后校验为合法 UCI 着法
            if (t.length() >= 4 && !t.contains(".")) {
                try {
                    String uci = t.replace("-", "");
                    Move.fromUci(uci); // Validate
                    String chinese = chineseIndex < chineseMoves.size() ? chineseMoves.get(chineseIndex) : "";
                    record.addMove(uci, chinese, "");
                    chineseIndex++;
                } catch (Exception e) {
                    log.warn("Skipping invalid move: {}", t);
                }
            }
        }

        currentRecord = record;
        currentIndex = -1;

        // Rebuild board states（以 [FEN] 头为起始局面，无则标准开局）
        Board startBoard;
        try {
            startBoard = record.startFen().isEmpty() ? Board.STANDARD : Board.fromFen(record.startFen());
        } catch (Exception e) {
            log.warn("Invalid [FEN] header '{}', falling back to standard", record.startFen());
            startBoard = Board.STANDARD;
        }
        boardStates.clear();
        boardStates.add(startBoard);
        Board currentBoard = startBoard;
        for (var node : record.mainLine()) {
            try {
                Move move = Move.fromUci(node.moveUci());
                currentBoard = currentBoard.apply(move);
                boardStates.add(currentBoard);
            } catch (Exception e) {
                log.warn("Failed to apply move {} while loading PGN", node.moveUci(), e);
            }
        }

        log.info("Loaded PGN from: {} ({} moves)", path, record.mainLine().size());
        return record;
    }

    public void setMetadata(String event, String site, String red, String black, String result) {
        currentRecord.setEventName(event);
        currentRecord.setSite(site);
        currentRecord.setRedPlayer(red);
        currentRecord.setBlackPlayer(black);
        if (result != null && !result.isEmpty()) currentRecord.setResult(result);
    }

    public void setRound(int round) {
        currentRecord.setRound(round);
    }

    public void setResult(String result) {
        if (result != null && !result.isEmpty()) {
            currentRecord.setResult(result);
        }
    }

    public String exportToText() {
        StringBuilder sb = new StringBuilder();
        var r = currentRecord;

        sb.append("赛事: ").append(r.eventName()).append("\n");
        sb.append("地点: ").append(r.site()).append("\n");
        sb.append("日期: ").append(r.date()).append("\n");
        sb.append("红方: ").append(r.redPlayer()).append("\n");
        sb.append("黑方: ").append(r.blackPlayer()).append("\n");
        sb.append("结果: ").append(r.result()).append("\n");
        sb.append("\n");

        var moves = r.mainLine();
        for (int i = 0; i < moves.size(); i++) {
            int moveNum = i / 2 + 1;
            var node = moves.get(i);

            if (i % 2 == 0) {
                sb.append(String.format("%3d. ", moveNum));
            }

            // Show Chinese move if available, otherwise UCI
            String moveStr = !node.chineseMove().isEmpty() ? node.chineseMove() : node.moveUci();
            sb.append(String.format("%-12s", moveStr));

            if (!node.remark().isEmpty()) {
                sb.append(" [").append(node.remark()).append("]");
            }

            if (i % 2 == 1) {
                sb.append("\n");
            }
        }

        if (moves.size() % 2 == 1) {
            sb.append("\n");
        }

        sb.append("\n").append(r.result());
        return sb.toString();
    }

    public List<String> getMoveList() {
        return currentRecord.mainLine().stream()
                .map(ManualRecord.RecordNode::moveUci)
                .toList();
    }

    public List<String> getChineseMoveList() {
        return currentRecord.mainLine().stream()
                .map(node -> !node.chineseMove().isEmpty() ? node.chineseMove() : node.moveUci())
                .toList();
    }

    public void clear() {
        startNewRecord();
    }

    public boolean isEmpty() {
        return currentRecord.mainLine().isEmpty();
    }

    public void loadFromFen(String fen) {
        boardStates.clear();
        try {
            Board board = Board.fromFen(fen);
            boardStates.add(board);
            currentRecord = new ManualRecord();
            currentIndex = -1;
            log.info("Loaded manual from FEN: {}", fen);
        } catch (Exception e) {
            log.error("Failed to load FEN: {}", fen, e);
            boardStates.add(Board.STANDARD);
        }
    }

    /**
     * 添加变招到指定步
     * @param moveIndex 主线着法索引
     * @param move 变招着法
     * @param remark 变招备注
     */
    public void addVariation(int moveIndex, Move move, String remark) {
        if (moveIndex < 0 || moveIndex >= currentRecord.mainLine().size()) {
            log.warn("Invalid move index for adding variation: {}", moveIndex);
            return;
        }

        ManualRecord.RecordNode node = currentRecord.mainLine().get(moveIndex);
        Board boardAtMove = moveIndex < boardStates.size() ? boardStates.get(moveIndex) : getCurrentBoard();

        String chineseMove = "";
        try {
            chineseMove = chineseTranslator.toChinese(boardAtMove, move);
        } catch (Exception e) {
            log.warn("Failed to generate Chinese notation for variation: {}", move.toUci(), e);
        }

        ManualRecord.RecordNode variationNode = new ManualRecord.RecordNode(
                move.toUci(), chineseMove, remark);
        node.variations().add(variationNode);

        log.info("Added variation to move {}: {}", moveIndex, move.toUci());
    }

    /**
     * 删除指定步的变招
     * @param moveIndex 主线着法索引
     * @param variationIndex 变招索引
     */
    public void removeVariation(int moveIndex, int variationIndex) {
        if (moveIndex < 0 || moveIndex >= currentRecord.mainLine().size()) {
            log.warn("Invalid move index for removing variation: {}", moveIndex);
            return;
        }

        ManualRecord.RecordNode node = currentRecord.mainLine().get(moveIndex);
        List<ManualRecord.RecordNode> variations = node.variations();

        if (variationIndex < 0 || variationIndex >= variations.size()) {
            log.warn("Invalid variation index for removing: {}", variationIndex);
            return;
        }

        ManualRecord.RecordNode removed = variations.remove(variationIndex);
        log.info("Removed variation {} from move {}: {}", variationIndex, moveIndex, removed.moveUci());
    }

    /**
     * 进入变招（将变招提升为主线，原主线变为变招）
     * @param moveIndex 主线着法索引
     * @param variationIndex 变招索引
     */
    public void navigateToVariation(int moveIndex, int variationIndex) {
        if (moveIndex < 0 || moveIndex >= currentRecord.mainLine().size()) {
            log.warn("Invalid move index for navigating to variation: {}", moveIndex);
            return;
        }

        ManualRecord.RecordNode node = currentRecord.mainLine().get(moveIndex);
        List<ManualRecord.RecordNode> variations = node.variations();

        if (variationIndex < 0 || variationIndex >= variations.size()) {
            log.warn("Invalid variation index for navigating: {}", variationIndex);
            return;
        }

        // 交换主线和变招
        ManualRecord.RecordNode variationNode = variations.get(variationIndex);
        ManualRecord.RecordNode tempNode = new ManualRecord.RecordNode(
                node.moveUci(), node.chineseMove(), node.remark());

        // 保存原主线的变招（除了要提升的那个）
        for (int i = 0; i < variations.size(); i++) {
            if (i != variationIndex) {
                tempNode.variations().add(variations.get(i));
            }
        }

        // 将原主线作为变招添加到新主线
        variationNode.variations().add(tempNode);

        // 替换主线
        node.setMoveUci(variationNode.moveUci());
        node.setChineseMove(variationNode.chineseMove());
        node.setRemark(variationNode.remark());
        node.variations().clear();
        node.variations().addAll(variationNode.variations());

        // 重建该位置之后的棋盘状态
        if (moveIndex + 1 < boardStates.size()) {
            boardStates.subList(moveIndex + 1, boardStates.size()).clear();
        }

        Board prevBoard = moveIndex > 0 ? boardStates.get(moveIndex) : Board.STANDARD;
        try {
            Move move = Move.fromUci(node.moveUci());
            Board newBoard = prevBoard.apply(move);
            if (moveIndex + 1 < boardStates.size()) {
                boardStates.set(moveIndex + 1, newBoard);
            } else {
                boardStates.add(newBoard);
            }
        } catch (Exception e) {
            log.warn("Failed to update board state after navigating to variation: {}", node.moveUci(), e);
        }

        log.info("Navigated to variation {} at move {}", variationIndex, moveIndex);
    }

    /**
     * 获取指定步的所有变招
     * @param moveIndex 主线着法索引
     * @return 变招列表
     */
    public List<ManualRecord.RecordNode> getVariations(int moveIndex) {
        if (moveIndex < 0 || moveIndex >= currentRecord.mainLine().size()) {
            log.warn("Invalid move index for getting variations: {}", moveIndex);
            return new ArrayList<>();
        }

        return currentRecord.mainLine().get(moveIndex).variations();
    }
}
