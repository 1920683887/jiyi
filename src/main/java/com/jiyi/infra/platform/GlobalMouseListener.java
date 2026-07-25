package com.jiyi.infra.platform;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class GlobalMouseListener implements NativeMouseInputListener, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(GlobalMouseListener.class);
    private final Consumer<NativeMouseEvent> callback;

    public GlobalMouseListener(Consumer<NativeMouseEvent> callback) {
        this.callback = callback;
    }

    public void start() throws NativeHookException {
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeMouseListener(this);
    }

    @Override
    public void nativeMouseClicked(NativeMouseEvent e) {
        callback.accept(e);
    }

    @Override
    public void close() {
        try {
            GlobalScreen.removeNativeMouseListener(this);
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            log.warn("Failed to unregister native hook", e);
        }
    }
}
