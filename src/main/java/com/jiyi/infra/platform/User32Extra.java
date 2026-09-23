package com.jiyi.infra.platform;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

public interface User32Extra extends User32 {
    User32Extra INSTANCE = Native.load("user32", User32Extra.class);

    WinDef.BOOL SetSystemCursor(WinDef.HCURSOR hcur, WinDef.DWORD id);
    WinDef.BOOL SystemParametersInfoA(int uiAction, int uiParam, int pvParam, int fWinIni);
    WinDef.HCURSOR LoadCursorFromFileA(String lpFileName);

    int GetDpiForSystem();
    int GetDpiForWindow(WinDef.HWND hwnd);

    boolean GetCursorPos(long[] lpPoint);
    HWND WindowFromPoint(long point);
    boolean ClientToScreen(WinDef.HWND hwnd, WinDef.POINT lpPoint);

    /** SendInput 原子点击（对齐 C++ MouseInjector::clickBySendInput） */
    int SendInput(int nInputs, WinUser.INPUT[] pInputs, int cbSize);

    /** PostMessage 带返回值版本（内置 PostMessage 映射为 void，无法观测投递失败） */
    WinDef.BOOL PostMessageW(WinDef.HWND hWnd, int msg, WinDef.WPARAM wParam, WinDef.LPARAM lParam);
}
