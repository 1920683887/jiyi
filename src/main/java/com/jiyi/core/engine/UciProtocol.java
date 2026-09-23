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
    public void setMultiPV(int n) { setOption("MultiPV", String.valueOf(n)); }

    public void newGame() { proc.send("ucinewgame"); }

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

    public void goTimeWithSearchMoves(long ms, List<Move> searchMoves) {
        StringBuilder cmd = new StringBuilder("go movetime " + ms);
        if (searchMoves != null && !searchMoves.isEmpty()) {
            cmd.append(" searchmoves");
            for (Move m : searchMoves) {
                cmd.append(" ").append(m.toUci());
            }
        }
        proc.send(cmd.toString());
    }

    public void goDepthWithSearchMoves(int depth, List<Move> searchMoves) {
        StringBuilder cmd = new StringBuilder("go depth " + depth);
        if (searchMoves != null && !searchMoves.isEmpty()) {
            cmd.append(" searchmoves");
            for (Move m : searchMoves) {
                cmd.append(" ").append(m.toUci());
            }
        }
        proc.send(cmd.toString());
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
        return EngineOutputParser.parseInfo(line);
    }
}
