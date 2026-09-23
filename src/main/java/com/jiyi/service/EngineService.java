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
    private volatile Move lastBestMove;
    private volatile int lastThreads = -1;
    private volatile int lastHash = -1;
    /** 本次搜索是否在进行中：analyze/analyzeWithExcludedMoves 置 true，收到本次 bestmove 置 false */
    private volatile boolean searchPending;
    /** 本次搜索的 bestmove 等待闩：每次搜索前 new 一个，stopThinkingAndMove 用它等待"本次"结果而非读旧缓存 */
    private volatile java.util.concurrent.CountDownLatch bestMoveLatch = new java.util.concurrent.CountDownLatch(1);
    /** 思考看门狗（对齐 Qt 两阶段看门狗）：本次 go 的开始时刻 + 动态时限。
     *  阶段1 超时 → 裸 stop 催招（bestmove 正常回传不丢步）+ 10s 宽限；
     *  宽限后仍无 bestmove → 阶段2 完整复位（防协议卡死冻结连线） */
    private volatile long searchStartNanos;
    private volatile long thinkDeadlineMs = THINK_TIMEOUT_MS;
    private volatile int watchdogStage = 0;   // 0=未催招 1=已催招（宽限期内）
    private static final long THINK_TIMEOUT_MS = 30000;
    private static final long THINK_DEPTH_DEADLINE_MS = 120000;
    private static final long THINK_STAGE2_GRACE_MS = 10000;

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

            // Send Threads/Hash options
            sendEngineOptions(cfg);

            // isready/readyok handshake
            process.send("isready");
            boolean ready = process.waitForLine("readyok", 5000);
            if (!ready) {
                log.warn("Engine {} did not respond to isready", cfg.name());
            }

            running = true;
            lastThreads = cfg.threads();
            lastHash = cfg.hash();
            eventBus.post(new EngineEvent.EngineStarted(cfg.name()));
            log.info("Engine started: {} ({})", cfg.name(), detected);

            // ★ 引擎崩溃处理（对齐 C++ UciEngine QProcess::finished）：
            //   进程退出回调里用 running 标志区分：主动停止（stopEngine 已置 false）不处理；
            //   非主动退出 = 崩溃 → 清状态 + 发 EngineStopped（UI 提示，连线可重同步）
            var proc = process;
            proc.onExit(p -> {
                if (running && this.process == proc) {
                    log.error("Engine process exited unexpectedly (crash?): {}", cfg.name());
                    running = false;
                    searchPending = false;
                    watchdogStage = 0;
                    bestMoveLatch.countDown();
                    eventBus.post(new EngineEvent.EngineStopped(cfg.name()));
                }
            });
        } catch (IOException e) {
            log.error("Failed to start engine: {}", cfg.path(), e);
        }
    }

    private void sendEngineOptions(EngineConfig cfg) {
        if (protocol instanceof UciProtocol uci) {
            uci.setThreads(cfg.threads());
            uci.setHash(cfg.hash());
            for (var opt : cfg.customOptions().entrySet()) {
                uci.setOption(opt.getKey(), opt.getValue());
            }
        } else if (protocol instanceof UcciProtocol ucci) {
            ucci.setThreads(cfg.threads());
            ucci.setHash(cfg.hash());
            for (var opt : cfg.customOptions().entrySet()) {
                ucci.setOption(opt.getKey(), opt.getValue());
            }
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
                    // ★ 损坏串防护：合法着法必为 [a-i][0-9][a-i][0-9]（ICCS 四字符）；
                    //   实测出现过 "(62o62" 之类的损坏 bestmove，解析出垃圾着法会被点击执行
                    String rawMove = bm.move();
                    if (rawMove == null || !rawMove.matches("[a-i][0-9][a-i][0-9].*")) {
                        log.warn("Discard malformed bestmove: {}", rawMove);
                        return;
                    }
                    // ★ stop 后 readyok 前的 bestmove 是旧搜索收尾输出（对齐 C++ stopFlag 丢弃）
                    if (process != null && process.isStopFlag()) {
                        log.debug("Discard stale bestmove (stopFlag): {}", bm.move());
                        return;
                    }
                    Move move = Move.fromUci(bm.move());
                    lastBestMove = move;
                    if (searchPending) {
                        currentMoves.add(move);
                        log.info("bestmove received: {} (searchPending=true, posting BestMove event)", move.toUci());
                        eventBus.post(new EngineEvent.BestMove(move, null));
                        searchPending = false;
                        watchdogStage = 0;   // 收到 bestmove = 引擎已出招：停看门狗
                        bestMoveLatch.countDown();
                    } else {
                        log.debug("bestmove received but no search pending, cached: {}", move.toUci());
                    }
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
                    // ★ 损坏串防护：合法着法必为 [a-i][0-9][a-i][0-9]（ICCS 四字符）；
                    //   实测出现过 "(62o62" 之类的损坏 bestmove，解析出垃圾着法会被点击执行
                    String rawMove = bm.move();
                    if (rawMove == null || !rawMove.matches("[a-i][0-9][a-i][0-9].*")) {
                        log.warn("Discard malformed bestmove: {}", rawMove);
                        return;
                    }
                    // ★ stop 后 readyok 前的 bestmove 是旧搜索收尾输出（对齐 C++ stopFlag 丢弃）
                    if (process != null && process.isStopFlag()) {
                        log.debug("Discard stale bestmove (stopFlag): {}", bm.move());
                        return;
                    }
                    Move move = Move.fromUci(bm.move());
                    lastBestMove = move;
                    if (searchPending) {
                        currentMoves.add(move);
                        log.info("bestmove received: {} (searchPending=true, posting BestMove event)", move.toUci());
                        eventBus.post(new EngineEvent.BestMove(move, null));
                        searchPending = false;
                        watchdogStage = 0;   // 收到 bestmove = 引擎已出招：停看门狗
                        bestMoveLatch.countDown();
                    } else {
                        log.debug("bestmove received but no search pending, cached: {}", move.toUci());
                    }
                }
                default -> {}
            }
        });
    }

    /**
     * 运行时热切换线程数/哈希 - 仅在值变化时发送option命令
     */
    public void applyOptions() {
        if (!running || protocol == null) return;
        var cfg = engineConfig.get();
        if (cfg == null) return;

        int newThreads = cfg.threads();
        int newHash = cfg.hash();

        if (newThreads != lastThreads) {
            if (protocol instanceof UciProtocol uci) {
                uci.setThreads(newThreads);
            } else if (protocol instanceof UcciProtocol ucci) {
                ucci.setThreads(newThreads);
            }
            lastThreads = newThreads;
            log.debug("Hot-switched threads to {}", newThreads);
        }

        if (newHash != lastHash) {
            if (protocol instanceof UciProtocol uci) {
                uci.setHash(newHash);
            } else if (protocol instanceof UcciProtocol ucci) {
                ucci.setHash(newHash);
            }
            lastHash = newHash;
            log.debug("Hot-switched hash to {}MB", newHash);
        }
    }

    public void analyze(Board board, boolean redGo) {
        if (!running || protocol == null) {
            log.warn("analyze skipped: running={}, protocol={}", running, protocol);
            return;
        }
        log.info("analyze(boardFen={}, redGo={})", board.toFen(redGo), redGo);
        applyOptions();
        currentMoves.clear();
        // ★ readyok 门控打断旧搜索（对齐 C++ stopThinking）：stop+isready 后新命令入队，
        //   readyok 冲刷，保证 position/go 不撞上收尾中的旧搜索
        interruptSearch();
        // 登记本次搜索：等待本次 bestmove（若引擎正在思考，stop 后需重新 go）
        beginSearch();

        // 引擎延迟/防检测：在配置的延迟范围内随机等待
        var cfg = engineConfig.get();
        if (cfg != null) {
            int delayStart = config.engine().delayStartMs();
            int delayEnd = config.engine().delayEndMs();
            if (delayEnd > delayStart && delayStart >= 0) {
                int delay = delayStart + (int)(Math.random() * (delayEnd - delayStart));
                if (delay > 0) {
                    log.debug("Engine delay: {}ms (range {}-{}ms)", delay, delayStart, delayEnd);
                    sleep(delay);
                }
            }
        }

        sleep(50);
        sendPosition(board, redGo);
        if (cfg == null) return;
        String model = cfg.analysisModel() != null ? cfg.analysisModel() : "FIXED_TIME";
        long value = cfg.analysisValue();
        if ("FIXED_TIME".equals(model)) {
            if (protocol instanceof UciProtocol uci) uci.goTime(value);
            else if (protocol instanceof UcciProtocol ucci) ucci.goTime(value);
        } else {
            // 固定深度：上限 64 层防溢出/引擎拒绝；非法值回退固定时间 5s
            int depth = (int) Math.min(value, 64);
            if (depth <= 0) {
                if (protocol instanceof UciProtocol uci) uci.goTime(5000);
                else if (protocol instanceof UcciProtocol ucci) ucci.goTime(5000);
                // go 已发出，标记本次搜索活跃（stop 的旧 bestmove 在此之前到达，不会污染本次）
                thinkDeadlineMs = computeDeadline("FIXED_TIME", 5000);
                watchdogStage = 0;
                searchPending = true;
                searchStartNanos = System.nanoTime();
                return;
            }
            if (protocol instanceof UciProtocol uci) uci.goDepth(depth);
            else if (protocol instanceof UcciProtocol ucci) ucci.goDepth(depth);
        }
        // go 已发出后才标记本次搜索活跃：stop 触发的旧 bestmove 在此前到达（searchPending=false）→ 走缓存分支，
        // 不再被误收为本次结果（修复 stop→begin 竞态导致陈旧着法被执行的缺陷）
        thinkDeadlineMs = computeDeadline(model, value);
        watchdogStage = 0;
        searchPending = true;
        searchStartNanos = System.nanoTime();
    }

    /** 看门狗动态时限（对齐 Qt）：时间模式 max(30s, moveTime*2+5s)（引擎收尾/换层有延迟，
     *  时限过短会误催），深度模式固定 120s */
    private long computeDeadline(String model, long value) {
        return "FIXED_TIME".equals(model)
            ? Math.max(THINK_TIMEOUT_MS, value * 2 + 5000)
            : THINK_DEPTH_DEADLINE_MS;
    }

    private void stopSearch() {
        if (protocol instanceof UciProtocol uci) uci.stop();
        else if (protocol instanceof UcciProtocol ucci) ucci.stop();
    }

    /** 同步打断旧搜索（对齐 C++ stopThinking）：readyok 门控 + 陈旧 bestmove 丢弃 */
    private void interruptSearch() {
        if (process != null) {
            process.stopThinkingGate();
            return;
        }
        stopSearch();
    }

    /** 公共打断入口（对齐 C++ UciEngine::stopThinking）：空闲分析关闭等场景由外部调用，
     *  readyok 门控保证后续命令在引擎空闲后下发，迟到 bestmove 被 stopFlag 丢弃 */
    public void stopThinking() {
        if (!running || protocol == null) return;
        interruptSearch();
    }

    /** 连线空闲分析（对齐 C++ LinkCore::startIdleAnalysis）：无限思考当前局面供分析面板，
     *  不置 searchPending —— 不触发看门狗、不阻塞识别，对手走子时由 analyze 的门控打断 */
    public void analyzeInfinite(Board board, boolean redGo) {
        if (!running || protocol == null) return;
        log.info("analyzeInfinite(boardFen={}, redGo={})", board.toFen(redGo), redGo);
        currentMoves.clear();
        interruptSearch();
        sendPosition(board, redGo);
        if (protocol instanceof UciProtocol uci) uci.goInfinite();
        else if (protocol instanceof UcciProtocol ucci) ucci.goInfinite();
    }

    private void sendPosition(Board board, boolean redGo) {
        if (protocol instanceof UciProtocol uci) {
            uci.position(board, redGo, List.of());
        } else if (protocol instanceof UcciProtocol ucci) {
            ucci.position(board, redGo, List.of());
        }
    }

    public void stopEngine() {
        if (!running) return;
        running = false;
        // quit() 内部已完整关闭进程（EngineProcess.close：发 quit、关流、join、强杀），
        // 此处不再重复 process.close()（幂等但会产生重复 quit/误报日志）
        if (protocol instanceof UciProtocol uci) uci.quit();
        else if (protocol instanceof UcciProtocol ucci) ucci.quit();

        process = null;
        protocol = null;
        eventBus.post(new EngineEvent.EngineStopped(
            engineConfig.get() != null ? engineConfig.get().name() : "unknown"));
    }

    public boolean isRunning() { return running; }

    /** 通知引擎新局开始（连线模式识别到新局时调用，对齐 C++ sendNewGame） */
    public void newGame() {
        if (!running) return;
        if (protocol instanceof UciProtocol uci) uci.newGame();
        else if (protocol instanceof UcciProtocol ucci) ucci.newGame();
        log.info("Engine notified: new game");
    }

    /** 引擎是否正在搜索（供连线检测线程门控）。
     *  ★ 两阶段看门狗（对齐 Qt）：阶段1 超时限 → 裸 stop 催招（bestmove 正常回传不丢步）
     *  + 10s 宽限；宽限后仍无 bestmove → 阶段2 完整复位（防协议卡死冻结连线）。
     *  迟到的 bestmove 在复位后 searchPending=false → 仅缓存不执行，无陈旧着法风险。 */
    public boolean isSearching() {
        if (!running || !searchPending) return false;
        long elapsedMs = (System.nanoTime() - searchStartNanos) / 1_000_000;
        if (elapsedMs <= thinkDeadlineMs) return true;
        if (watchdogStage == 0) {
            watchdogStage = 1;
            log.warn("Engine think deadline ({}ms) hit, stop sent, {}ms grace",
                thinkDeadlineMs, THINK_STAGE2_GRACE_MS);
            stopSearch();   // 裸 stop：引擎回 bestmove 走正常回调，不丢步
            searchStartNanos = System.nanoTime();   // 宽限计时以催招为基准
            thinkDeadlineMs = THINK_STAGE2_GRACE_MS;
            return true;    // 宽限期内仍视为搜索中
        }
        watchdogStage = 0;
        searchPending = false;
        bestMoveLatch.countDown();
        // 阶段2 完整复位（对齐 C++ m_engine->stopThinking()）：同步打断在途搜索并复位协议状态
        interruptSearch();
        log.warn("Engine think timeout (no bestmove after stop grace), search reset");
        return false;
    }

    public Move getLastBestMove() { return lastBestMove; }

    /**
     * 立即出招 - 停止思考并返回当前最佳着法。
     * 只使用本次搜索的 bestmove：等待 latch 到本次结果（最多 1500ms），
     * 超时则放弃，绝不使用上一次搜索的旧着法。
     */
    public void stopThinkingAndMove() {
        if (!running || protocol == null) return;
        stopSearch();
        Move move = awaitCurrentBestMove(1500);
        if (move != null) {
            eventBus.post(new EngineEvent.BestMove(move, null));
        } else {
            log.warn("stopThinkingAndMove: no bestmove within timeout, abandoning");
        }
    }

    private void beginSearch() {
        // searchPending 由 go 命令发出后置位（见 analyze/analyzeWithExcludedMoves）：
        // 提前置位会让 stop 触发的旧 bestmove 落入新搜索窗口被误收为本次结果
        bestMoveLatch = new java.util.concurrent.CountDownLatch(1);
    }

    private Move awaitCurrentBestMove(long timeoutMs) {
        var latch = bestMoveLatch;
        try {
            if (!latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        // latch 触发且 searchPending 已清（回调置 false）才视为"本次"结果
        return searchPending ? null : lastBestMove;
    }

    /**
     * 设置MultiPV（多条主要变例）
     */
    public void setMultiPV(int n) {
        if (!running || protocol == null) return;
        if (protocol instanceof UciProtocol uci) {
            uci.setMultiPV(n);
        } else if (protocol instanceof UcciProtocol ucci) {
            ucci.setMultiPV(n);
        }
    }

    /**
     * 分析变招 - 排除某些着法后分析
     */
    public void analyzeWithExcludedMoves(Board board, boolean redGo, List<Move> excludedMoves) {
        if (!running || protocol == null) return;
        currentMoves.clear();
        interruptSearch();
        beginSearch();
        sleep(50);
        sendPosition(board, redGo);

        var cfg = engineConfig.get();
        if (cfg == null) return;

        String model = cfg.analysisModel() != null ? cfg.analysisModel() : "FIXED_TIME";
        long value = cfg.analysisValue();

        // 生成所有合法着法，排除被排除的着法
        var generator = new com.jiyi.core.rule.MoveGenerator();
        var allLegalMoves = generator.generateLegal(board, redGo);
        var searchMoves = allLegalMoves.stream()
            .filter(m -> !excludedMoves.contains(m))
            .toList();

        if (protocol instanceof UciProtocol uci) {
            if ("FIXED_TIME".equals(model)) {
                uci.goTimeWithSearchMoves(value, searchMoves);
            } else {
                int depth = (int) value;
                uci.goDepthWithSearchMoves(depth, searchMoves);
            }
        } else if (protocol instanceof UcciProtocol ucci) {
            if ("FIXED_TIME".equals(model)) {
                ucci.goTime(value);
            } else {
                int depth = (int) value;
                ucci.goDepth(depth);
            }
        }
        // go 已发出后才标记本次搜索活跃（stop 的旧 bestmove 在此之前到达，不会污染本次）
        thinkDeadlineMs = computeDeadline(model, value);
        watchdogStage = 0;
        searchPending = true;
        searchStartNanos = System.nanoTime();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
