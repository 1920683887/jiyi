package com.jiyi.core.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EngineOutputParserTest {

    @Test
    void testParseInfo_uciScoreCp_parsesScore() {
        EngineOutput.ThinkingData td =
            (EngineOutput.ThinkingData) EngineOutputParser.parseInfo("info depth 12 score cp 45 time 100 nps 2000 multipv 1 pv h2e2 h9g7");
        assertNotNull(td);
        assertEquals(12, td.depth());
        assertEquals(45, td.score());
        assertFalse(td.isMate());
    }

    @Test
    void testParseInfo_ucciScorePlain_parsesScore() {
        // UCCI 引擎：score 后直接是数字，无 cp 前缀——核心回归
        EngineOutput.ThinkingData td =
            (EngineOutput.ThinkingData) EngineOutputParser.parseInfo("info depth 12 score 45 time 100 nps 2000 pv h2e2 h9g7");
        assertNotNull(td);
        assertEquals(12, td.depth());
        assertEquals(45, td.score(), "UCCI 分数应正确解析，而非恒 0");
        assertFalse(td.isMate());
    }

    @Test
    void testParseInfo_mate_parsesMate() {
        EngineOutput.ThinkingData td =
            (EngineOutput.ThinkingData) EngineOutputParser.parseInfo("info depth 10 score mate 3 pv h2e2");
        assertNotNull(td);
        assertTrue(td.isMate());
        assertEquals(3, td.score());
    }

    @Test
    void testParseInfo_pv_collectsPv() {
        EngineOutput.ThinkingData td =
            (EngineOutput.ThinkingData) EngineOutputParser.parseInfo("info depth 5 score cp 10 pv h2e2 h9g7 b0c2");
        assertNotNull(td);
        assertEquals("h2e2 h9g7 b0c2", td.pvLine());
    }

    @Test
    void testParseInfo_multipv_parsesPvNumber() {
        EngineOutput.ThinkingData td =
            (EngineOutput.ThinkingData) EngineOutputParser.parseInfo("info depth 8 multipv 3 score cp 20 pv h2e2");
        assertNotNull(td);
        assertEquals(3, td.pv());
    }

    @Test
    void testParseInfo_nonInfoLine_returnsDefaultData() {
        // 非 info 行（如 bestmove）不含解析 token，返回默认 ThinkingData（由调用方 parseLine 的 startsWith("info") 过滤）
        EngineOutput out = EngineOutputParser.parseInfo("bestmove h2e2");
        assertNotNull(out);
        assertInstanceOf(EngineOutput.ThinkingData.class, out);
        assertEquals(0, ((EngineOutput.ThinkingData) out).depth());
    }
}
