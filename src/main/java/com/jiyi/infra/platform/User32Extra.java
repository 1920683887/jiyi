package com.jiyi.infra.platform;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

public interface User32Extra extends User32 {
    User32Extra INSTANCE = Native.load("user32", User32Extra.class);

    boolean GetCursorPos(int[] lpPoint);
    WinDef.HWND WindowFromPoint(WinDef.POINT point);
    WinDef.BOOL SetSystemCursor(WinDef.HCURSOR hcur, WinDef.DWORD id);
    WinDef.BOOL SystemParametersInfoA(int uiAction, int uiParam, int pvParam, int fWinIni);
    WinDef.HCURSOR LoadCursorFromFileA(String lpFileName);
    int GetDpiForSystem();
    int GetDpiForWindow(WinDef.HWND hwnd);
}
