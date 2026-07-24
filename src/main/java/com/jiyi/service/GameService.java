package com.jiyi.service;

import com.google.inject.Inject;
import com.jiyi.core.event.EventBus;
import com.jiyi.core.event.GameEvent;
import com.jiyi.core.model.Board;
import com.jiyi.core.model.GameStatus;
import com.jiyi.core.model.Move;
import com.jiyi.core.rule.CheckDetector;
import com.jiyi.core.rule.MateDetector;
import com.jiyi.core.rule.MoveValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class GameService {
    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final EventBus eventBus;
    private final MoveValidator moveValidator;
    private final CheckDetector checkDetector;
    private final MateDetector mateDetector;
    private final ManualService manualService;

    private Board currentBoard;
    private boolean redToGo = true;
    private GameStatus status = GameStatus.IDLE;
    private final List<Board> boardHistory = new ArrayList<>();
    private final List<Move> moveHistory = new ArrayList<>();

    public record BoardState(Board board, Move move, boolean isRed) {}

    @Inject
    public GameService(EventBus eventBus, MoveValidator moveValidator,
                       CheckDetector checkDetector, MateDetector mateDetector,
                       ManualService manualService) {
        this.eventBus = eventBus;
        this.moveValidator = moveValidator;
        this.checkDetector = checkDetector;
        this.mateDetector = mateDetector;
        this.manualService = manualService;
    }

    public void startNewGame() {
        currentBoard = Board.STANDARD;
        redToGo = true;
        status = GameStatus.PLAYING;
        boardHistory.clear();
        moveHistory.clear();
        eventBus.post(new GameEvent.GameStarted(currentBoard));
        log.info("New game started");
    }

    public boolean executeMove(Move move) {
        if (status != GameStatus.PLAYING) {
            log.warn("Cannot move: game not playing");
            return false;
        }
        if (!moveValidator.canGo(currentBoard, move, redToGo)) {
            log.debug("Invalid move: {}", move.toUci());
            return false;
        }
        Board newBoard = currentBoard.apply(move);
        if (checkDetector.isInCheck(newBoard, redToGo)) {
            log.debug("Move would leave king in check: {}", move.toUci());
            return false;
        }

        boardHistory.add(currentBoard);
        moveHistory.add(move);
        currentBoard = newBoard;

        manualService.addMoveFromGameService(move);
        eventBus.post(new GameEvent.MoveExecuted(currentBoard, move, redToGo));

        if (mateDetector.isCheckmate(currentBoard, !redToGo)) {
            status = redToGo ? GameStatus.RED_WIN : GameStatus.BLACK_WIN;
            eventBus.post(new GameEvent.GameEnded(currentBoard,
                redToGo ? "红胜" : "黑胜"));
            log.info("Checkmate! {} wins", redToGo ? "Red" : "Black");
        } else if (mateDetector.isStalemate(currentBoard, !redToGo)) {
            status = GameStatus.DRAW;
            eventBus.post(new GameEvent.GameEnded(currentBoard, "和棋"));
            log.info("Stalemate! Draw");
        }

        redToGo = !redToGo;
        eventBus.post(new GameEvent.SideSwitched(redToGo));

        return true;
    }

    public void undo() {
        if (boardHistory.isEmpty()) return;
        currentBoard = boardHistory.removeLast();
        moveHistory.removeLast();
        redToGo = !redToGo;
        status = GameStatus.PLAYING;
        eventBus.post(new GameEvent.UndoExecuted(currentBoard));
        eventBus.post(new GameEvent.SideSwitched(redToGo));
    }

    public Board getCurrentBoard() { return currentBoard; }
    public boolean isRedToGo() { return redToGo; }
    public GameStatus getStatus() { return status; }
    public List<Move> getMoveHistory() { return List.copyOf(moveHistory); }
    public List<Board> getBoardHistory() { return List.copyOf(boardHistory); }
    public ManualService getManualService() { return manualService; }

    public Board getBoardAtMove(int moveIndex) {
        if (moveIndex < 0 || moveIndex >= moveHistory.size()) return currentBoard;
        Board b = boardHistory.get(0); // initial board before first move
        for (int i = 0; i <= moveIndex && i < moveHistory.size(); i++) {
            b = b.apply(moveHistory.get(i));
        }
        return b;
    }

    public List<BoardState> getFullHistory() {
        var states = new ArrayList<BoardState>();
        Board b = boardHistory.isEmpty() ? Board.STANDARD : boardHistory.get(0);
        boolean isRed = true;
        for (int i = 0; i < moveHistory.size(); i++) {
            b = b.apply(moveHistory.get(i));
            states.add(new BoardState(b, moveHistory.get(i), isRed));
            isRed = !isRed;
        }
        return states;
    }
}
