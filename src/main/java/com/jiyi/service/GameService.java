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
    /** 本局起始局面：startNewGame=标准开局，loadFen=加载的 FEN 局面（供 getFullHistory 起算） */
    private Board historyBaseBoard = Board.STANDARD;

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
        synchronized (this) {
            currentBoard = Board.STANDARD;
            historyBaseBoard = Board.STANDARD;
            redToGo = true;
            status = GameStatus.PLAYING;
            boardHistory.clear();
            moveHistory.clear();
        }
        eventBus.post(new GameEvent.GameStarted(currentBoard));
        log.info("New game started");
    }

    public void loadFen(String fen) {
        synchronized (this) {
            currentBoard = Board.fromFen(fen);
            historyBaseBoard = currentBoard;
            // 行棋方：FEN 第 2 段 "w"/"b"，缺失默认红先（不再用 contains(" b ") 误判）
            String[] parts = fen.trim().split("\\s+");
            redToGo = parts.length < 2 || !"b".equals(parts[1]);
            status = GameStatus.PLAYING;
            boardHistory.clear();
            moveHistory.clear();
        }
        eventBus.post(new GameEvent.GameStarted(currentBoard));
        log.info("Loaded FEN: {}", fen);
    }

    /**
     * 连线模式局面同步：仅更新当前局面与行棋方，不清历史、不发 GameStarted。
     * （loadFen 每步清空棋谱并重置 UI，连线同步应走此方法——对手每步只更新局面）
     */
    public void syncBoard(Board board, boolean redToGo) {
        synchronized (this) {
            currentBoard = board;
            this.redToGo = redToGo;
            status = GameStatus.PLAYING;
        }
        eventBus.post(new GameEvent.BoardChanged(board));
    }

    public synchronized boolean executeMove(Move move) {
        if (status != GameStatus.PLAYING) {
            log.warn("Cannot move: game not playing");
            return false;
        }
        if (!moveValidator.canGo(currentBoard, move, redToGo)) {
            char piece = currentBoard.pieceAt(move.fromRow(), move.fromCol());
            char target = currentBoard.pieceAt(move.toRow(), move.toCol());
            log.warn("Invalid move: {} (from {}x{}='{}' to {}x{}='{}' turn={})",
                move.toUci(), move.fromRow(), move.fromCol(), piece,
                move.toRow(), move.toCol(), target, redToGo);
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

        if (mateDetector.isNoLegalMove(currentBoard, !redToGo)) {
            // 被将军=将死；未被将军=困毙。中国象棋规则下无子可动即负，均判对方胜
            status = redToGo ? GameStatus.RED_WIN : GameStatus.BLACK_WIN;
            eventBus.post(new GameEvent.GameEnded(currentBoard,
                redToGo ? "红胜" : "黑胜"));
            log.info("Game over (checkmate or stalemate)! {} wins", redToGo ? "Red" : "Black");
        }

        redToGo = !redToGo;
        eventBus.post(new GameEvent.SideSwitched(redToGo));

        return true;
    }

    public synchronized void undo() {
        if (boardHistory.isEmpty()) {
            log.warn("Cannot undo: no history available");
            return;
        }
        currentBoard = boardHistory.removeLast();
        moveHistory.removeLast();
        // 同步撤销棋谱记录，避免 undo 后棋谱与实际局面不一致
        manualService.removeLastMoveFromGameService();
        redToGo = !redToGo;
        status = GameStatus.PLAYING;
        eventBus.post(new GameEvent.UndoExecuted(currentBoard));
        eventBus.post(new GameEvent.SideSwitched(redToGo));
    }

    public synchronized Board getCurrentBoard() { return currentBoard; }
    public synchronized boolean isRedToGo() { return redToGo; }
    public synchronized GameStatus getStatus() { return status; }
    public synchronized List<Move> getMoveHistory() { return List.copyOf(moveHistory); }
    public synchronized List<Board> getBoardHistory() { return List.copyOf(boardHistory); }
    public ManualService getManualService() { return manualService; }

    public synchronized Board getBoardAtMove(int moveIndex) {
        if (moveIndex < 0 || moveIndex >= moveHistory.size()) return currentBoard;
        Board b = historyBaseBoard;
        for (int i = 0; i <= moveIndex && i < moveHistory.size(); i++) {
            b = b.apply(moveHistory.get(i));
        }
        return b;
    }

    public synchronized List<BoardState> getFullHistory() {
        var states = new ArrayList<BoardState>();
        Board b = historyBaseBoard;
        boolean isRed = redToGo; // 起始走棋方
        for (int i = 0; i < moveHistory.size(); i++) {
            b = b.apply(moveHistory.get(i));
            states.add(new BoardState(b, moveHistory.get(i), isRed));
            isRed = !isRed;
        }
        return states;
    }
}
