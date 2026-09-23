package com.jiyi.core.rule;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Move;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChineseTranslatorTest {

    private final ChineseTranslator translator = new ChineseTranslator();

    @Test
    void testToChinese_topRedPawnTwoOnFile_frontPawn() {
        // 同列两红兵：row3 与 row6。走 row3 兵（离黑方近）→ 前兵
        Board b = Board.fromFen("3k5/9/9/3P5/9/9/3P5/9/9/4K4 w - - 0 1");
        String s = translator.toChinese(b, new Move(3, 3, 2, 3));
        assertTrue(s.startsWith("前兵"), "应以前兵开头，实际: " + s);
    }

    @Test
    void testToChinese_bottomRedPawnTwoOnFile_rearPawn() {
        // 同列两红兵：row3 与 row6。走 row6 兵（靠己方底线）→ 后兵
        Board b = Board.fromFen("3k5/9/9/3P5/9/9/3P5/9/9/4K4 w - - 0 1");
        String s = translator.toChinese(b, new Move(6, 3, 5, 3));
        assertTrue(s.startsWith("后兵"), "应以后兵开头，实际: " + s);
    }

    @Test
    void testToChinese_blackPawns_frontRear() {
        // 同列两黑卒：row3 与 row6。走 row6 卒（离红方近）→ 前卒
        Board b = Board.fromFen("3k5/9/9/3p5/9/9/3p5/9/9/4K4 b - - 0 1");
        String s = translator.toChinese(b, new Move(6, 3, 7, 3));
        assertTrue(s.startsWith("前卒"), "黑方 row 大应为前卒，实际: " + s);
        // 走 row3 卒 → 后卒
        String s2 = translator.toChinese(b, new Move(3, 3, 4, 3));
        assertTrue(s2.startsWith("后卒"), "黑方 row 小应为后卒，实际: " + s2);
    }

    @Test
    void testToChinese_threeRedPawns_numbered() {
        // 同列三红兵：row2/row4/row6。走 row4 兵 → 中间应编号"二"
        Board b = Board.fromFen("3k5/9/3P5/9/3P5/9/3P5/9/9/4K4 w - - 0 1");
        String s = translator.toChinese(b, new Move(4, 3, 3, 3));
        assertTrue(s.startsWith("二兵"), "三个兵走中间应编'二'，实际: " + s);
        // 走 row2 兵（最前）→ "一兵"
        String s2 = translator.toChinese(b, new Move(2, 3, 1, 3));
        assertTrue(s2.startsWith("一兵"), "最前应编'一'，实际: " + s2);
    }

    @Test
    void testToChinese_redRookTwoOnFile_frontRear() {
        // 同列两红车：row1 与 row7。走 row1 车 → 前车；row7 车 → 后车（回归，不应被兵卒修复影响）
        Board b = Board.fromFen("3k5/3R5/9/9/9/9/9/3R5/9/4K4 w - - 0 1");
        String s1 = translator.toChinese(b, new Move(1, 3, 0, 3));
        assertTrue(s1.startsWith("前车"), "实际: " + s1);
        String s2 = translator.toChinese(b, new Move(7, 3, 6, 3));
        assertTrue(s2.startsWith("后车"), "实际: " + s2);
    }

    @Test
    void testToChinese_singlePawn_noPrefix() {
        // 单个红兵无同列兵 → 无前缀，如"兵五进一"
        Board b = Board.fromFen("3k5/9/9/9/9/9/5P3/9/9/4K4 w - - 0 1");
        String s = translator.toChinese(b, new Move(6, 5, 5, 5));
        assertFalse(s.startsWith("前") || s.startsWith("后") || s.startsWith("一"), "单兵不应有前缀，实际: " + s);
    }
}
