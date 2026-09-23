package com.jiyi.service;

import com.google.inject.Inject;
import com.jiyi.core.detection.BoardMatcher;
import com.jiyi.core.detection.DetectionResult;
import com.jiyi.core.detection.YoloDetector;
import com.jiyi.core.model.Board;
import com.jiyi.infra.config.Config;
import com.jiyi.infra.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;

public class DetectionService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    public enum CaptureMode {
        PRINT_WINDOW,  // 使用PrintWindow API (后台，可被遮挡；GPU渲染窗口可能黑图)
        ROBOT          // 使用Robot (前台，屏幕截图；底层同为屏幕DC BitBlt，能抓到GPU渲染内容)
    }

    private final Platform platform;
    private final AutomationService automation;
    private volatile YoloDetector detector;
    private final BoardMatcher matcher;
    private final Config config;

    private volatile boolean running;
    private volatile Thread detectionThread;
    private long targetWindowHwnd;
    private volatile CaptureMode captureMode = CaptureMode.PRINT_WINDOW;
    /** 配置的首选截图方式（AUTO 时从 PRINT_WINDOW 起，降级链 PRINT_WINDOW→ROBOT） */
    private String configuredMethod = "AUTO";
    private int failureCount = 0;
    private static final int MAX_FAILURES_BEFORE_FALLBACK = 5;

    private volatile Board lastProcessedBoard = null;

    /** 稳定帧等待状态（对齐 C++ waitForStableBoard）：上次稳定的识别局面 + 连续相同计数 */
    private Board lastStableBoard = null;
    private int stableCount = 0;
    private int stableConfirmCount = 2;
    /** 调试截图保存：上次保存时间（限频，约 3 秒一张） */
    private long lastScreenshotSaveTime = 0;

    @Inject
    public DetectionService(Platform platform, AutomationService automation, Config config) {
        this.platform = platform;
        this.automation = automation;
        this.config = config;
        this.matcher = new BoardMatcher();

        YoloDetector d;
        try {
            d = new YoloDetector(config.link().modelPath());
        } catch (Exception e) {
            log.warn("YOLO not loaded from {}: {}", config.link().modelPath(), e.getMessage());
            d = null;
        }
        this.detector = d;
    }

    /** 重新加载检测模型（连线面板入口）。失败时保留旧模型。 */
    public synchronized boolean reloadModel(String path) {
        try {
            YoloDetector d = new YoloDetector(path);
            YoloDetector old = this.detector;
            this.detector = d;
            if (old != null) old.close();
            log.info("Model reloaded from {}", path);
            return true;
        } catch (Exception e) {
            log.error("Failed to reload model from {}: {}", path, e.getMessage());
            return false;
        }
    }

    public boolean start(long hwnd) {
        if (detector == null) return false;
        this.targetWindowHwnd = hwnd;
        this.running = true;
        // 首选截图方式：按配置；AUTO 从 PrintWindow 起（失败黑图自动降级 Robot）。
        // BITBLT 已移除（与 Robot 同为屏幕 DC 抓取，效果等价），旧配置值映射为 ROBOT
        this.configuredMethod = config.link().captureMethod();
        this.captureMode = switch (configuredMethod) {
            case "ROBOT" -> CaptureMode.ROBOT;
            case "BITBLT" -> CaptureMode.ROBOT;  // 旧值兼容：BITBLT 已删除，等价 Robot
            default -> CaptureMode.PRINT_WINDOW;
        };
        // 天天象棋等已知 GPU 渲染窗口：PrintWindow 必黑屏，强制 Robot（对齐 C++ isSkipPrintWindow）
        if (platform instanceof com.jiyi.infra.platform.WindowsPlatform wp
            && wp.isKnownGpuRenderedWindow(hwnd)) {
            this.captureMode = CaptureMode.ROBOT;
            log.info("Target window is GPU-rendered (QQChess): forcing Robot capture");
        }
        this.failureCount = 0;
        this.lastProcessedBoard = null;
        this.lastStableBoard = null;
        this.stableCount = 0;
        this.stableConfirmCount = config.link().stableConfirmCount();
        this.detectionThread = Thread.ofPlatform().name("detection").start(this::loop);
        log.info("Detection started on hwnd=0x{} with mode={} (configured={})",
            Long.toHexString(hwnd), captureMode, configuredMethod);
        return true;
    }

    public void stop() {
        running = false;
        if (detectionThread != null) {
            detectionThread.interrupt();
            detectionThread = null;
        }
    }

    private void loop() {
        while (running) {
            try {
                // 引擎走子优先执行（对齐 C++ scanLoop：m_engineMoveReady → executeEngineMove 最先处理）
                if (automation.executePendingEngineMove()) {
                    sleep(config.link().scanIntervalMs());
                    continue;
                }

                var screenshot = capture();
                if (screenshot == null) {
                    handleCaptureFailure();
                    logFailureThrottled("Capture failed (mode={}, failureCount={})", captureMode, failureCount);
                    sleep(500);
                    continue;
                }

                // PrintWindow 对 GPU 渲染窗口可能返回黑图（内容未写入 GDI DC）：检测空白并切换模式
                if (isBlankImage(screenshot)) {
                    handleCaptureFailure();
                    logFailureThrottled("Capture appears blank/black (mode={}, failureCount={})", captureMode, failureCount);
                    sleep(300);
                    continue;
                }

                // 成功捕获，重置失败计数
                failureCount = 0;
                log.debug("Captured {}x{} mode={}", screenshot.getWidth(), screenshot.getHeight(),
                    captureMode);

                // 调试截图：开启时约每 3 秒保存一帧到桌面（定位识别问题时开启）
                if (config.link().saveScreenshot()) {
                    long now = System.currentTimeMillis();
                    if (now - lastScreenshotSaveTime > 3000) {
                        lastScreenshotSaveTime = now;
                        try {
                            var dir = new java.io.File(
                                System.getProperty("user.home") + "/Desktop/极弈日志/screenshots");
                            dir.mkdirs();
                            var f = new java.io.File(dir, "capture-" + java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("HHmmss-SSS")) + ".png");
                            javax.imageio.ImageIO.write(screenshot, "png", f);
                            log.info("Debug screenshot saved: {}", f.getAbsolutePath());
                        } catch (Exception e) {
                            log.debug("Failed to save debug screenshot: {}", e.getMessage());
                        }
                    }
                }

                if (detector == null) {
                    log.warn("Detector not available");
                    sleep(1000);
                    continue;
                }

                var result = detector.detect(screenshot);
                // ★ 坐标系统一为物理客户区坐标（对齐 C++ clickMap：BitBlt 物理图 → 检测框物理坐标，
                //   点击时由 WindowsPlatform 加物理客户区屏幕原点，不再做任何逻辑转换——
                //   原 physical->logical 转换导致 FRONT 点击 offset(物理)+from(逻辑) 错位点偏，外部棋子不动）
                if (result.boardRect() == null) {
                    // 棋盘未检测到：跳过本帧重试（对齐 C++ findBoardPosition 重试）
                    log.warn("YOLO board not detected (image {}x{}), retrying",
                        screenshot.getWidth(), screenshot.getHeight());
                    sleep(200);
                    continue;
                }

                var match = matcher.match(result);
                if (match == null) {
                    // 主候选（面积最大）校验失败：遍历其余候选逐个尝试
                    // （YOLO 可能误检窗口顶部小棋盘图/横幅为棋盘）
                    for (var cand : result.boardCandidates()) {
                        if (cand.equals(result.boardRect())) continue;
                        var m = matcher.match(new DetectionResult(cand, result.pieces()));
                        if (m != null) {
                            match = m;
                            result = new DetectionResult(cand, result.pieces());
                            log.info("Board matched with fallback candidate {}", cand);
                            break;
                        }
                    }
                    if (match == null) {
                        log.warn("Board match failed (candidates={} {}, pieces={})",
                            result.boardCandidates().size(), result.boardCandidates(), result.pieces().size());
                        sleep(200);
                        continue;
                    }
                }

                Board currentBoard = match.board();
                boolean flipped = match.flipped();  // 翻转判定来自本次匹配结果（不再用 setFlipped 手动值）
                log.debug("Matched board flipped={} fen={}", flipped, currentBoard.toFen(true));

                // 动画确认逻辑：如果开启了animation，检测到变化后等待500ms再截图确认
                if (config.link().animation() && lastProcessedBoard != null
                    && !currentBoard.equals(lastProcessedBoard)) {
                    log.debug("Animation detected, waiting 500ms for confirmation");
                    sleep(500);
                    // 再次截图确认动画已结束
                    var confirmScreenshot = capture();
                    if (confirmScreenshot != null) {
                        var confirmResult = detector.detect(confirmScreenshot);
                        if (confirmResult.boardRect() != null) {
                            var confirmMatch = matcher.match(confirmResult);
                            if (confirmMatch != null) {
                                currentBoard = confirmMatch.board();
                                flipped = confirmMatch.flipped();
                                result = new DetectionResult(confirmResult.boardRect(), confirmResult.pieces());
                            }
                        }
                    }
                }

                lastProcessedBoard = currentBoard;

                // 引擎思考时跳过扫描（门控），避免读到中间局面
                if (automation.isEngineThinking()) {
                    log.debug("Engine thinking, skipping scan");
                    sleep(100);
                    continue;
                }

                // ★ 稳定帧等待（对齐 C++ waitForStableBoard）：连续 N 帧识别相同才派发。
                //   动画中间态（棋子悬空/双位置/吃子特效）每帧识别结果不同 → 永远达不到稳定 → 被跳过。
                if (currentBoard.equals(lastStableBoard)) {
                    stableCount++;
                    if (stableCount < stableConfirmCount) {
                        log.debug("Board stable {}/{}, waiting", stableCount, stableConfirmCount);
                        sleep(config.link().scanIntervalMs());
                        continue;
                    }
                    stableCount = 0;
                } else {
                    stableCount = 0;
                    lastStableBoard = currentBoard;
                    log.debug("Board changed, waiting for stability (confirm={})", stableConfirmCount);
                    sleep(config.link().scanIntervalMs());
                    continue;
                }

                // Call AutomationService synchronously (on detection thread)
                log.debug("Dispatching board to automation (flipped={})", flipped);
                automation.onBoardDetected(result, currentBoard, flipped);

                sleep(config.link().scanIntervalMs());
            } catch (Exception e) {
                log.warn("Detection error", e);
                sleep(1000);
            }
        }
    }

    private BufferedImage capture() {
        try {
            if (targetWindowHwnd == 0) {
                return platform.captureScreen(null);   // 整屏（物理像素）
            }

            BufferedImage img = null;
            // 对齐 C++ tryDetectBoardCrop：单次全图检测，不裁剪棋盘。
            // （棋盘裁剪复用会导致 YOLO 棋盘框漂移 → 列映射偏移 → 棋子错位）
            switch (captureMode) {
                case PRINT_WINDOW -> img = platform.captureWindow(targetWindowHwnd, null);
                case ROBOT -> img = captureScreenArea();
            }
            return img;
        } catch (Exception e) {
            log.debug("Capture failed: {}", e.getMessage());
            return null;
        }
    }

    private BufferedImage captureScreenArea() {
        try {
            // 截客户区（ClientToScreen 原点 + GetClientRect 尺寸）：与其他截图方式坐标基准统一。
            // ★ 必须走 BitBlt（captureScreen 物理 1:1）：Robot 坐标是 AWT 逻辑空间，DPI 缩放
            //   下把物理坐标当逻辑用会整体偏移 scale 倍（125% 实测根因：棋盘残片→识别全错）
            if (platform instanceof com.jiyi.infra.platform.WindowsPlatform wp) {
                var clientRect = wp.getClientRectScreen(targetWindowHwnd);
                if (clientRect != null && clientRect.width > 0 && clientRect.height > 0) {
                    return wp.captureScreen(clientRect);
                }
                // 降级：窗口矩形 / 整个屏幕
                var winRect = wp.getWindowRect(targetWindowHwnd);
                if (winRect != null && winRect.width > 0 && winRect.height > 0) {
                    return wp.captureScreen(winRect);
                }
                return wp.captureScreen(null);
            }
            return platform.captureScreen(null);
        } catch (Exception e) {
            log.debug("Screen capture failed: {}", e.getMessage());
            return null;
        }
    }

    private void handleCaptureFailure() {
        failureCount++;
        if (failureCount >= MAX_FAILURES_BEFORE_FALLBACK) {
            var next = nextMode(captureMode);
            if (next != captureMode) {
                log.warn("Capture mode {} failed {} times, switching to {}", captureMode, failureCount, next);
                captureMode = next;
                failureCount = 0;
            } else {
                failureCount = 0;
            }
        }
    }

    /** 失败日志节流：同一失败每 30s 最多打一次（防止窗口失效时每帧刷屏，对齐 C++ 30s 节流） */
    private long lastFailureLogNanos = 0;
    private static final long FAILURE_LOG_INTERVAL_MS = 30000;

    private void logFailureThrottled(String msg, Object... args) {
        long now = System.nanoTime();
        if (now - lastFailureLogNanos > FAILURE_LOG_INTERVAL_MS * 1_000_000L) {
            lastFailureLogNanos = now;
            log.warn(msg, args);
        }
    }

    /** 降级链：PrintWindow 失败/黑图 → Robot（BITBLT 已移除） */
    private CaptureMode nextMode(CaptureMode cur) {
        return switch (cur) {
            case PRINT_WINDOW -> CaptureMode.ROBOT;
            case ROBOT -> CaptureMode.ROBOT;
        };
    }

    /** 空白/黑图检测：非黑像素比例 < 0.5% 判定为无效截图（GPU 渲染窗口 PrintWindow 黑屏特征） */
    private boolean isBlankImage(java.awt.image.BufferedImage img) {
        if (img == null) return true;
        int w = img.getWidth(), h = img.getHeight();
        if (w <= 0 || h <= 0) return true;
        int step = Math.max(1, Math.max(w, h) / 50);
        int samples = 0, lit = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                samples++;
                if (r > 24 || g > 24 || b > 24) lit++;
            }
        }
        return samples == 0 || (double) lit / samples < 0.005;
    }

    public CaptureMode getCaptureMode() {
        return captureMode;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override
    public void close() { stop(); if (detector != null) detector.close(); }
}
