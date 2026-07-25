package com.jiyi.infra.platform;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;

public class WindowsPlatform implements Platform {
    private static final Logger log = LoggerFactory.getLogger(WindowsPlatform.class);

    private final Robot robot;
    private final double screenScalingFactor;
    private boolean needScaling;
    private long currentHwnd;
    private GlobalMouseListener globalMouse;
    private java.util.function.Consumer<Long> windowCallback;

    public WindowsPlatform() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Robot init failed", e);
        }
        var ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        this.screenScalingFactor = ge.getDefaultScreenDevice()
            .getDefaultConfiguration().getDefaultTransform().getScaleX();
    }

    @Override
    public BufferedImage captureWindow(long hwnd, Rectangle rect) {
        var hWnd = new WinDef.HWND(new Pointer(hwnd));
        var hdc = User32.INSTANCE.GetDC(hWnd);
        var memDC = GDI32.INSTANCE.CreateCompatibleDC(hdc);
        try {
            var bounds = new WinDef.RECT();
            User32.INSTANCE.GetClientRect(hWnd, bounds);
            int w = bounds.right - bounds.left;
            int h = bounds.bottom - bounds.top;
            if (needScaling) { w = (int)(w / screenScalingFactor); h = (int)(h / screenScalingFactor); }

            var hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdc, w, h);
            var old = GDI32.INSTANCE.SelectObject(memDC, hBitmap);
            User32.INSTANCE.PrintWindow(hWnd, memDC, 0x1 | 0x2);

            var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            var bmi = new WinGDI.BITMAPINFO();
            bmi.bmiHeader.biWidth = w;
            bmi.bmiHeader.biHeight = -h;
            bmi.bmiHeader.biPlanes = 1;
            bmi.bmiHeader.biBitCount = 32;
            bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

            var buffer = new Memory(w * h * 4L);
            GDI32.INSTANCE.GetDIBits(memDC, hBitmap, 0, h, buffer, bmi, WinGDI.DIB_RGB_COLORS);
            var data = buffer.getIntArray(0, w * h);
            image.setRGB(0, 0, w, h, data, 0, w);

            GDI32.INSTANCE.SelectObject(memDC, old);
            GDI32.INSTANCE.DeleteObject(hBitmap);

            if (rect != null) {
                int rx = needScaling ? (int)(rect.x / screenScalingFactor) : rect.x;
                int ry = needScaling ? (int)(rect.y / screenScalingFactor) : rect.y;
                int rw = needScaling ? (int)(rect.width / screenScalingFactor) : rect.width;
                int rh = needScaling ? (int)(rect.height / screenScalingFactor) : rect.height;
                image = image.getSubimage(rx, ry, rw, rh);
            }
            return image;
        } catch (Exception e) {
            log.warn("PrintWindow failed, fallback to Robot", e);
            return captureScreen(rect);
        } finally {
            GDI32.INSTANCE.DeleteDC(memDC);
            User32.INSTANCE.ReleaseDC(hWnd, hdc);
        }
    }

    @Override
    public BufferedImage captureScreen(Rectangle rect) {
        return robot.createScreenCapture(rect);
    }

    @Override
    public void mouseClick(Point from, Point to, ClickMode mode) {
        if (mode == ClickMode.BACK && currentHwnd != 0) {
            var hWnd = new WinDef.HWND(new Pointer(currentHwnd));
            if (needScaling) {
                from = new Point((int)(from.x * screenScalingFactor), (int)(from.y * screenScalingFactor));
                to = new Point((int)(to.x * screenScalingFactor), (int)(to.y * screenScalingFactor));
            }
            sendClick(hWnd, from.x, from.y);
            sendClick(hWnd, to.x, to.y);
        } else {
            if (from != null) { robot.mouseMove(from.x, from.y); robot.delay(10); }
            robot.mouseMove(to.x, to.y);
            robot.delay(2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(2);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
    }

    private void sendClick(WinDef.HWND hWnd, int x, int y) {
        var lParam = new WinDef.LPARAM((y << 16) | (x & 0xFFFF));
        User32.INSTANCE.PostMessage(hWnd, 0x0200, new WinDef.WPARAM(1), lParam);
        User32.INSTANCE.PostMessage(hWnd, 0x0201, new WinDef.WPARAM(1), lParam);
        User32.INSTANCE.PostMessage(hWnd, 0x0202, new WinDef.WPARAM(0), lParam);
    }

    @Override
    public long getWindowFromPoint(Point p) {
        var pt = new WinDef.POINT(p.x, p.y);
        var hWnd = User32Extra.INSTANCE.WindowFromPoint(pt);
        return Pointer.nativeValue(hWnd.getPointer());
    }

    @Override
    public double getDpiScale(long hwnd) {
        int sysDpi = User32Extra.INSTANCE.GetDpiForSystem();
        var hWnd = new WinDef.HWND(new Pointer(hwnd));
        int winDpi = User32Extra.INSTANCE.GetDpiForWindow(hWnd);
        this.needScaling = sysDpi != winDpi;
        return sysDpi / 96.0;
    }

    // Window selection — 启动全局鼠标钩子
    public void startWindowSelection(java.util.function.Consumer<Long> onSelected) {
        if (globalMouse != null) globalMouse.close();
        this.windowCallback = onSelected;
        try {
            selectCrossCursor();
            globalMouse = new GlobalMouseListener(e -> {
                try {
                    var pt = new WinDef.POINT(e.getX(), e.getY());
                    var hWnd = User32Extra.INSTANCE.WindowFromPoint(pt);
                    long hwnd = Pointer.nativeValue(hWnd.getPointer());
                    getDpiScale(hwnd);
                    currentHwnd = hwnd;
                    restoreCursor();
                    if (windowCallback != null) windowCallback.accept(hwnd);
                } catch (Exception ex) {
                    log.warn("Window selection failed", ex);
                }
            });
            globalMouse.start();
        } catch (Exception e) {
            log.warn("Native hook failed", e);
            restoreCursor();
        }
    }

    private void selectCrossCursor() {
        try {
            String[] paths = {"circle.ico", "D:\\极弈\\assets\\models\\..\\..\\circle.ico"};
            WinDef.HCURSOR hc = null;
            for (String p : paths) {
                hc = User32Extra.INSTANCE.LoadCursorFromFileA(p);
                if (hc != null) break;
            }
            if (hc != null) {
                User32Extra.INSTANCE.SetSystemCursor(hc, new WinDef.DWORD(32512));
            }
        } catch (Exception e) {
            log.debug("Cursor unavailable, will use default");
        }
    }

    private void restoreCursor() {
        User32Extra.INSTANCE.SystemParametersInfoA(0x57, 0, 0, 2);
    }

    public long getCurrentHwnd() { return currentHwnd; }

    @Override
    public boolean isWindows() { return true; }
}
