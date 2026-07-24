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
            list.removeIf(s -> s.handler == handler);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void post(T event) {
        List<Subscriber> list = subscribers.get(event.getClass());
        if (list == null) return;
        for (Subscriber s : list) {
            switch (s.dispatch) {
                case POSTER -> ((Consumer<T>) s.handler).accept(event);
                case PLATFORM -> Platform.runLater(() -> ((Consumer<T>) s.handler).accept(event));
                case ASYNC -> Thread.ofVirtual().start(() -> ((Consumer<T>) s.handler).accept(event));
            }
        }
    }

    public <T> void post(Class<T> type, T event) {
        post(event);
    }

    private record Subscriber(Object handler, Dispatch dispatch) {}
}
