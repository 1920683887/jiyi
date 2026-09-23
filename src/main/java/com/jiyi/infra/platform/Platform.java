package com.jiyi.infra.platform;

import java.awt.*;
import java.awt.image.BufferedImage;

public interface Platform {
    /** PrintWindow 后台截图（窗口可被遮挡，但对 GPU 渲染窗口可能返回黑图） */
    BufferedImage captureWindow(long hwnd, Rectangle rect);
    /** 屏幕区域截图：rect 为物理屏幕像素坐标（JNA ClientToScreen/GetWindowRect 同一坐标系），
     *  null = 整个虚拟屏幕；返回 1:1 物理像素（实现必须绕开 Robot 的 AWT 逻辑坐标空间） */
    BufferedImage captureScreen(Rectangle rect);
    /** @return true=点击已发出；false=目标窗口失效/注入失败（上层据此不推进局面） */
    boolean mouseClick(Point from, Point to, ClickMode mode);
    long getWindowFromPoint(Point screenPoint);
    Rectangle getWindowRect(long hwnd);

    enum ClickMode { FRONT, BACK }

    default boolean isWindows() { return false; }
    default boolean isLinux() { return false; }
    default boolean isMac() { return false; }
}
