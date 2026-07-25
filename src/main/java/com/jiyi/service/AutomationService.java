package com.jiyi.service;

import com.google.inject.Inject;
import com.jiyi.core.detection.AutoClicker;
import com.jiyi.core.detection.BoardComparator;
import com.jiyi.core.detection.DetectionResult;
import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;
import com.jiyi.core.rule.MoveValidator;
import com.jiyi.infra.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.util.function.Consumer;

public class AutomationService {
    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);

    private final AutoClicker clicker;
    private final BoardComparator comparator;
    private final GameService gameService;
    private final EngineService engineService;
    private final Platform platform;

    private volatile boolean enabled;
    private volatile boolean engineIsRed;
    private volatile boolean analysisMode;
    private Board referenceBoard;
    private Rectangle boardRect;

    @Inject
    public AutomationService(Platform platform,
                             GameService gameService, EngineService engineService) {
        this.clicker = new AutoClicker(platform);
        this.comparator = new BoardComparator();
        this.gameService = gameService;
        this.engineService = engineService;
        this.platform = platform;
    }

    public void start(boolean enginePlaysRed, boolean analysisMode) {
        this.engineIsRed = enginePlaysRed;
        this.analysisMode = analysisMode;
        this.referenceBoard = null;
        this.boardRect = null;
        this.enabled = true;
        log.info("Automation started, engine={}, mode={}", enginePlaysRed ? "R" : "B",
            analysisMode ? "analysis" : "play");
    }

    public void stop() { this.enabled = false; }
    public boolean isEnabled() { return enabled; }

    // Called from DetectionService (synchronous, on detection thread)
    public synchronized void onBoardDetected(DetectionResult detResult, Board board, boolean flipped) {
        if (!enabled) return;
        if (detResult.boardRect() != null) {
            this.boardRect = detResult.boardRect();
            clicker.setBoardRect(detResult.boardRect());
        }

        if (referenceBoard == null) {
            referenceBoard = board;
            log.info("Initial board captured");
            return;
        }

        var diff = comparator.compare(board, referenceBoard, engineIsRed, analysisMode);
        if (diff == null) return;

        switch (diff.action()) {
            case OPPONENT_MOVED:
                log.debug("Opponent moved: {}", diff.diff());
                referenceBoard = board;
                if (engineService.isRunning()) {
                    engineService.analyze(board, engineIsRed);
                }
                break;

            case ENGINE_MOVED:
                log.debug("Engine move: {}", diff.diff());
                if (analysisMode) {
                    referenceBoard = board;
                    break;
                }
                if (diff.diff() != null && comparator.needConfirm(board, referenceBoard, diff)) {
                    log.debug("Awaiting confirmation for {}", diff.diff());
                    break;
                }
                referenceBoard = board;
                var move = diff.diff().toMove();
                clicker.click(move, flipped);
                gameService.executeMove(move);
                break;

            case NEW_GAME:
                log.info("New game detected");
                referenceBoard = board;
                break;

            case UNCLEAR:
                log.debug("Unclear board change, skipping");
                break;
        }
    }

}
