package com.jiyi.infra.platform;

import java.awt.*;
import java.awt.image.BufferedImage;

public interface Platform {
    BufferedImage captureWindow(long hwnd, Rectangle rect);
    BufferedImage captureScreen(Rectangle rect);
    void mouseClick(Point from, Point to, ClickMode mode);
    long getWindowFromPoint(Point screenPoint);
    double getDpiScale(long hwnd);

    enum ClickMode { FRONT, BACK }

    default boolean isWindows() { return false; }
    default boolean isLinux() { return false; }
    default boolean isMac() { return false; }
}
