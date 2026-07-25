package com.jiyi.core.engine;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class UciProtocol {
    private static final Logger log = LoggerFactory.getLogger(UciProtocol.class);

    private final EngineProcess proc;
    private Consumer<EngineOutput> outputCallback;

    public UciProtocol(EngineProcess proc) {
        this.proc = proc;
        proc.addCallback(this::parseLine);
    }

    public void setOutputCallback(Consumer<EngineOutput> callback) {
        this.outputCallback = callback;
    }

    public void setOption(String name, String value) {
        proc.send("setoption name " + name + " value " + value);
    }

    public void setThreads(int n) { setOption("Threads", String.valueOf(n)); }
    public void setHash(int mb) { setOption("Hash", String.valueOf(mb)); }

    public void position(Board board, boolean redGo, List<Move> moves) {
        StringBuilder cmd = new StringBuilder("position fen " + board.toFen(redGo));
        if (!moves.isEmpty()) {
            cmd.append(" moves");
            for (Move m : moves) cmd.append(" ").append(m.toUci());
        }
        proc.send(cmd.toString());
    }

    public void goDepth(int depth) {
        proc.send("go depth " + depth);
    }

    public void goTime(long ms) {
        proc.send("go movetime " + ms);
    }

    public void goInfinite() {
        proc.send("go infinite");
    }

    public void stop() {
        proc.send("stop");
    }

    public void quit() {
        proc.close();
    }

    private void parseLine(String line) {
        if (line.startsWith("info")) {
            EngineOutput output = parseInfo(line);
            if (output != null && outputCallback != null) {
                outputCallback.accept(output);
            }
        } else if (line.startsWith("bestmove")) {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2 && outputCallback != null) {
                outputCallback.accept(new EngineOutput.BestMove(parts[1]));
            }
        }
    }

    private EngineOutput parseInfo(String line) {
        try {
            int depth = 0, score = 0, mate = 0, pv = 1;
            long time = 0, nps = 0;
            boolean isMate = false;

            String[] tokens = line.split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                switch (tokens[i]) {
                    case "depth" -> { if (++i < tokens.length) depth = Integer.parseInt(tokens[i]); }
                    case "score" -> {
                        if (++i >= tokens.length) break;
                        if ("cp".equals(tokens[i]) && ++i < tokens.length) {
                            score = Integer.parseInt(tokens[i]);
                        } else if ("mate".equals(tokens[i]) && ++i < tokens.length) {
                            isMate = true;
                            mate = Integer.parseInt(tokens[i]);
                            score = mate;
                        }
                    }
                    case "time" -> { if (++i < tokens.length) time = Long.parseLong(tokens[i]); }
                    case "nps" -> { if (++i < tokens.length) nps = Long.parseLong(tokens[i]); }
                    case "multipv" -> { if (++i < tokens.length) pv = Integer.parseInt(tokens[i]); }
                    case "pv" -> {
                        StringBuilder sb = new StringBuilder();
                        for (int j = i + 1; j < tokens.length; j++) sb.append(tokens[j]).append(" ");
                        return new EngineOutput.ThinkingData(depth, score, isMate, time, nps, pv, sb.toString().trim());
                    }
                }
            }
            return new EngineOutput.ThinkingData(depth, score, isMate, time, nps, pv, "");
        } catch (Exception e) {
            log.debug("Failed to parse engine output: {}", line, e);
            return null;
        }
    }
}
