package com.jiyi.infra.platform;

import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.mouse.NativeMouseEvent;
import org.jnativehook.mouse.NativeMouseInputListener;

import java.util.function.Consumer;

public class GlobalMouseListener implements NativeMouseInputListener {
    private final Consumer<NativeMouseEvent> callback;

    public GlobalMouseListener(Consumer<NativeMouseEvent> callback) {
        this.callback = callback;
    }

    public void nativeMouseClicked(NativeMouseEvent e) {
        callback.accept(e);
    }

    public void nativeMousePressed(NativeMouseEvent e) {
    }

    public void nativeMouseReleased(NativeMouseEvent e) {
    }

    public void nativeMouseMoved(NativeMouseEvent e) {
    }

    public void nativeMouseDragged(NativeMouseEvent e) {
    }

    public void start() throws NativeHookException {
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeMouseListener(this);
    }

    public void stop() throws NativeHookException {
        GlobalScreen.removeNativeMouseListener(this);
        GlobalScreen.unregisterNativeHook();
    }
}
