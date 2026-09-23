package com.jiyi.infra.platform;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.WinDef;

public interface GDI32Extra extends GDI32 {
    GDI32Extra INSTANCE = Native.load("gdi32", GDI32Extra.class);

    boolean StretchBlt(WinDef.HDC hdcDest, int xDest, int yDest, int wDest, int hDest,
                       WinDef.HDC hdcSrc, int xSrc, int ySrc, int wSrc, int hSrc, int dwRop);
}
