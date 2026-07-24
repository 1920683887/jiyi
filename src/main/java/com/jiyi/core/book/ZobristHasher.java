package com.jiyi.core.book;

import com.jiyi.core.model.Board;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ZobristHasher {
    private static final long[][] TABLE = new long[14][256];
    private static final long PLAYER = 0xA0CE2AF90C452F58L;
    private static final Map<Character, Integer> PIECE_INDEX = Map.ofEntries(
        Map.entry('K',0), Map.entry('A',1), Map.entry('B',2), Map.entry('N',3),
        Map.entry('R',4), Map.entry('C',5), Map.entry('P',6),
        Map.entry('k',7), Map.entry('a',8), Map.entry('b',9), Map.entry('n',10),
        Map.entry('r',11), Map.entry('c',12), Map.entry('p',13)
    );

    static {
        var rng = ThreadLocalRandom.current();
        for (int i = 0; i < 14; i++)
            for (int j = 0; j < 256; j++)
                TABLE[i][j] = rng.nextLong();
    }

    public static long hash(Board board, boolean redGo) {
        long h = 0;
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                char piece = board.pieceAt(r, c);
                if (piece == ' ') continue;
                Integer idx = PIECE_INDEX.get(piece);
                if (idx != null) h ^= TABLE[idx][r * 9 + c];
            }
        }
        if (redGo) h ^= PLAYER;
        return h;
    }
}
