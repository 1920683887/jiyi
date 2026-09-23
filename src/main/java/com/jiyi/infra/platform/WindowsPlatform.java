package com.jiyi.infra.platform;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinUser.INPUT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;

public class WindowsPlatform implements Platform {
    private static final Logger log = LoggerFactory.getLogger(WindowsPlatform.class);

    private final Robot robot;
    private final double screenScalingFactor;
    private boolean needScaling;
    private long currentHwnd;
    private GlobalMouseListener listener;
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
            if (!User32.INSTANCE.PrintWindow(hWnd, memDC, 0x1 | 0x2)) {
                log.warn("PrintWindow returned false");
                return null; // 让上层走降级计数
            }

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

            // rect 非 null 且尺寸有效时才裁剪棋盘区域
            // 注意：rect 与 image 同坐标系（都已是逻辑/缩放后尺寸），不能再除以 screenScalingFactor
            if (rect != null && rect.width > 0 && rect.height > 0) {
                image = image.getSubimage(rect.x, rect.y, rect.width, rect.height);
            }
            return image;
        } catch (Exception e) {
            log.warn("PrintWindow failed, fallback to screen capture", e);
            // 降级：捕获窗口屏幕区域（物理坐标）；rect 无效时捕获整屏
            return captureScreen(rect != null && rect.width > 0 && rect.height > 0 ? rect : null);
        } finally {
            GDI32.INSTANCE.DeleteDC(memDC);
            User32.INSTANCE.ReleaseDC(hWnd, hdc);
        }
    }

    /**
     * BitBlt 屏幕 DC 抓取（对齐 C++ ScreenCapture.captureByBitBlt）：rect 为物理屏幕像素
     * 坐标（与 JNA ClientToScreen/GetWindowRect 同一坐标系），返回 1:1 物理像素。
     * ★ 不可用 Robot：其坐标是 AWT 逻辑空间（uiScale），DPI 缩放下把物理坐标当逻辑用会
     *   整体偏移 scale 倍——125% 时请求 (1084,33) 实抓 (1355,41)，棋盘左缘被裁、残片被压缩，
     *   YOLO 框出“伪棋盘”导致识别全错（实测复现：越界区域为黑带）。
     */
    @Override
    public BufferedImage captureScreen(Rectangle rect) {
        try {
            int x, y, w, h;
            if (rect == null || rect.width <= 0 || rect.height <= 0) {
                x = User32.INSTANCE.GetSystemMetrics(WinUser.SM_XVIRTUALSCREEN);
                y = User32.INSTANCE.GetSystemMetrics(WinUser.SM_YVIRTUALSCREEN);
                w = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXVIRTUALSCREEN);
                h = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYVIRTUALSCREEN);
            } else {
                x = rect.x; y = rect.y; w = rect.width; h = rect.height;
            }
            var screenDC = User32.INSTANCE.GetDC(null);
            var memDC = GDI32.INSTANCE.CreateCompatibleDC(screenDC);
            try {
                var hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(screenDC, w, h);
                var old = GDI32.INSTANCE.SelectObject(memDC, hBitmap);
                // SRCCOPY | CAPTUREBLT（含分层窗口，与 Robot 行为一致）
                GDI32.INSTANCE.BitBlt(memDC, 0, 0, w, h, screenDC, x, y, 0x00CC0020 | 0x40000000);
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
                return image;
            } finally {
                GDI32.INSTANCE.DeleteDC(memDC);
                User32.INSTANCE.ReleaseDC(null, screenDC);
            }
        } catch (Exception e) {
            log.warn("BitBlt capture failed, fallback to Robot fullscreen", e);
            // 兜底：Robot 整屏（逻辑空间全屏内容完整，仅分辨率按 scale 缩小，无坐标错位）
            var ts = Toolkit.getDefaultToolkit().getScreenSize();
            return robot.createScreenCapture(new Rectangle(0, 0, ts.width, ts.height));
        }
    }

    @Override
    public boolean mouseClick(Point from, Point to, ClickMode mode) {
        // ★ 窗口句柄失效防护：目标窗口已关闭/最小化时中止点击，
        //   否则 FRONT 会以 offset=(0,0) 误点屏幕左上角附近其它应用、BACK 静默失败
        if (currentHwnd == 0 || !User32.INSTANCE.IsWindow(new WinDef.HWND(new Pointer(currentHwnd)))) {
            log.warn("Click aborted: target window invalid (hwnd=0x{})", Long.toHexString(currentHwnd));
            return false;
        }
        if (mode == ClickMode.BACK && currentHwnd != 0) {
            var hWnd = new WinDef.HWND(new Pointer(currentHwnd));
            // ★ 恒物理客户区坐标直接入 PostMessage lParam（对齐 C++ clickByPostMessage：
            //   不再乘 scale——检测框已统一为物理客户区坐标）
            log.info("BACK click hwnd=0x{}: ({},{})->({},{})", Long.toHexString(currentHwnd),
                from != null ? from.x : -1, from != null ? from.y : -1, to.x, to.y);
            boolean ok = true;
            if (from != null) ok &= sendClick(hWnd, from.x, from.y);
            ok &= sendClick(hWnd, to.x, to.y);
            return ok;
        } else {
            // 前台点击：坐标是物理客户区坐标，需加物理客户区屏幕原点。
            // ★ 对齐 C++ MouseInjector：用 ClientToScreen 取客户区原点（GetWindowRect 含标题栏会整体偏下）。
            //   检测框恒为物理客户区坐标（DetectionService 不再转逻辑），offset 也恒物理，直接相加。
            int offX = 0, offY = 0;
            var pt = new WinDef.POINT();
            if (currentHwnd != 0 && User32Extra.INSTANCE.ClientToScreen(
                    new WinDef.HWND(new Pointer(currentHwnd)), pt)) {
                offX = pt.x;
                offY = pt.y;
            }
            log.info("FRONT click hwnd=0x{} offset=({},{}): ({},{})->({},{})",
                Long.toHexString(currentHwnd), offX, offY,
                from != null ? from.x : -1, from != null ? from.y : -1, to.x, to.y);
            Point orig = MouseInfo.getPointerInfo().getLocation();  // 记录原位，点击后移回（对齐 TCHESS）
            // ★ SendInput 原子点击（对齐 C++ clickBySendInput）：绝对移动+按下同批注入，
            //   消除 Robot「移动→延迟→按下」两步之间光标被抢占/干扰导致的错点
            boolean ok = true;
            if (from != null) {
                ok &= sendInputClickAtomic(offX + from.x, offY + from.y);
            }
            ok &= sendInputClickAtomic(offX + to.x, offY + to.y);
            robot.mouseMove(orig.x, orig.y);
            return ok;
        }
    }

    /** 按下→抬起间隔（对齐 C++ clickPauseMs 默认 30）：无间隔可能被目标识别为无效点击 */
    private static final int CLICK_DOWN_UP_PAUSE_MS = 30;

    // MOUSEEVENTF_* 标志（JNA 未定义，值取自 WinUser.h）
    private static final int MOUSEEVENTF_MOVE = 0x0001;
    private static final int MOUSEEVENTF_LEFTDOWN = 0x0002;
    private static final int MOUSEEVENTF_LEFTUP = 0x0004;
    private static final int MOUSEEVENTF_VIRTUALDESK = 0x4000;
    private static final int MOUSEEVENTF_ABSOLUTE = 0x8000;

    /** SendInput 原子点击（对齐 C++ MouseInjector::clickBySendInput）：
     *  [绝对移动+左键按下] 同批注入 → GetCursorPos 回读漂移告警 → 停顿 → [重断言位置+抬起]。
     *  坐标为物理屏幕像素，按虚拟桌面归一化（多显示器安全）。@return false=注入失败 */
    private boolean sendInputClickAtomic(int x, int y) {
        int vx = User32.INSTANCE.GetSystemMetrics(WinUser.SM_XVIRTUALSCREEN);
        int vy = User32.INSTANCE.GetSystemMetrics(WinUser.SM_YVIRTUALSCREEN);
        int vw = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXVIRTUALSCREEN);
        int vh = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CYVIRTUALSCREEN);
        if (vw <= 0 || vh <= 0) {
            log.warn("SendInput click aborted: invalid virtual screen {}x{}", vw, vh);
            return false;
        }
        int cx = Math.max(vx, Math.min(x, vx + vw - 1));
        int cy = Math.max(vy, Math.min(y, vy + vh - 1));
        int nx = (int) ((cx - vx) * 65535L / (vw - 1));
        int ny = (int) ((cy - vy) * 65535L / (vh - 1));
        int baseFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK;

        // ★ JNA Structure 数组必须共享一块连续内存：toArray(2) 生成 [mouseInput 实例, 副本]；
        //   直接 new INPUT[]{a,b} 的两个元素各自分配内存，SendInput 编组时抛
        //   "Structure array elements must use contiguous memory"（2026-09-14 实测点击全失败）
        INPUT press1 = mouseInput(nx, ny, baseFlags);
        INPUT[] press = (INPUT[]) press1.toArray(2);
        fillMouseInput(press[1], nx, ny, baseFlags | MOUSEEVENTF_LEFTDOWN);
        int sent = User32Extra.INSTANCE.SendInput(2, press, press1.size());
        if (sent != 2) {
            log.warn("SendInput press failed: sent={}", sent);
            return false;
        }
        // 回读光标校验（对齐 C++ GetCursorPos 漂移告警 >2px）
        long[] pos = new long[1];
        if (User32Extra.INSTANCE.GetCursorPos(pos)) {
            int ax = (int) pos[0];               // POINT.x（低 32 位，打包复用现有 GetCursorPos 映射）
            int ay = (int) (pos[0] >>> 32);      // POINT.y（高 32 位）
            if (Math.abs(ax - x) > 2 || Math.abs(ay - y) > 2) {
                log.warn("Cursor drift after press: expect ({},{}) actual ({},{})", x, y, ax, ay);
            }
        }
        sleep(CLICK_DOWN_UP_PAUSE_MS);
        INPUT up1 = mouseInput(nx, ny, baseFlags);
        INPUT[] up = (INPUT[]) up1.toArray(2);
        fillMouseInput(up[1], nx, ny, baseFlags | MOUSEEVENTF_LEFTUP);
        sent = User32Extra.INSTANCE.SendInput(2, up, up1.size());
        if (sent != 2) {
            log.warn("SendInput release failed: sent={}", sent);
            return false;
        }
        return true;
    }

    private INPUT mouseInput(int nx, int ny, int flags) {
        INPUT inp = new INPUT();
        fillMouseInput(inp, nx, ny, flags);
        return inp;
    }

    private void fillMouseInput(INPUT inp, int nx, int ny, int flags) {
        inp.type = new WinDef.DWORD(INPUT.INPUT_MOUSE);
        WinUser.MOUSEINPUT mi = new WinUser.MOUSEINPUT();
        mi.dx = new WinDef.LONG(nx);
        mi.dy = new WinDef.LONG(ny);
        mi.mouseData = new WinDef.DWORD(0);
        mi.dwFlags = new WinDef.DWORD(flags);
        mi.time = new WinDef.DWORD(0);
        inp.input.mi = mi;
        inp.input.setType("mi");
    }

    /** @return PostMessage 是否全部成功（队列满等场景返回 false） */
    private boolean sendClick(WinDef.HWND hWnd, int x, int y) {
        var lParam = new WinDef.LPARAM((y << 16) | (x & 0xFFFF));
        boolean ok = User32Extra.INSTANCE.PostMessageW(hWnd, 0x0200, new WinDef.WPARAM(1), lParam).booleanValue();
        // DOWN→UP 间隔（对齐 C++ clickPauseMs）：无间隔可能被目标识别为无效点击
        sleep(5);
        ok &= User32Extra.INSTANCE.PostMessageW(hWnd, 0x0201, new WinDef.WPARAM(1), lParam).booleanValue();
        sleep(5);
        ok &= User32Extra.INSTANCE.PostMessageW(hWnd, 0x0202, new WinDef.WPARAM(0), lParam).booleanValue();
        return ok;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override
    public long getWindowFromPoint(Point p) {
        // POINT 结构打包：x 低 32 位、y 高 32 位（修正原 16 位打包错误）
        long point = ((long) p.y << 32) | (p.x & 0xFFFFFFFFL);
        var hWnd = User32Extra.INSTANCE.WindowFromPoint(point);
        return Pointer.nativeValue(hWnd.getPointer());
    }

    @Override
    public Rectangle getWindowRect(long hwnd) {
        try {
            var hWnd = new WinDef.HWND(new Pointer(hwnd));
            var rect = new WinDef.RECT();
            User32.INSTANCE.GetWindowRect(hWnd, rect);
            // 恒物理坐标（Robot 截图需要物理像素）
            return new Rectangle(rect.left, rect.top,
                rect.right - rect.left, rect.bottom - rect.top);
        } catch (Exception e) {
            log.debug("Failed to get window rect: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 客户区屏幕矩形（物理坐标）：ClientToScreen 客户区原点 + GetClientRect 尺寸。
     * 对齐 C++ ScreenCapture.captureByBitBlt：各截图方式统一以客户区为坐标基准，
     * 截图内坐标即客户区坐标，点击用同一原点还原。恒物理像素，不再按 needScaling 缩放。
     */
    public Rectangle getClientRectScreen(long hwnd) {
        try {
            var hWnd = new WinDef.HWND(new Pointer(hwnd));
            var origin = new WinDef.POINT();
            User32Extra.INSTANCE.ClientToScreen(hWnd, origin);
            var clientRect = new WinDef.RECT();
            User32.INSTANCE.GetClientRect(hWnd, clientRect);
            int x = origin.x, y = origin.y;
            int w = clientRect.right - clientRect.left, h = clientRect.bottom - clientRect.top;
            return new Rectangle(x, y, w, h);
        } catch (Exception e) {
            log.debug("Failed to get client rect screen: {}", e.getMessage());
            return null;
        }
    }

    // Window selection — 与TCHESS完全一致的实现
    public void startWindowSelection(java.util.function.Consumer<Long> onSelected) {
        log.info("Starting window selection...");
        this.windowCallback = onSelected;

        try {
            // 1. 先启动监听器（与TCHESS一致）
            this.listener = new GlobalMouseListener(e -> {
                log.info("Mouse click detected");
                try {
                    // 停止监听
                    this.listener.stop();
                    // 恢复光标
                    restoreCursor();
                    // 用 GetCursorPos 取物理屏幕坐标（与 TCHESS 一致）。
                    // 不能依赖 jnativehook 事件坐标：其在 DPI 缩放下可能与物理像素不一致，
                    // 而 WindowFromPoint 需要物理坐标，否则窗口查找错位导致选不中目标窗口。
                    long[] pos = new long[1];
                    User32Extra.INSTANCE.GetCursorPos(pos);
                    var hWnd = User32Extra.INSTANCE.WindowFromPoint(pos[0]);
                    // 取顶层窗口（GA_ROOT=2）：WindowFromPoint 可能命中多层窗口（如 Electron）的子窗口
                    var root = User32Extra.INSTANCE.GetAncestor(hWnd, 2);
                    if (root != null) hWnd = root;
                    long hwnd = Pointer.nativeValue(hWnd.getPointer());
                    log.info("Window handle obtained: 0x{}", Long.toHexString(hwnd));

                    // 校验 hwnd 有效（点击桌面空白处返回 0——B62）
                    if (hwnd == 0) {
                        log.warn("WindowFromPoint returned 0, selection failed");
                        if (windowCallback != null) windowCallback.accept(0L);
                        return;
                    }

                    // 排除本程序窗口：点击到极弈自身时视为无效选择（避免误选自己窗口）
                    var pidRef = new com.sun.jna.ptr.IntByReference();
                    User32Extra.INSTANCE.GetWindowThreadProcessId(hWnd, pidRef);
                    if (pidRef.getValue() == Kernel32.INSTANCE.GetCurrentProcessId()) {
                        log.warn("Selected own window (pid={}), rejected", pidRef.getValue());
                        if (windowCallback != null) windowCallback.accept(0L);
                        return;
                    }

                    currentHwnd = hwnd;
                    var hwndObj = new WinDef.HWND(new Pointer(hwnd));
                    needScaling = needScaling(hwndObj);

                    // 执行回调
                    if (windowCallback != null) {
                        windowCallback.accept(hwnd);
                    }
                } catch (Exception ex) {
                    log.error("Window selection failed", ex);
                    try { restoreCursor(); } catch (Exception ignored) {}
                }
            });
            this.listener.start();
            log.info("GlobalMouseListener started");

            // 2. 再改变光标（与TCHESS一致）
            selectCrossCursor();
            log.info("Cursor changed to cross");

        } catch (Exception e) {
            log.error("Failed to start window selection", e);
            restoreCursor();
        }
    }

    private void selectCrossCursor() {
        try {
            // 优先从 classpath 提取 ui/circle.ico 到临时文件（jar 内资源无法直接按路径加载）
            String cursorPath = extractCursorResource();
            if (cursorPath == null) {
                // 备用：JAR 同级目录（TCHESS 方式）
                cursorPath = getJarPath() + "ui/circle.ico";
            }
            WinDef.HCURSOR h = User32Extra.INSTANCE.LoadCursorFromFileA(cursorPath);
            if (h != null) {
                User32Extra.INSTANCE.SetSystemCursor(h, new WinDef.DWORD(32512));
                log.info("Cursor loaded from: {}", cursorPath);
            } else {
                log.warn("Cursor file not found or failed to load: {}", cursorPath);
            }
        } catch (Exception e) {
            log.debug("Failed to change cursor: {}", e.getMessage());
        }
    }

    /** 从 classpath 提取 ui/circle.ico 到临时文件，返回路径；资源缺失返回 null */
    private String extractCursorResource() {
        try (var in = getClass().getResourceAsStream("/ui/circle.ico")) {
            if (in == null) return null;
            var tmp = java.nio.file.Files.createTempFile("jiyi-cursor", ".ico");
            java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().deleteOnExit();
            return tmp.toString();
        } catch (Exception e) {
            log.debug("Failed to extract cursor resource: {}", e.getMessage());
            return null;
        }
    }

    private String getJarPath() {
        try {
            String path = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            path = java.net.URLDecoder.decode(path, "UTF-8");
            if (path.startsWith("/")) path = path.substring(1);
            int i = path.lastIndexOf("/");
            if (i >= 0) path = path.substring(0, i + 1);
            return path;
        } catch (Exception e) {
            return System.getProperty("user.dir") + "/";
        }
    }

    private boolean needScaling(WinDef.HWND hwnd) {
        int sysDpi = User32Extra.INSTANCE.GetDpiForSystem();
        int winDpi = User32Extra.INSTANCE.GetDpiForWindow(hwnd);
        return sysDpi != winDpi;
    }

    private void restoreCursor() {
        User32Extra.INSTANCE.SystemParametersInfoA(0x57, 0, 0, 2);
    }

    public long getCurrentHwnd() { return currentHwnd; }

    /**
     * 已知 GPU 渲染/特殊渲染窗口（PrintWindow 必黑屏）：如天天象棋（窗口类 QQChess）。
     * 对齐 C++ isSkipPrintWindow：此类窗口截图强制走 Robot（BITBLT 已移除，Robot 同为屏幕 DC 抓取）。
     */
    public boolean isKnownGpuRenderedWindow(long hwnd) {
        try {
            var hWnd = new WinDef.HWND(new Pointer(hwnd));
            char[] buf = new char[256];
            int len = User32.INSTANCE.GetClassName(hWnd, buf, 255);
            if (len <= 0) return false;
            return "qqchess".equals(new String(buf, 0, len).toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isWindows() { return true; }
}
