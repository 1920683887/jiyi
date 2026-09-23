package com.jiyi.core.event;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private static class TestEvent {
        final int value;
        TestEvent(int value) { this.value = value; }
    }

    private static final class SubEvent extends TestEvent {
        SubEvent(int value) { super(value); }
    }

    private static final class OtherEvent {}

    @Test
    void testPost_subscriberException_doesNotStopOthers() {
        EventBus bus = new EventBus();
        AtomicInteger calls = new AtomicInteger();
        bus.register(TestEvent.class, e -> { throw new IllegalStateException("boom"); }, EventBus.Dispatch.POSTER);
        bus.register(TestEvent.class, e -> calls.incrementAndGet(), EventBus.Dispatch.POSTER);

        // 第一个订阅者抛异常，post 不应抛出、第二个订阅者仍应被调用
        assertDoesNotThrow(() -> bus.post(new TestEvent(1)));
        assertEquals(1, calls.get(), "第二个订阅者应仍被调用");
    }

    @Test
    void testPost_classTypeEvent_deliversToTypeSubscribers() {
        EventBus bus = new EventBus();
        AtomicInteger baseCalls = new AtomicInteger();
        AtomicInteger subCalls = new AtomicInteger();

        // 注册基类与子类
        bus.register(TestEvent.class, e -> baseCalls.incrementAndGet(), EventBus.Dispatch.POSTER);
        bus.register(SubEvent.class, e -> subCalls.incrementAndGet(), EventBus.Dispatch.POSTER);

        // 用两参数 post(SubEvent.class, subEvent)：只应分发给 SubEvent 订阅者（基类订阅不按 type 查表收到）
        bus.post(SubEvent.class, new SubEvent(1));
        assertEquals(0, baseCalls.get(), "按 type=SubEvent 分发不应命中基类订阅");
        assertEquals(1, subCalls.get());

        // 单参数 post(subEvent) 按运行时类分发：仍只命中 SubEvent（register 时按具体类登记）
        bus.post(new SubEvent(2));
        assertEquals(0, baseCalls.get());
        assertEquals(2, subCalls.get());
    }

    @Test
    void testUnregister_removesHandlerOnlyOnce() {
        EventBus bus = new EventBus();
        AtomicInteger calls = new AtomicInteger();
        var handler = new java.util.function.Consumer<TestEvent>() {
            @Override
            public void accept(TestEvent e) { calls.incrementAndGet(); }
        };
        bus.register(TestEvent.class, handler, EventBus.Dispatch.POSTER);
        bus.post(new TestEvent(1));
        assertEquals(1, calls.get());

        bus.unregister(TestEvent.class, handler);
        bus.post(new TestEvent(2));
        assertEquals(1, calls.get(), "注销后不应再收到事件");
    }

    @Test
    void testPost_noSubscribers_returnsSilently() {
        EventBus bus = new EventBus();
        assertDoesNotThrow(() -> bus.post(new OtherEvent()));
    }
}
