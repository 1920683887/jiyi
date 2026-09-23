package com.jiyi.core.event;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;

import java.util.List;

public sealed interface EngineEvent {
    record EngineStarted(String name) implements EngineEvent {}
    record EngineStopped(String name) implements EngineEvent {}
    record ThinkingUpdate(ThinkData data) implements EngineEvent {}
    record BestMove(Move move, Board board) implements EngineEvent {}
    record AnalysisCompleted(List<ThinkData> pvs) implements EngineEvent {}

    record ThinkData(int depth, int score, boolean isMate, long timeMs, long nps, int pv, String pvLine) {
        public String formattedTitle() {
            String scoreStr;
            if (isMate) {
                // 正 mate=己方绝杀对方，负 mate=己方被绝杀（显示"负N步"），MIN_VALUE 防溢出
                int steps = score == Integer.MIN_VALUE ? 0 : Math.abs(score);
                scoreStr = score < 0 ? "被绝杀" + steps + "步" : "绝杀" + steps + "步";
            } else {
                scoreStr = "分数" + score;
            }
            return "深度" + depth + "  PV" + pv + "  " + scoreStr + "  NPS" + (nps / 1000) + "K  " +
                String.format("%.1fs", timeMs / 1000.0);
        }
    }
}
