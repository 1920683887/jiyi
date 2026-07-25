package com.jiyi.core.event;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public sealed interface DetectionEvent {
    record BoardDetected(Board board, Rectangle boardRect, BufferedImage source) implements DetectionEvent {}
    record BoardChangedByOpponent(Board newBoard, Move move) implements DetectionEvent {}
    record BoardChangedByEngine(Board newBoard, Move move) implements DetectionEvent {}
    record NewGameDetected(Board board) implements DetectionEvent {}
    record DetectionFailed(String reason) implements DetectionEvent {}
    record LinkStarted(String targetWindow) implements DetectionEvent {}
    record LinkStopped() implements DetectionEvent {}
}
