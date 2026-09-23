package com.jiyi.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EngineProcess implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(EngineProcess.class);

    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private volatile boolean running;
    private final List<Consumer<String>> callbacks = new CopyOnWriteArrayList<>();
    private Thread readerThread;

    // ★ readyok 门控（对齐 C++ UciEngine queueOrSend）：stop+isready 已发、readyok 未回期间，
    //   position/setoption/go/ucinewgame 一律入队，readyok 到达后按序冲刷——
    //   保证新命令只在引擎真正空闲后下发，防新搜索撞上收尾中的旧搜索（UCI 未定义行为/崩溃）
    private volatile boolean stopFlag = false;
    private volatile boolean waitingReady = false;
    private final java.util.ArrayDeque<String> queuedCommands = new java.util.ArrayDeque<>();

    public EngineProcess(String command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        this.process = pb.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        // 启动唯一读线程
        this.running = true;
        this.readerThread = Thread.ofVirtual().name("engine-reader").start(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    log.debug("<- {}", line);
                    // ★ readyok 确认引擎已停止：清 stopFlag/waitingReady 并冲刷挂起命令
                    //   （对齐 C++ UciEngine::parseLine readyok 分支；启动握手期 waitingReady=false 不受影响）
                    if ("readyok".equals(line.trim()) && waitingReady) {
                        flushQueuedAfterReady();
                    }
                    for (var cb : callbacks) {
                        try { cb.accept(line); } catch (Exception e) { log.warn("Callback error", e); }
                    }
                }
            } catch (IOException e) {
                if (running) log.error("Engine reader stopped", e);
            }
        });
    }

    /** stopThinking 同步打断（对齐 C++ UciEngine::stopThinking）：
     *  置 stopFlag（期间到达的旧 bestmove 视为陈旧丢弃）+ 发 stop/isready，
     *  readyok 回来前后续命令全部入队。幂等：已在等待 readyok 时不重复发。 */
    public synchronized void stopThinkingGate() {
        if (waitingReady) return;
        stopFlag = true;
        waitingReady = true;
        sendDirect("stop");
        sendDirect("isready");
    }

    /** stopFlag：true=stop 已发、readyok 未回，期间的 bestmove 是旧搜索收尾输出（陈旧） */
    public boolean isStopFlag() { return stopFlag; }

    /** readyok 到达：清标志并按序冲刷 stop 期间挂起的命令（读线程调用）。
     *  ★ 全程持锁冲刷：锁外发送会与并发 send() 交错乱序（如 setoption 插进 position/go 之间），
     *    send() 在锁上排队、冲刷完才放行 → 全局命令顺序严格保持 */
    private void flushQueuedAfterReady() {
        synchronized (this) {
            stopFlag = false;
            waitingReady = false;
            while (!queuedCommands.isEmpty()) {
                String c = queuedCommands.poll();
                log.debug("-> (flush) {}", c);
                sendDirect(c);
            }
        }
    }

    /** 命令写入加锁：UI/检测/读线程回调可能并发调用，无锁会导致命令交错损坏协议。
     *  ★ readyok 门控期间命令入队（quit 除外，保证进程可退出）。 */
    public synchronized void send(String cmd) {
        if (waitingReady && !"quit".equals(cmd)) {
            queuedCommands.add(cmd);
            log.debug("-> (queued) {}", cmd);
            return;
        }
        sendDirect(cmd);
    }

    private void sendDirect(String cmd) {
        try {
            writer.write(cmd + "\n");
            writer.flush();
            log.debug("-> {}", cmd);
        } catch (IOException e) {
            log.error("Failed to send: {}", cmd, e);
        }
    }

    public void addCallback(Consumer<String> callback) {
        callbacks.add(callback);
    }

    public void removeCallback(Consumer<String> callback) {
        callbacks.remove(callback);
    }

    /**
     * 注册引擎进程退出回调（崩溃/主动停止都会触发，由注册方用 running 标志区分）。
     * 对齐 C++ UciEngine 监听 QProcess::finished 的做法。
     */
    public void onExit(java.util.function.Consumer<Process> callback) {
        process.onExit().thenRun(() -> {
            if (callback != null) callback.accept(process);
        });
    }

    public boolean waitForLine(String contains, long timeoutMs) {
        var found = new boolean[]{false};
        var cb = (Consumer<String>) line -> {
            if (line.contains(contains)) found[0] = true;
        };
        callbacks.add(cb);
        try {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (!found[0] && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
        } catch (InterruptedException ignored) {
        } finally {
            callbacks.remove(cb);
        }
        return found[0];
    }

    @Override
    public void close() {
        running = false;
        try {
            send("quit");
            Thread.sleep(100);
        } catch (Exception e) {
            log.debug("Error sending quit", e);
        }

        // 关闭输入流以解除 readLine() 阻塞（阻塞 IO 不响应 interrupt，必须靠关闭流使其抛 IOException 退出）
        try {
            if (reader != null) reader.close();
        } catch (IOException e) {
            log.debug("Error closing reader", e);
        }

        // 等待读线程退出（join 兜底，readLine 被 reader.close 打断后应很快退出）
        if (readerThread != null) {
            readerThread.interrupt();
            try {
                readerThread.join(1000);
            } catch (InterruptedException ignored) {
            }
        }

        // 关闭写入端
        try {
            if (writer != null) writer.close();
        } catch (IOException e) {
            log.debug("Error closing writer", e);
        }

        // 强制终止进程
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        log.info("Engine process closed");
    }
}
