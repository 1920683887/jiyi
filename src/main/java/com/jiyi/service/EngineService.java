package com.jiyi.service;

import com.google.inject.Inject;
import com.jiyi.core.engine.*;
import com.jiyi.core.event.EngineEvent;
import com.jiyi.core.event.EventBus;
import com.jiyi.core.model.Move;
import com.jiyi.core.model.Board;
import com.jiyi.infra.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class EngineService {
    private static final Logger log = LoggerFactory.getLogger(EngineService.class);

    private final EventBus eventBus;
    private final Config config;
    private EngineProcess process;
    private Object protocol; // UciProtocol or UcciProtocol
    private volatile boolean running;
    private final List<Move> currentMoves = new ArrayList<>();
    private final AtomicReference<EngineConfig> engineConfig = new AtomicReference<>();

    @Inject
    public EngineService(EventBus eventBus, Config config) {
        this.eventBus = eventBus;
        this.config = config;
    }

    public void startEngine(EngineConfig cfg) {
        if (running) stopEngine();
        engineConfig.set(cfg);
        try {
            process = new EngineProcess(cfg.path());
            ProtocolDetector detector = new ProtocolDetector();
            var detected = detector.detect(process);

            switch (detected) {
                case UCI -> {
                    var uci = new UciProtocol(process);
                    setupUci(uci, cfg);
                    protocol = uci;
                }
                case UCCI -> {
                    var ucci = new UcciProtocol(process);
                    setupUcci(ucci, cfg);
                    protocol = ucci;
                }
                case UNKNOWN -> {
                    log.error("Unknown engine protocol for {}", cfg.name());
                    process.close();
                    return;
                }
            }

            running = true;
            eventBus.post(new EngineEvent.EngineStarted(cfg.name()));
            log.info("Engine started: {} ({})", cfg.name(), detected);
        } catch (IOException e) {
            log.error("Failed to start engine: {}", cfg.path(), e);
        }
    }

    private void setupUci(UciProtocol uci, EngineConfig cfg) {
        uci.setThreads(cfg.threads());
        uci.setHash(cfg.hash());
        uci.setOutputCallback(output -> {
            switch (output) {
                case EngineOutput.ThinkingData d -> {
                    var td = new EngineEvent.ThinkData(d.depth(), d.score(),
                        d.isMate(), d.timeMs(), d.nps(), d.pv(), d.pvLine());
                    eventBus.post(new EngineEvent.ThinkingUpdate(td));
                }
                case EngineOutput.BestMove bm -> {
                    Move move = Move.fromUci(bm.move());
                    currentMoves.add(move);
                    eventBus.post(new EngineEvent.BestMove(move, null));
                }
                default -> {}
            }
        });
    }

    private void setupUcci(UcciProtocol ucci, EngineConfig cfg) {
        ucci.setThreads(cfg.threads());
        ucci.setHash(cfg.hash());
        ucci.setOutputCallback(output -> {
            switch (output) {
                case EngineOutput.ThinkingData d -> {
                    var td = new EngineEvent.ThinkData(d.depth(), d.score(),
                        d.isMate(), d.timeMs(), d.nps(), d.pv(), d.pvLine());
                    eventBus.post(new EngineEvent.ThinkingUpdate(td));
                }
                case EngineOutput.BestMove bm -> {
                    Move move = Move.fromUci(bm.move());
                    currentMoves.add(move);
                    eventBus.post(new EngineEvent.BestMove(move, null));
                }
                default -> {}
            }
        });
    }

    public void analyze(Board board) {
        if (!running || protocol == null) return;
        currentMoves.clear();
        // Stop any ongoing search before sending new position + go
        stopSearch();
        sendPosition(board);
        long time = config.engine().analysisValue();
        if ("FIXED_TIME".equals(config.engine().analysisModel())) {
            if (protocol instanceof UciProtocol uci) uci.goTime(time);
            else if (protocol instanceof UcciProtocol ucci) ucci.goTime(time);
        } else {
            int depth = (int) time;
            if (protocol instanceof UciProtocol uci) uci.goDepth(depth);
            else if (protocol instanceof UcciProtocol ucci) ucci.goDepth(depth);
        }
    }

    private void stopSearch() {
        if (protocol instanceof UciProtocol uci) uci.stop();
        else if (protocol instanceof UcciProtocol ucci) ucci.stop();
    }

    private void sendPosition(Board board) {
        if (protocol instanceof UciProtocol uci) {
            uci.position(board, List.of());
        } else if (protocol instanceof UcciProtocol ucci) {
            ucci.position(board, List.of());
        }
    }

    public void stopEngine() {
        if (!running) return;
        running = false;
        if (protocol instanceof UciProtocol uci) uci.quit();
        else if (protocol instanceof UcciProtocol ucci) ucci.quit();
        process = null;
        protocol = null;
        eventBus.post(new EngineEvent.EngineStopped(
            engineConfig.get() != null ? engineConfig.get().name() : "unknown"));
    }

    public boolean isRunning() { return running; }
}
