package com.jiyi.core.event;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {
    private static final Logger log = LoggerFactory.getLogger(EventBus.class);
    private final Map<Class<?>, List<Subscriber>> subscribers = new ConcurrentHashMap<>();

    public enum Dispatch { POSTER, PLATFORM, ASYNC }

    public <T> void register(Class<T> eventType, Consumer<T> handler, Dispatch dispatch) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
            .add(new Subscriber(handler, dispatch));
    }

    public <T> void register(Class<T> eventType, Consumer<T> handler) {
        register(eventType, handler, Dispatch.POSTER);
    }

    public <T> void unregister(Class<T> eventType, Consumer<T> handler) {
        List<Subscriber> list = subscribers.get(eventType);
        if (list != null) {
            // 使用同步避免并发修改
            synchronized (list) {
                list.removeIf(s -> s.handler == handler);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void post(T event) {
        dispatch(event.getClass(), event);
    }

    /**
     * 按指定类型分发。若 event 的运行时类型与 type 不一致（子类实例投递给基类订阅），
     * 这里按 type 查表分发——注册基类/接口的订阅者能收到子类事件。
     */
    public <T> void post(Class<T> type, T event) {
        dispatch(type, event);
    }

    @SuppressWarnings("unchecked")
    private <T> void dispatch(Class<?> type, T event) {
        List<Subscriber> list = subscribers.get(type);
        if (list == null) return;
        // 创建快照避免并发修改
        List<Subscriber> snapshot;
        synchronized (list) {
            snapshot = new ArrayList<>(list);
        }
        for (Subscriber s : snapshot) {
            switch (s.dispatch) {
                case POSTER -> {
                    // 隔离单个订阅者异常：继续分发其余订阅者，不回抛发布线程
                    try {
                        ((Consumer<T>) s.handler).accept(event);
                    } catch (Throwable t) {
                        log.error("Event handler failed for {}", type.getSimpleName(), t);
                    }
                }
                case PLATFORM -> Platform.runLater(() -> {
                    try {
                        ((Consumer<T>) s.handler).accept(event);
                    } catch (Throwable t) {
                        log.error("Event handler failed (FX) for {}", type.getSimpleName(), t);
                    }
                });
                case ASYNC -> Thread.ofVirtual().start(() -> {
                    try {
                        ((Consumer<T>) s.handler).accept(event);
                    } catch (Throwable t) {
                        log.error("Event handler failed (async) for {}", type.getSimpleName(), t);
                    }
                });
            }
        }
    }

    private record Subscriber(Object handler, Dispatch dispatch) {}
}
