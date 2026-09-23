package com.jiyi.core.book;

import com.jiyi.core.model.Board;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 鹏飞 PF 开局库（SQLite，表 pfBook）。
 * vkey 以整数形式存储（无 cast），与 TCHESS PfOpenBook 一致。
 */
public class PfOpenBook implements OpenBook {
    private static final Logger log = LoggerFactory.getLogger(PfOpenBook.class);

    private final Connection conn;

    public PfOpenBook(String dbPath) {
        try {
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            log.info("Opened PF book: {}", dbPath);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open PF book: " + dbPath, e);
        }
    }

    @Override
    public List<BookEntry> query(Board board, boolean redGo) {
        var results = new ArrayList<BookEntry>();
        long hash = ZobristHasher.hash(board, redGo);
        results.addAll(queryByHash(hash, false, "PF库"));

        long hashLR = ZobristHasher.hash(board, redGo, true);
        if (hashLR != hash) {
            results.addAll(queryByHash(hashLR, true, "PF库(镜像)"));
        }
        return results;
    }

    private List<BookEntry> queryByHash(long zobrist, boolean leftRightSwap, String source) {
        var results = new ArrayList<BookEntry>();
        String sql = "SELECT vmove, vscore, vwin, vdraw, vlost, vmemo FROM pfBook WHERE vkey = " + zobrist + " AND vvalid = 1";
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int vm = rs.getInt("vmove");
                String move = decodeMove(vm);
                if (leftRightSwap) move = mirrorMove(move);
                int win = rs.getInt("vwin");
                int draw = rs.getInt("vdraw");
                int lose = rs.getInt("vlost");
                int score = rs.getInt("vscore");
                String memo = rs.getString("vmemo");
                if (memo == null) memo = "";
                double total = win + draw + lose;
                double wr = total > 0 ? (win + draw / 2.0) / total : 0;
                results.add(new BookEntry(move, score, wr, win, draw, lose, memo, source));
            }
        } catch (SQLException e) {
            log.warn("PF book query failed (hash={})", Long.toHexString(zobrist), e);
        }
        return results;
    }

    /** vmove 解码：高 8 位 = from 格 c90（0x33 + row*16 + col），低 8 位 = to 格 c90 */
    private String decodeMove(int vm) {
        return c90ToUci(vm >> 8) + c90ToUci(vm & 255);
    }

    private String c90ToUci(int c90) {
        int d = c90 - 0x33;
        int row = d / 16;
        int col = d % 16;
        return "" + (char) ('a' + col) + (9 - row);
    }

    private String mirrorMove(String uci) {
        if (uci.length() < 4) return uci;
        int fc = uci.charAt(0) - 'a';
        int fr = 9 - (uci.charAt(1) - '0');
        int tc = uci.charAt(2) - 'a';
        int tr = 9 - (uci.charAt(3) - '0');
        return "" + (char) ('a' + (8 - fc)) + (9 - fr) + (char) ('a' + (8 - tc)) + (9 - tr);
    }

    @Override
    public List<BookEntry> query(String fenCode, boolean onlyFinalPhase) {
        return List.of();
    }

    @Override
    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }
}
