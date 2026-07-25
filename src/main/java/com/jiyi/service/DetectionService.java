package com.jiyi.service;

import com.google.inject.Inject;
import com.jiyi.core.detection.BoardMatcher;
import com.jiyi.core.detection.YoloDetector;
import com.jiyi.infra.config.Config;
import com.jiyi.infra.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;

public class DetectionService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    private final Platform platform;
    private final AutomationService automation;
    private final YoloDetector detector;
    private final BoardMatcher matcher;
    private final Config config;

    private volatile boolean running;
    private long targetWindowHwnd;
    private boolean isFlipped;

    @Inject
    public DetectionService(Platform platform, AutomationService automation, Config config) {
        this.platform = platform;
        this.automation = automation;
        this.config = config;
        this.matcher = new BoardMatcher();

        YoloDetector d;
        try {
            d = new YoloDetector("./models/yolov11.onnx");
        } catch (Exception e) {
            log.warn("YOLO not loaded: {}", e.getMessage());
            d = null;
        }
        this.detector = d;
    }

    public boolean start(long hwnd) {
        if (detector == null) return false;
        this.targetWindowHwnd = hwnd;
        this.running = true;
        Thread.ofPlatform().name("detection").start(this::loop);
        log.info("Detection started on hwnd=0x{}", Long.toHexString(hwnd));
        return true;
    }

    public void stop() { running = false; }

    public void setFlipped(boolean f) { isFlipped = f; }

    private void loop() {
        while (running) {
            try {
                var screenshot = capture();
                if (screenshot == null) { sleep(500); continue; }

                var result = detector.detect(screenshot);
                if (result.boardRect() == null) { sleep(200); continue; }

                var match = matcher.match(result);
                if (match == null) { sleep(200); continue; }

                // Call AutomationService synchronously (on detection thread)
                automation.onBoardDetected(result, match.board(), isFlipped);

                sleep(config.link().scanIntervalMs());
            } catch (Exception e) {
                log.warn("Detection error", e);
                sleep(1000);
            }
        }
    }

    private BufferedImage capture() {
        if (targetWindowHwnd == 0) {
            var d = Toolkit.getDefaultToolkit().getScreenSize();
            return platform.captureScreen(new Rectangle(d));
        }
        var rect = new Rectangle();
        return platform.captureWindow(targetWindowHwnd, rect);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override
    public void close() { stop(); if (detector != null) detector.close(); }
}
