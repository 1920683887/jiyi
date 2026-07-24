package com.jiyi.core.event;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;

public sealed interface GameEvent {
    record MoveExecuted(Board board, Move move, boolean isRed) implements GameEvent {}
    record UndoExecuted(Board board) implements GameEvent {}
    record GameStarted(Board board) implements GameEvent {}
    record GameEnded(Board board, String result) implements GameEvent {}
    record BoardChanged(Board board) implements GameEvent {}
    record SideSwitched(boolean redToGo) implements GameEvent {}
}
