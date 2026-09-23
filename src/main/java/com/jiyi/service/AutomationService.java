package com.jiyi.service;

import com.google.inject.Inject;
import com.jiyi.core.detection.AutoClicker;
import com.jiyi.core.detection.BoardComparator;
import com.jiyi.core.detection.DetectionResult;
import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;
import com.jiyi.core.model.Piece;
import com.jiyi.infra.config.Config;
import com.jiyi.core.rule.MoveValidator;
import com.jiyi.core.rule.MateDetector;
import com.jiyi.core.detection.TemplateMatcher;
import com.jiyi.infra.platform.Platform;
import com.jiyi.infra.platform.WindowsPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Consumer;

public class AutomationService {
    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);

    private final AutoClicker clicker;
    private final BoardComparator comparator;
    private final GameService gameService;
    private final EngineService engineService;
    private final BookService bookService;
    private final Platform platform;
    private final Config config;
    private final MoveValidator validator = new MoveValidator();
    private final MateDetector mateDetector = new MateDetector();
    /** 自动点击续盘（对齐 VinXiangQi AutoClickLoop）：独立线程每 2 秒截图找标定模板，找到即点击 */
    private volatile boolean autoClick;
    /** 将死停止：检测到将死局面时停止自动点击续盘 */
    private volatile boolean stopWhenMate;
    private volatile Thread autoClickThread;

    private volatile boolean enabled;
    private volatile boolean engineIsRed;
    private volatile boolean analysisMode;
    private Board referenceBoard;
    private Rectangle boardRect;
    /** 最近识别帧的翻转状态（bestmove 直接点击时使用，对齐 TCHESS trickAutoClick） */
    private volatile boolean lastFlipped;
    /** 防重复点击：同一着法在冷却期内不重复点击（识别噪声/点击无效时的风暴防御） */
    private volatile String lastClickedMove = "";
    private volatile long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 3000;
    /** 新局/局面重置后的点击抑制期（给识别恢复时间，防止污染局面立即乱点） */
    private volatile long suppressClickUntil = 0;
    /** 引擎走子待执行状态（对齐 C++ m_engineMoveReady：bestmove 到达后由检测线程统一执行） */
    private volatile Move pendingEngineMove = null;
    private volatile boolean pendingEngineMoveFlipped = false;
    private volatile boolean engineMoveReady = false;
    /** UNCLEAR（未配对差异）连续帧计数（对齐 C++ flag4：连续 10 帧 → 新局重开） */
    private int unclearFrames = 0;
    /** 轮到引擎但棋盘无变化的连续帧计数（对齐 Qt kIdleSoftFailFrames=50，约 5s）：
     *  点击未被平台受理（软失败）时局面无变化 → 计满重触发分析自愈 */
    private int idleSoftFailFrames = 0;
    private static final int IDLE_SOFT_FAIL_FRAMES = 50;
    /** 会话代数（对齐 C++ m_linkGeneration）：start/stop 自增，延迟回调捕获代数，不匹配即丢弃 */
    private final java.util.concurrent.atomic.AtomicInteger generation =
        new java.util.concurrent.atomic.AtomicInteger();
    private volatile int sessionGen;
    /** 动画确认状态（对齐 C++ m_awaitingConfirm：车/炮走子动画等待稳定后应用） */
    private boolean awaitingConfirm = false;
    private Board confirmBaseBoard = null;
    private int confirmStableCount = 0;
    private Board pendingOpponentBoard = null;
    private long confirmStartTime = 0;

    // ★ 连线空闲分析（对齐 Qt m_idleAnalysis/m_analysisActive）：引擎空闲时在连线棋盘上
    //   无限思考供分析面板，识别照常跑，对手走子/引擎该走时被 analyze 的门控自动打断
    private volatile boolean idleAnalysis = false;
    private volatile boolean analysisActive = false;

    /** 空闲分析开关（UI"分析"按钮在连线中路由到此）：关时同步打断在途空闲搜索 */
    public void setIdleAnalysis(boolean on) {
        idleAnalysis = on;
        if (!on) {
            analysisActive = false;
            // 关闭时打断在途无限搜索（对齐 Qt setIdleAnalysis(false) → 引擎面板 engineStop 路由）
            engineService.stopThinking();
        }
        log.info("Idle analysis set to {}", on);
    }

    public boolean isIdleAnalysis() { return idleAnalysis; }

    /** 引擎空闲且空闲分析开启 → 拉起无限思考（对齐 Qt scanLoop 单点兜底） */
    private void startIdleAnalysis() {
        Board board = gameService.getCurrentBoard();
        if (board == null) return;
        analysisActive = true;
        engineService.analyzeInfinite(board, engineIsRed);
        log.info("Idle analysis started");
    }

    @Inject
    public AutomationService(Platform platform,
                             GameService gameService, EngineService engineService,
                             BookService bookService, Config config) {
        this.clicker = new AutoClicker(platform);
        this.comparator = new BoardComparator();
        this.gameService = gameService;
        this.engineService = engineService;
        this.bookService = bookService;
        this.platform = platform;
        this.config = config;
    }

    public void start(boolean enginePlaysRed, boolean analysisMode) {
        this.engineIsRed = enginePlaysRed;
        this.analysisMode = analysisMode;
        this.referenceBoard = null;
        this.boardRect = null;
        this.pendingEngineMove = null;
        this.engineMoveReady = false;
        // ★ 会话代数：本次会话捕获当前代数，stop 后 generation 自增使旧回调全部失效
        this.sessionGen = generation.incrementAndGet();
        this.enabled = true;
        log.info("Automation started, engine={}, mode={}, session={}", enginePlaysRed ? "R" : "B",
            analysisMode ? "analysis" : "play", sessionGen);
    }

    public void stop() {
        this.enabled = false;
        // ★ 自动续盘线程常驻（对齐 Qt AutoRematch setEnabled 语义）：
        //   随配置开关启停，不随连线 stop 而关闭——连线断开后再来一局弹窗仍可自动点击
        this.pendingEngineMove = null;
        this.engineMoveReady = false;
        // 空闲分析随连线停止关闭（对齐 Qt LinkCore::stop 清 m_idleAnalysis）
        boolean idleWasActive = this.analysisActive;
        this.idleAnalysis = false;
        this.analysisActive = false;
        // 复用用户引擎时引擎不随连线停止，在途无限搜索必须显式打断
        if (idleWasActive) engineService.stopThinking();
        this.generation.incrementAndGet();   // 使旧会话的延迟回调失效
        log.info("Automation stopped");
    }

    public boolean isEnabled() { return enabled; }

    /** 引擎是否正在思考（检测线程据此跳过扫描） */
    public boolean isEngineThinking() { return engineService.isSearching(); }

    public void reset() {
        this.referenceBoard = null;
        this.boardRect = null;
        log.info("Automation reset - awaiting new board capture");
    }

    public void syncBoard(Board board) {
        if (!enabled) return;
        this.referenceBoard = board;
        log.info("Reference board synchronized");
    }

    public void setClickDelay(int delayMs) {
        clicker.setClickDelay(delayMs);
        log.debug("Click delay set to {}ms", delayMs);
    }

    public void setBackMode(boolean backMode) {
        clicker.setBackMode(backMode);
        log.info("Back mode set to {}", backMode);
    }

    public void setMoveDelay(int delayMs) {
        clicker.setMoveDelay(delayMs);
        log.debug("Move delay set to {}ms", delayMs);
    }

    public void setEngineColor(boolean enginePlaysRed) {
        this.engineIsRed = enginePlaysRed;
        log.info("Engine color changed to {}", enginePlaysRed ? "RED" : "BLACK");
    }

    public void setAnalysisMode(boolean analysisMode) {
        this.analysisMode = analysisMode;
        log.info("Analysis mode: {}", analysisMode ? "ON" : "OFF");
    }

    /** 自动点击续盘开关（设置保存时调用；线程懒启动，退出自动结束） */
    public void setAutoClick(boolean on) {
        this.autoClick = on;
        if (on && (autoClickThread == null || !autoClickThread.isAlive())) {
            autoClickThread = Thread.ofPlatform().name("autoclick").daemon().start(this::autoClickLoop);
        }
        log.info("Auto click set to {}", on);
    }

    public void setStopWhenMate(boolean on) {
        this.stopWhenMate = on;
        log.info("Stop-when-mate set to {}", on);
    }

    public boolean isAutoClick() { return autoClick; }

    /** 自动点击续盘循环（对齐 Qt AutoRematch::loop + VinXiangQi AutoClickLoop）：
     *  每 1 秒截取目标窗口客户区 → 遍历 autoclick 目录模板图找"再来一局"按钮
     *  → 任一命中即点击，单轮可连点多个模板（再来一局→确定）；
     *  点击后 3s 冷却期整轮跳过匹配（防按钮动画期间重复点击）。
     *  线程常驻：只随配置开关启停，连线 stop 不影响。
     *  状态日志 30s 节流，便于确认循环在工作（静默空转无法排查）。
     *  （2026-09-14 用户要求由 Qt 原版 2s 提速至 1s：按钮出现后 ≤1s 即点击）
     */
    private static final long AUTOCLICK_COOLDOWN_MS = 3000;
    private static final long AUTOCLICK_SCAN_INTERVAL_MS = 1000;

    private void autoClickLoop() {
        File dir = new File(config.link().autoclickDir());
        long[] lastStatusLog = {0};
        long cooldownUntil = 0;
        while (autoClick) {
            try {
                sleep(AUTOCLICK_SCAN_INTERVAL_MS);
                if (!autoClick) break;
                // 点击后冷却期：整轮跳过匹配（对齐 Qt m_clickCooldownActive）
                if (System.currentTimeMillis() < cooldownUntil) continue;
                if (!dir.isDirectory()) {
                    logStatusThrottled(lastStatusLog,
                        "Auto click: template dir missing: {}", dir.getAbsolutePath());
                    continue;
                }
                File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
                if (files == null || files.length == 0) {
                    logStatusThrottled(lastStatusLog,
                        "Auto click: no templates in {}", dir.getAbsolutePath());
                    continue;
                }

                BufferedImage shot = captureClientArea();
                if (shot == null) {
                    logStatusThrottled(lastStatusLog,
                        "Auto click: client-area capture failed (hwnd invalid?)");
                    continue;
                }

                boolean found = false;
                for (File f : files) {
                    if (!autoClick) break;
                    try {
                        BufferedImage model = javax.imageio.ImageIO.read(f);
                        if (model == null) continue;
                        Point pos = TemplateMatcher.findFromTop(shot, model);
                        if (pos == null) continue;
                        found = true;
                        if (stopWhenMate && isMateEnded()) {
                            log.info("Auto click skipped: mate detected (stopWhenMate) - {}", f.getName());
                            continue;
                        }
                        Point center = new Point(pos.x + model.getWidth() / 2, pos.y + model.getHeight() / 2);
                        log.info("Auto click template {} at ({},{})", f.getName(), center.x, center.y);
                        platform.mouseClick(null, center, Platform.ClickMode.FRONT);
                        // 命中即进入冷却：单轮内继续扫其余模板（再来一局→确定连点），
                        // 点击失败也冷却，防对不可点击区域风暴式重试（对齐 Qt 行为）
                        cooldownUntil = System.currentTimeMillis() + AUTOCLICK_COOLDOWN_MS;
                    } catch (Exception e) {
                        log.warn("Auto click template {} failed: {}", f.getName(), e.getMessage());
                    }
                }
                if (!found) {
                    logStatusThrottled(lastStatusLog,
                        "Auto click: scanned {} template(s), none found (no 'again' button on screen?)", files.length);
                }
            } catch (Exception e) {
                log.error("Auto click loop error", e);
            }
        }
        log.info("Auto click loop exited");
    }

    /** 状态日志节流：同一状态每 30s 最多打一次（lastLog[0] 为上次打印时刻） */
    private void logStatusThrottled(long[] lastLog, String msg, Object... args) {
        long now = System.currentTimeMillis();
        if (now - lastLog[0] > 30000) {
            lastLog[0] = now;
            log.info(msg, args);
        }
    }

    /** 截取目标窗口客户区（未选窗口返回 null）；BitBlt 物理 1:1（Robot 逻辑空间在 DPI 缩放下会错位） */
    private BufferedImage captureClientArea() {
        try {
            if (platform instanceof WindowsPlatform wp) {
                long hwnd = wp.getCurrentHwnd();
                if (hwnd == 0) return null;
                var rect = wp.getClientRectScreen(hwnd);
                if (rect != null && rect.width > 0 && rect.height > 0) {
                    return wp.captureScreen(rect);
                }
            }
        } catch (Exception e) {
            log.debug("Auto click capture failed: {}", e.getMessage());
        }
        return null;
    }

    /** 当前内部局面是否已终局（任一方无合法着法） */
    private boolean isMateEnded() {
        Board b = gameService.getCurrentBoard();
        return b != null && (mateDetector.isNoLegalMove(b, true) || mateDetector.isNoLegalMove(b, false));
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public boolean isAnalysisMode() { return analysisMode; }
    public boolean isEngineRed() { return engineIsRed; }
    public Board getReferenceBoard() { return referenceBoard; }
    public Rectangle getBoardRect() { return boardRect; }
    public boolean getLastFlipped() { return lastFlipped; }

    /** 连线模式实时判定：当前是否轮到引擎走（用首帧确定的 engineIsRed，对齐 C++ engineToMove） */
    public boolean isEngineTurnNow() {
        return enabled && gameService.isRedToGo() == engineIsRed;
    }

    // Called from DetectionService (synchronous, on detection thread)
    public synchronized void onBoardDetected(DetectionResult detResult, Board board, boolean flipped) {
        if (!enabled) {
            log.debug("Automation disabled, ignoring board");
            return;
        }
        // ★ 会话代数校验：停止后残留的检测帧直接丢弃（防串线）
        if (generation.get() != sessionGen) return;

        // ★ 空闲分析兜底（对齐 Qt scanLoop 单点）：勾选分析且引擎空闲 → 拉起无限思考。
        //   放在最前：无论局面处于什么状态，引擎一空闲即可（重新）开始分析；
        //   对手走子/引擎该走时由 analyze 的 readyok 门控自动打断
        if (idleAnalysis && !analysisActive && !engineMoveReady && pendingEngineMove == null
                && !engineService.isSearching() && engineService.isRunning()) {
            startIdleAnalysis();
        }

        try {
            if (detResult.boardRect() != null) {
                this.boardRect = detResult.boardRect();
                clicker.setBoardRect(detResult.boardRect());
            }
            lastFlipped = flipped;

            // 动画确认状态（对齐 C++ m_awaitingConfirm）：车/炮走子动画逐帧确认稳定
            if (awaitingConfirm) {
                // ★ 确认超时兜底（对齐 C++ m_confirmTimer 1s）：动画不断/抖动时强制继续，防死循环
                if (System.currentTimeMillis() - confirmStartTime > 1000) {
                    awaitingConfirm = false;
                    log.warn("Animation confirm timeout (1s), applying opponent move");
                    applyOpponentMove(pendingOpponentBoard);
                } else if (board.equals(confirmBaseBoard)) {
                    confirmStableCount++;
                    if (confirmStableCount >= config.link().stableConfirmCount()) {
                        awaitingConfirm = false;
                        log.info("Animation confirm done ({} frames), applying opponent move", confirmStableCount);
                        applyOpponentMove(pendingOpponentBoard);
                    }
                } else {
                    confirmStableCount = 0;
                    confirmBaseBoard = board;
                    log.debug("Confirm: board changed, re-anchoring");
                }
                return;  // 确认期间不处理其他
            }

            // 引擎局面基准 = GameService 当前局面（executeMove 推进），首次先同步参考棋盘
            Board engineBoard = gameService.getCurrentBoard();
            log.debug("Detected board fen={} | engine board fen={} | engineIsRed={} | analysis={}",
                board.toFen(true), engineBoard.toFen(true), engineIsRed, analysisMode);

            if (referenceBoard == null) {
                referenceBoard = board;
                // ★ 对齐 C++ tryDetectBoardCrop auto-detect（LinkCore.cpp:500-510）：
                //   m_isReverse ? Side::Black : Side::Red —— 不翻转（红视角）→ 引擎红；
                //   翻转（黑视角）→ 引擎黑。flipped 与 m_isReverse 同义。
                if ("AUTO".equals(config.link().engineColor())) {
                    engineIsRed = !flipped;
                }
                // ★ 对齐 C++ 初始同步 blackFirst = m_isReverse && !isInitialPos
                boolean isInitialPos = board.equals(Board.STANDARD);
                boolean blackFirst = flipped && !isInitialPos;
                // 行棋方写入引擎局面（FEN 第 2 段 = 行棋方：blackFirst → 黑走 "b"）
                // ★ syncBoard：连线同步不清历史/不发 GameStarted（loadFen 会清空棋谱记录并重置 UI）
                gameService.syncBoard(board, !blackFirst);
                // ★ 对齐 C++ engineToMove：引擎色 == 当前行棋方才触发分析（开局引擎先手自动走）
                boolean engineToMove = engineIsRed == !blackFirst;
                if (engineToMove) {
                    log.info("Initial sync: engine to move (engineIsRed={}, blackFirst={})",
                        engineIsRed, blackFirst);
                    triggerEngineOrBook(board);
                } else {
                    log.info("Initial board captured, engine={} blackFirst={} engineToMove={} (human to move)",
                        engineIsRed ? "RED" : "BLACK", blackFirst, engineToMove);
                }
                return;
            }

            // 用引擎局面做基准，而非上一帧外部棋盘（B44）
            var diff = comparator.compare(board, engineBoard, engineIsRed, analysisMode);
            if (diff == null) {
                log.debug("No board difference detected");
                // ★ 停滞守卫（对齐 Qt kIdleSoftFailFrames）：轮到引擎、引擎空闲、无待执行走法，
                //   但棋盘连续 N 帧无变化 → 点击很可能未被平台受理（软失败）。重新触发引擎分析，
                //   走 bestmove→executePendingEngineMove 重发点击路径自愈
                boolean engineTurn = isEngineTurnNow();
                if (engineTurn && !analysisMode && !idleAnalysis && engineService.isRunning()
                        && !engineService.isSearching()) {
                    if (++idleSoftFailFrames >= IDLE_SOFT_FAIL_FRAMES) {
                        idleSoftFailFrames = 0;
                        log.warn("Engine turn but board idle {} frames, re-triggering engine",
                            IDLE_SOFT_FAIL_FRAMES);
                        engineService.analyze(engineBoard, engineIsRed);
                    }
                } else {
                    idleSoftFailFrames = 0;
                }
                return;
            }
            idleSoftFailFrames = 0;
            log.info("Compare result: action={}, diff={} (linkFen={})",
                diff.action(), diff.diff() == null ? "-" : diff.diff().toMove().toUci(), board.toFen(true));

            switch (diff.action()) {
                case OPPONENT_MOVED:
                    unclearFrames = 0;
                    // 车/炮走子动画确认（对齐 C++ needConfirm 只对 flag1）
                    if (comparator.needConfirm(board, engineBoard, diff)) {
                        awaitingConfirm = true;
                        confirmStableCount = 0;
                        confirmBaseBoard = board;
                        pendingOpponentBoard = board;
                        confirmStartTime = System.currentTimeMillis();
                        log.info("Opponent move needs animation confirm: {}",
                            diff.diff() == null ? "-" : diff.diff().toMove().toUci());
                        break;
                    }
                    handleOpponentMove(board, diff);
                    break;

                case ENGINE_MOVED:
                    unclearFrames = 0;
                    // 对齐 C++ flag2 己方走子：只同步（点击由 bestmove executePendingEngineMove 负责）
                    referenceBoard = board;
                    log.debug("ENGINE_MOVED (self move), syncing only: {}",
                        diff.diff() == null ? "-" : diff.diff().toMove().toUci());
                    break;

                case NEW_GAME:
                    unclearFrames = 0;
                    handleNewGame(board);
                    break;

                case UNCLEAR:
                    // 对齐 C++ flag4：未配对差异计数，连续 10 帧 → 新局重开（重新同步基准）
                    unclearFrames++;
                    if (unclearFrames > 9) {
                        log.warn("Unmatched diff persisted {} frames, resetting baseline", unclearFrames);
                        unclearFrames = 0;
                        referenceBoard = null;
                        gameService.syncBoard(board, engineIsRed);
                        suppressClickUntil = System.currentTimeMillis() + 3000;
                    } else {
                        log.debug("Unmatched board change, skipping frame ({}/10)", unclearFrames);
                    }
                    break;
            }
        } catch (Exception e) {
            log.error("Error processing board detection", e);
        }
    }

    private void handleOpponentMove(Board board, BoardComparator.ComparisonResult diff) {
        log.info("OPPONENT_MOVED: {}", diff.diff().toMove().toUci());
        // 对齐 C++：引擎执色由首帧视角 auto-detect 一次定死（无颜色自校验，
        // 避免 engineIsRed 中途翻转与 MainController.engineSide 漂移导致 bestmove 门禁错乱）
        applyOpponentMove(board);
    }

    /** 对手走子：同步引擎局面 + 触发引擎分析（对齐 C++ linkerMove + triggerEngineAnalysis） */
    private void applyOpponentMove(Board board) {
        referenceBoard = board;
        // 同步引擎局面到外部最新局面（走棋方=引擎色），使 bestmove 回来时 canGo 基准正确
        // ★ syncBoard：不清历史、不发 GameStarted（loadFen 每步清空棋谱记录并重置 UI）
        log.info("Syncing engine board via syncBoard: {} engineIsRed={}", board.toFen(engineIsRed), engineIsRed);
        gameService.syncBoard(board, engineIsRed);
        // 当前轮确为引擎时才触发（开局库优先，未命中走引擎分析）
        if (engineService.isRunning() && gameService.isRedToGo() == engineIsRed) {
            log.info("Triggering engine move (engineIsRed={})", engineIsRed);
            triggerEngineOrBook(board);
        } else {
            log.warn("Skipping engine analysis: engineRunning={}, redToGo={}, engineIsRed={}",
                engineService.isRunning(), gameService.isRedToGo(), engineIsRed);
        }
    }

    /** 引擎该走：开局库命中 → 出招延迟后排队库招；未命中 → 引擎分析
     *  （对齐 Qt triggerEngineAnalysis 开局库优先钩子） */
    private void triggerEngineOrBook(Board board) {
        if (!engineService.isRunning()) return;
        // 走子搜索优先于空闲分析（对齐 Qt：triggerEngineAnalysis 清 m_analysisActive，
        // 由 analyze 的门控打断在途无限搜索；结束后兜底逻辑自动重新拉起）
        analysisActive = false;
        if (bookMove(board)) return;
        engineService.analyze(board, engineIsRed);
    }

    /** 连线开局库查招：命中即同步延迟后经 supplyEngineMove 排队（内含复验），
     *  延迟期间识别暂停与引擎思考一致；未命中/异常/非法回退引擎分析。
     *  @return true=库招已排队（或延迟中），不再触发引擎 */
    private boolean bookMove(Board board) {
        if (!config.book().bookSwitch()) return false;
        try {
            String uci = bookService.queryBestMove(board, engineIsRed,
                gameService.getMoveHistory().size());
            if (uci == null) return false;
            Move m = Move.fromUci(uci);
            // 库招合法性校验（canGo 含送将/应将，口径与走法复验一致）
            if (!validator.canGo(board, m, engineIsRed)) {
                log.warn("Book move illegal, fallback to engine: {}", uci);
                return false;
            }
            // ★ 库招同样应用出招延迟（对齐 Qt 方案12，对齐 VinXiangQi MinTimeUsingOpenbook）：
            //   库招秒回与引擎真算招的响应时间差异会成为自动化行为特征。
            //   公式与 EngineService.analyze 一致：[start,end) 随机，end<=start 固定 start
            int dStart = config.engine().delayStartMs();
            int dEnd = config.engine().delayEndMs();
            if (dStart > 0) {
                int delay = (dEnd > dStart) ? dStart + (int) (Math.random() * (dEnd - dStart)) : dStart;
                log.info("Book move: {} delay={}ms", uci, delay);
                sleep(delay);
            } else {
                log.info("Book move: {}", uci);
            }
            supplyEngineMove(m);
            return true;
        } catch (Exception e) {
            log.warn("Book query failed, falling back to engine", e);
            return false;
        }
    }

    /** 只点击外部平台执行引擎着法（局面已由 bestmove 处理器更新） */
    public void clickEngineMoveExternal(Move move, boolean flipped) {
        try {
            // 观战模式：只同步不点击
            if (analysisMode) {
                log.debug("Analysis mode - skipping external click: {}", move.toUci());
                return;
            }
            long now = System.currentTimeMillis();
            // 防重复点击：同一着法在冷却期内不重复（点击无效/识别噪声时避免风暴式重复点）
            if (move.toUci().equals(lastClickedMove) && now - lastClickTime < CLICK_COOLDOWN_MS) {
                log.warn("Duplicate engine click suppressed: {} ({}ms ago)", move.toUci(), now - lastClickTime);
                return;
            }
            // 新局/局面重置后的点击抑制期
            if (now < suppressClickUntil) {
                log.warn("Click suppressed until {} (new game cooldown): {}", suppressClickUntil, move.toUci());
                return;
            }
            lastClickedMove = move.toUci();
            lastClickTime = now;
            log.info("Clicking external platform: {} (flipped={}, boardRect={})",
                move.toUci(), flipped, clicker.getBoardRect());
            clicker.click(move, flipped);
            log.info("Clicked external platform move: {}", move.toUci());
        } catch (Exception e) {
            log.error("Error clicking external move: {}", move.toUci(), e);
        }
    }

    /**
     * 连线模式下 bestmove 处理器调用：登记引擎着法，交给检测线程统一执行
     * （对齐 C++ onEngineBestMove → m_pendingEngineMove + m_engineMoveReady）
     */
    public void supplyEngineMove(Move move) {
        if (!enabled) {
            log.debug("Automation disabled, ignoring engine move: {}", move.toUci());
            return;
        }
        // ★ 会话代数校验：上次会话残留的 bestmove 丢弃
        if (generation.get() != sessionGen) return;
        // ★ 预校验：引擎着法在当前引擎局面必须合法（对齐 C++ onEngineBestMove canMove 预检，
        //   非法/过期走法直接丢弃，避免先点后验污染外部棋盘）
        if (!validator.canGo(gameService.getCurrentBoard(), move, engineIsRed)) {
            log.warn("Bestmove invalid on current engine board, dropping: {}", move.toUci());
            return;
        }
        long now = System.currentTimeMillis();
        // 防重复：同一着法在冷却期内不重复排队
        if (move.toUci().equals(lastClickedMove) && now - lastClickTime < CLICK_COOLDOWN_MS) {
            log.warn("Duplicate engine move suppressed: {} ({}ms ago)", move.toUci(), now - lastClickTime);
            return;
        }
        pendingEngineMove = move;
        pendingEngineMoveFlipped = lastFlipped;
        engineMoveReady = true;
        log.info("Engine move queued for execution: {} (flipped={})", move.toUci(), lastFlipped);
    }

    /**
     * 检测线程每个 tick 最先调用：执行待执行的引擎着法（对齐 C++ executeEngineMove）。
     * 点击成功后推进引擎局面；失败则丢弃（与 C++ 一致，避免死循环）。
     * @return true 表示已消费（本 tick 不再进行识别）
     */
    public boolean executePendingEngineMove() {
        Move m = pendingEngineMove;
        if (m == null || !engineMoveReady) return false;
        // ★ 会话代数校验：停止后残留的待执行着法丢弃
        if (generation.get() != sessionGen) {
            pendingEngineMove = null;
            engineMoveReady = false;
            return true;
        }
        try {
            boolean flipped = pendingEngineMoveFlipped;
            log.info("Executing pending engine move: {} (flipped={})", m.toUci(), flipped);
            if (analysisMode) {
                // 观战模式：只推进局面不点击
                boolean ok = gameService.executeMove(m);
                log.info("Watch mode: engine board updated without click: {} ok={}", m.toUci(), ok);
            } else {
                if (clicker.getBoardRect() == null) {
                    log.warn("Board rect not ready, dropping pending move: {}", m.toUci());
                    return true;
                }
                // ★ 点击失败不推进局面（对齐 Qt clickTwoPoint 失败→不 makeMove→"回退等待"）：
                //   局面保持引擎行棋 → 空闲停滞守卫（IDLE_SOFT_FAIL_FRAMES）重触发分析，
                //   走 bestmove→executePendingEngineMove 路径重发点击自愈
                if (!clicker.click(m, flipped)) {
                    log.warn("Click not delivered for {}, board not advanced (idle guard will retry)", m.toUci());
                    return true;
                }
                lastClickedMove = m.toUci();
                lastClickTime = System.currentTimeMillis();
                // 点击成功后推进引擎局面（失败则局面不动，对齐 C++ makeMove 时机）
                boolean ok = gameService.executeMove(m);
                log.info("Engine move executed: {} clicked, gameService={}", m.toUci(), ok);
            }
        } catch (Exception e) {
            log.error("Failed to execute pending engine move: {}", m.toUci(), e);
        } finally {
            pendingEngineMove = null;
            engineMoveReady = false;
        }
        return true;
    }

    /** 立即出招等场景：点击外部 + 同步本地局面 */
    public void clickEngineMove(Move move, boolean flipped) {
        try {
            clicker.click(move, flipped);
            boolean executed = gameService.executeMove(move);
            if (executed) {
                log.info("Auto-executed move: {}", move.toUci());
            } else {
                log.warn("Failed to execute move in game service: {}", move.toUci());
            }
        } catch (Exception e) {
            log.error("Error executing move: {}", move.toUci(), e);
        }
    }

    private void handleNewGame(Board board) {
        log.info("NEW_GAME/large-diff detected: {}", board.toFen(true));
        // ★ 对齐 C++ LinkCore.cpp:473：棋盘剧变 = 任何待执行走法已过时，先丢弃（防陈旧着法点进新局）
        pendingEngineMove = null;
        engineMoveReady = false;
        awaitingConfirm = false;
        referenceBoard = board;
        gameService.loadFen(board.toFen(true));
        // ★ 对齐 C++：区分真新局（回到初始局面）vs 续盘/异常大差异（只同步不重置引擎）
        if (board.equals(Board.STANDARD)) {
            engineService.newGame();
            log.info("Initial position detected: engine reset (new game)");
            // ★ 对齐 C++ m_boardSynced=false：重置初始同步标志 → 下一帧重跑初始同步块，
            //   由其判定行棋方并触发引擎（引擎执红=开局先手，立即出招）。
            //   2026-09-14 实测缺陷：移植时未重置此标志，自动续盘后引擎闲置 ~60s 不走第一步，
            //   须等对手走子 + UNCLEAR×10 才自愈。
            referenceBoard = null;
        } else {
            log.info("Non-initial position (resume): engine board synced only");
        }
        // 新局后 3 秒内抑制点击：给识别/引擎状态恢复时间，防止污染局面立即乱点
        suppressClickUntil = System.currentTimeMillis() + 3000;
    }

    public void manualClick(int fromRow, int fromCol, int toRow, int toCol, boolean flipped) {
        if (!enabled) {
            log.warn("Automation not enabled, cannot execute manual click");
            return;
        }
        if (boardRect == null) {
            log.warn("Board rectangle not set, cannot execute manual click");
            return;
        }
        var move = new Move(fromRow, fromCol, toRow, toCol);
        clicker.click(move, flipped);
        log.info("Manual click executed: {}", move.toUci());
    }

}
