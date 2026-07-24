package com.jiyi.core.model;

import java.util.HashMap;
import java.util.Map;

public enum Piece {
    RED_KING('K'),   RED_ADVISOR('A'), RED_BISHOP('B'),
    RED_KNIGHT('N'), RED_ROOK('R'),   RED_CANNON('C'), RED_PAWN('P'),
    BLACK_KING('k'),   BLACK_ADVISOR('a'), BLACK_BISHOP('b'),
    BLACK_KNIGHT('n'), BLACK_ROOK('r'),   BLACK_CANNON('c'), BLACK_PAWN('p');

    public final char symbol;

    Piece(char symbol) { this.symbol = symbol; }

    private static final Map<Character, Piece> BY_SYMBOL = new HashMap<>();
    static {
        for (Piece p : values()) BY_SYMBOL.put(p.symbol, p);
    }

    public static Piece fromSymbol(char c) { return BY_SYMBOL.get(c); }
    public boolean isRed() { return Character.isUpperCase(symbol); }
    public boolean isBlack() { return Character.isLowerCase(symbol); }
    public static boolean isRedChar(char c) { return c != ' ' && Character.isUpperCase(c); }
    public static boolean isBlackChar(char c) { return c != ' ' && Character.isLowerCase(c); }
}
