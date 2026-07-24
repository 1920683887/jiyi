package com.jiyi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ManualServiceTest {

    @Test
    void saveAndLoadPgn(@TempDir Path tempDir) throws Exception {
        var svc = new ManualService();
        svc.startNewRecord();
        svc.setMetadata("Test Game", "Local", "RedPlayer", "BlackPlayer", "*");
        svc.addMove("h2e2", "炮二平五", "");
        svc.addMove("h7e7", "炮8平5", "");
        svc.addMove("h0g2", "马二进三", "");

        var pgnPath = tempDir.resolve("test.pgn");
        svc.savePgn(pgnPath);

        var svc2 = new ManualService();
        svc2.loadPgn(pgnPath);
        assertEquals("Test Game", svc2.getRecord().eventName());
        assertEquals("RedPlayer", svc2.getRecord().redPlayer());
        assertEquals("BlackPlayer", svc2.getRecord().blackPlayer());
        assertEquals(3, svc2.getRecord().mainLine().size());
        assertEquals("h2e2", svc2.getRecord().mainLine().get(0).moveUci());
        assertEquals("h7e7", svc2.getRecord().mainLine().get(1).moveUci());
        assertEquals("h0g2", svc2.getRecord().mainLine().get(2).moveUci());
    }
}
