package com.jiyi.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EngineProcess implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(EngineProcess.class);

    private final Process process;
    private final BufferedWriter writer;
    private volatile boolean running;
    private final List<Consumer<String>> callbacks = new ArrayList<>();

    public EngineProcess(String command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        this.process = pb.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

        // 启动唯一读线程
        this.running = true;
        var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        Thread.ofVirtual().name("engine-reader").start(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    log.debug("<- {}", line);
                    for (var cb : callbacks) {
                        try { cb.accept(line); } catch (Exception e) { log.warn("Callback error", e); }
                    }
                }
            } catch (IOException e) {
                if (running) log.error("Engine reader stopped", e);
            }
        });
    }

    public void addCallback(Consumer<String> callback) {
        callbacks.add(callback);
    }

    public void send(String cmd) {
        try {
            writer.write(cmd + "\n");
            writer.flush();
            log.debug("-> {}", cmd);
        } catch (IOException e) {
            log.error("Failed to send: {}", cmd, e);
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            send("quit");
            Thread.sleep(100);
            process.destroyForcibly();
            writer.close();
        } catch (Exception e) {
            log.warn("Error closing engine", e);
        }
    }
}
