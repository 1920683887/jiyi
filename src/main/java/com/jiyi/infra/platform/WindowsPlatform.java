package com.jiyi.infra.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;

public class WindowsPlatform implements Platform {
    private static final Logger log = LoggerFactory.getLogger(WindowsPlatform.class);
    private final Robot robot;

    public WindowsPlatform() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BufferedImage captureWindow(long hwnd, Rectangle rect) {
        return captureScreen(rect);
    }

    @Override
    public BufferedImage captureScreen(Rectangle rect) {
        return robot.createScreenCapture(rect);
    }

    @Override
    public void mouseClick(Point from, Point to, ClickMode mode) {
        int x = to.x;
        int y = to.y;
        if (from != null) {
            robot.mouseMove(from.x, from.y);
            robot.delay(10);
        }
        robot.mouseMove(x, y);
        robot.delay(2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(2);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    @Override
    public long getWindowFromPoint(Point screenPoint) {
        return 0;
    }

    @Override
    public double getDpiScale(long hwnd) {
        return Toolkit.getDefaultToolkit().getScreenResolution() / 96.0;
    }

    @Override
    public boolean isWindows() { return true; }
}
