package com.jiyi.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtocolDetector {
    private static final Logger log = LoggerFactory.getLogger(ProtocolDetector.class);

    public enum Protocol { UCI, UCCI, UNKNOWN }

    public Protocol detect(EngineProcess proc) {
        proc.send("uci");
        for (int i = 0; i < 50; i++) {
            String line = proc.readLine(200);
            if (line == null) break;
            if (line.contains("uciok")) {
                log.info("Detected UCI protocol");
                return Protocol.UCI;
            }
        }
        proc.send("ucci");
        for (int i = 0; i < 50; i++) {
            String line = proc.readLine(200);
            if (line == null) break;
            if (line.contains("ucciok")) {
                log.info("Detected UCCI protocol");
                return Protocol.UCCI;
            }
        }
        log.warn("Could not detect engine protocol");
        return Protocol.UNKNOWN;
    }
}
