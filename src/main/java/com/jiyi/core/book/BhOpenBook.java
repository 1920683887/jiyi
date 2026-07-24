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

        try (var stmt = conn.prepareStatement(
                "SELECT vmove, vscore, vwin, vdraw, vlost, vmemo FROM bhobk WHERE cast(vkey as double) = ? AND vvalid = 1")) {
            stmt.setDouble(1, Double.longBitsToDouble(hash));
            var rs = stmt.executeQuery();
            while (rs.next()) {
                results.add(parseEntry(rs, "本地库"));
            }
        } catch (SQLException e) {
            log.warn("Book query failed", e);
        }

        long hashLR = ZobristHasher.hash(board.mirrorHorizontal(), redGo);
        if (hashLR != hash) {
            try (var stmt = conn.prepareStatement(
                    "SELECT vmove, vscore, vwin, vdraw, vlost, vmemo FROM bhobk WHERE cast(vkey as double) = ? AND vvalid = 1")) {
                stmt.setDouble(1, Double.longBitsToDouble(hashLR));
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    var entry = parseEntry(rs, "本地库(镜像)");
                    entry = new BookEntry(mirrorMove(entry.move()), entry.score(),
                        entry.winRate(), entry.winNum(), entry.drawNum(), entry.loseNum(), entry.note(), entry.source());
                    results.add(entry);
                }
            } catch (SQLException e) {
                log.warn("Book mirror query failed", e);
            }
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
        double wr = total > 0 ? win / total : 0;
        return new BookEntry(move, score, wr, win, draw, lose, memo, source);
    }

    private String decodeMove(int vm) {
        int fromRow = (vm >> 12) & 0xF;
        int fromCol = (vm >> 8) & 0xF;
        int toRow = (vm >> 4) & 0xF;
        int toCol = vm & 0xF;
        return "" + (char) ('a' + fromCol) + (9 - fromRow) + (char) ('a' + toCol) + (9 - toRow);
    }

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
