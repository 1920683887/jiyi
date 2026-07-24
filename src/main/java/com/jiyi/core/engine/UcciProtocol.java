package com.jiyi.core.engine;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

public class UcciProtocol {
    private static final Logger log = LoggerFactory.getLogger(UcciProtocol.class);

    private final EngineProcess proc;
    private Consumer<EngineOutput> outputCallback;

    public UcciProtocol(EngineProcess proc) {
        this.proc = proc;
        proc.startReading(this::parseLine);
    }

    public void setOutputCallback(Consumer<EngineOutput> callback) {
        this.outputCallback = callback;
    }

    public void setThreads(int n) { proc.send("setoption usethreads value " + n); }
    public void setHash(int mb) { proc.send("setoption hashsize value " + mb); }

    public void position(Board board, List<Move> moves) {
        StringBuilder cmd = new StringBuilder("position fen " + board.toFen(true));
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
        proc.send("go time " + ms);
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
            if (parts.length >= 2) {
                outputCallback.accept(new EngineOutput.BestMove(parts[1]));
            }
        }
    }

    private EngineOutput parseInfo(String line) {
        try {
            int depth = 0, score = 0, pv = 1;
            long time = 0, nps = 0;
            boolean isMate = false;

            String[] tokens = line.split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                switch (tokens[i]) {
                    case "depth" -> depth = Integer.parseInt(tokens[++i]);
                    case "score" -> {
                        if ("cp".equals(tokens[i + 1])) {
                            score = Integer.parseInt(tokens[++i]);
                            ++i;
                        } else if ("mate".equals(tokens[i + 1])) {
                            score = Integer.parseInt(tokens[++i]);
                            isMate = true;
                            ++i;
                        }
                    }
                    case "time" -> time = Long.parseLong(tokens[++i]);
                    case "nps" -> nps = Long.parseLong(tokens[++i]);
                    case "multipv" -> pv = Integer.parseInt(tokens[++i]);
                    case "pv" -> {
                        StringBuilder sb = new StringBuilder();
                        for (int j = i + 1; j < tokens.length; j++) sb.append(tokens[j]).append(" ");
                        return new EngineOutput.ThinkingData(depth, score, isMate, time, nps, pv, sb.toString().trim());
                    }
                }
            }
            return new EngineOutput.ThinkingData(depth, score, isMate, time, nps, pv, "");
        } catch (Exception e) {
            log.debug("Failed to parse UCCI output: {}", line, e);
            return null;
        }
    }
}
