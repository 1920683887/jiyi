package com.jiyi.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class EngineProcess implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(EngineProcess.class);

    private final Process process;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private volatile boolean running;

    public EngineProcess(String command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command.split("\\s+"));
        pb.redirectErrorStream(true);
        this.process = pb.start();
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        this.running = true;
    }

    public void send(String command) {
        try {
            writer.write(command + "\n");
            writer.flush();
            log.debug("-> {}", command);
        } catch (IOException e) {
            log.error("Failed to send command: {}", command, e);
        }
    }

    public String readLine(long timeoutMs) {
        var future = CompletableFuture.supplyAsync(() -> {
            try {
                return reader.readLine();
            } catch (IOException e) {
                return null;
            }
        });
        try {
            return future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return null;
        }
    }

    public void startReading(Consumer<String> callback) {
        Thread.ofVirtual().name("engine-reader").start(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    log.debug("<- {}", line);
                    callback.accept(line);
                }
            } catch (IOException e) {
                if (running) log.error("Engine reader error", e);
            }
        });
    }

    @Override
    public void close() {
        running = false;
        try {
            send("quit");
            Thread.sleep(100);
            process.destroyForcibly();
            reader.close();
            writer.close();
        } catch (Exception e) {
            log.warn("Error closing engine", e);
        }
    }
}
