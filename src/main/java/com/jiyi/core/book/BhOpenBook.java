package com.jiyi.core.book;

import com.jiyi.core.model.Board;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BhOpenBook implements OpenBook {
    private static final Logger log = LoggerFactory.getLogger(BhOpenBook.class);

    private final Connection conn;

    public BhOpenBook(String dbPath) {
        try {
            this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            log.info("Opened book: {}", dbPath);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to open book: " + dbPath, e);
        }
    }

    @Override
    public List<BookEntry> query(Board board, boolean redGo) {
        var results = new ArrayList<BookEntry>();
        long hash = ZobristHasher.hash(board, redGo);
        results.addAll(queryByHash(hash, false, "本地库"));

        long hashLR = ZobristHasher.hash(board, redGo, true);
        if (hashLR != hash) {
            results.addAll(queryByHash(hashLR, true, "本地库(镜像)"));
        }
        return results;
    }

    /**
     * 按 vkey 查询，存储方式与 TCHESS 一致：
     * zobrist < 0 → vkey 存 double（longBitsToDouble 位模式）；zobrist >= 0 → vkey 存 integer。
     */
    private List<BookEntry> queryByHash(long zobrist, boolean leftRightSwap, String source) {
        var results = new ArrayList<BookEntry>();
        try {
            String sql;
            if (zobrist < 0) {
                double zobristDouble = Double.longBitsToDouble(zobrist);
                sql = "SELECT vmove, vscore, vwin, vdraw, vlost, vmemo FROM bhobk "
                    + "WHERE cast(vkey as double) = " + zobristDouble + " AND vvalid = 1";
            } else {
                sql = "SELECT vmove, vscore, vwin, vdraw, vlost, vmemo FROM bhobk "
                    + "WHERE cast(vkey as integer) = " + zobrist + " AND vvalid = 1";
            }
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    var entry = parseEntry(rs, source);
                    if (leftRightSwap) {
                        entry = new BookEntry(mirrorMove(entry.move()), entry.score(),
                            entry.winRate(), entry.winNum(), entry.drawNum(), entry.loseNum(), entry.note(), entry.source());
                    }
                    results.add(entry);
                }
            }
        } catch (SQLException e) {
            log.warn("Book query failed (hash={})", Long.toHexString(zobrist), e);
        }
        return results;
    }

    @Override
    public List<BookEntry> query(String fenCode, boolean onlyFinalPhase) {
        return List.of();
    }

    @Override
    public void close() {
        try { conn.close(); } catch (SQLException ignored) {}
    }

    private BookEntry parseEntry(ResultSet rs, String source) throws SQLException {
        int vm = rs.getInt("vmove");
        String move = decodeMove(vm);
        int score = rs.getInt("vscore");
        int win = rs.getInt("vwin");
        int draw = rs.getInt("vdraw");
        int lose = rs.getInt("vlost");
        String memo = rs.getString("vmemo");
        if (memo == null) memo = "";
        double total = win + draw + lose;
        double wr = total > 0 ? (win + draw / 2.0) / total : 0;
        return new BookEntry(move, score, wr, win, draw, lose, memo, source);
    }

    /**
     * 解码 vmove：高 8 位 = from 格的 c90 值（0x33 + row*16 + col），低 8 位 = to 格 c90 值。
     * 与 TCHESS getMoveFromVmove 的 coordMap 编码一致。
     */
    private String decodeMove(int vm) {
        int first = vm >> 8;
        int second = vm & 255;
        return c90ToUci(first) + c90ToUci(second);
    }

    private String c90ToUci(int c90) {
        int d = c90 - 0x33;
        int row = d / 16;
        int col = d % 16;
        return "" + (char) ('a' + col) + (9 - row);
    }

    /** 左右镜像着法：列 8-col（行不变），与 TCHESS leftRightSwap 语义一致 */
    private String mirrorMove(String uci) {
        if (uci.length() < 4) return uci;
        int fc = uci.charAt(0) - 'a';
        int fr = 9 - (uci.charAt(1) - '0');
        int tc = uci.charAt(2) - 'a';
        int tr = 9 - (uci.charAt(3) - '0');
        int mfc = 8 - fc;
        int mtc = 8 - tc;
        return "" + (char) ('a' + mfc) + (9 - fr) + (char) ('a' + mtc) + (9 - tr);
    }
}
