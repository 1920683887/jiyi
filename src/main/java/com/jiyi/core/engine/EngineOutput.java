package com.jiyi.core.engine;

public sealed interface EngineOutput {
    record ThinkingData(int depth, int score, boolean isMate, long timeMs, long nps, int pv, String pvLine) implements EngineOutput {}
    record BestMove(String move) implements EngineOutput {}
    record Error(String message) implements EngineOutput {}
}
