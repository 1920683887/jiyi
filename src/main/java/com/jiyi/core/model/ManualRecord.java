package com.jiyi.core.model;

import java.util.ArrayList;
import java.util.List;

public class ManualRecord {
    private String eventName = "";
    private String site = "";
    private String date = "";
    private String redPlayer = "";
    private String blackPlayer = "";
    private String result = "*";
    private int round;
    private final List<RecordNode> mainLine = new ArrayList<>();
    private String remark = "";
    /** 本棋谱的起始局面 FEN（PGN [FEN] 头用；空则视为标准开局） */
    private String startFen = "";

    public String startFen() { return startFen; }
    public void setStartFen(String v) { startFen = v; }

    public String eventName() { return eventName; }
    public void setEventName(String v) { eventName = v; }
    public String site() { return site; }
    public void setSite(String v) { site = v; }
    public String date() { return date; }
    public void setDate(String v) { date = v; }
    public String redPlayer() { return redPlayer; }
    public void setRedPlayer(String v) { redPlayer = v; }
    public String blackPlayer() { return blackPlayer; }
    public void setBlackPlayer(String v) { blackPlayer = v; }
    public String result() { return result; }
    public void setResult(String v) { result = v; }
    public int round() { return round; }
    public void setRound(int v) { round = v; }
    public List<RecordNode> mainLine() { return mainLine; }
    public String remark() { return remark; }
    public void setRemark(String v) { remark = v; }

    public void addMove(String moveUci, String chineseMove, String remark) {
        mainLine.add(new RecordNode(moveUci, chineseMove, remark));
    }

    /** 在指定索引插入一着（浏览到中间步续走时截断其后分支） */
    public void insertMove(int index, String moveUci, String chineseMove, String remark) {
        if (index < 0 || index > mainLine.size()) return;
        mainLine.add(index, new RecordNode(moveUci, chineseMove, remark));
        // 截断被插入点之后的旧主线（新着之后的旧着作废）
        if (index + 1 < mainLine.size()) {
            mainLine.subList(index + 1, mainLine.size()).clear();
        }
    }

    public static class RecordNode {
        private String moveUci;
        private String chineseMove;
        private String remark;
        private final List<RecordNode> variations = new ArrayList<>();

        public RecordNode(String moveUci, String chineseMove, String remark) {
            this.moveUci = moveUci;
            this.chineseMove = chineseMove;
            this.remark = remark;
        }

        public String moveUci() { return moveUci; }
        public void setMoveUci(String v) { moveUci = v; }
        public String chineseMove() { return chineseMove; }
        public void setChineseMove(String v) { chineseMove = v; }
        public String remark() { return remark; }
        public void setRemark(String v) { remark = v; }
        public List<RecordNode> variations() { return variations; }
    }
}
