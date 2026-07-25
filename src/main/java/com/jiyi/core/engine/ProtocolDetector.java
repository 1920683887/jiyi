package com.jiyi.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtocolDetector {
    private static final Logger log = LoggerFactory.getLogger(ProtocolDetector.class);

    public enum Protocol { UCI, UCCI, UNKNOWN }

    public Protocol detect(EngineProcess proc) {
        var result = new Protocol[1];

        proc.addCallback(line -> {
            log.debug("Protocol detection got: {}", line);
            if (line.contains("uciok")) {
                log.info("Protocol detection: found uciok");
                result[0] = Protocol.UCI;
            }
            if (line.contains("ucciok")) {
                log.info("Protocol detection: found ucciok");
                result[0] = Protocol.UCCI;
            }
        });

        log.info("Protocol detection: sending uci...");
        proc.send("uci");
        if (pollResult(result)) {
            log.info("Protocol detection: {}", result[0]);
            return result[0];
        }

        log.info("Protocol detection: uci not detected, trying ucci...");
        proc.send("ucci");
        if (pollResult(result)) {
            log.info("Protocol detection: {}", result[0]);
            return result[0];
        }

        log.warn("Protocol detection: unknown engine protocol");
        return Protocol.UNKNOWN;
    }

    private boolean pollResult(Protocol[] result) {
        for (int i = 0; i < 30; i++) {
            if (result[0] != null) return true;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        return false;
    }
}
