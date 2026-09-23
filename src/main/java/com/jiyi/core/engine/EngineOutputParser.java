package com.jiyi.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一的 info 行解析器，兼容 UCI 与 UCCI 两种协议：
 * <ul>
 *   <li>UCI：`info depth 12 score cp 45 ...` / `score mate 3`</li>
 *   <li>UCCI：`info depth 12 score 45 ...`（score 后直接是数字，无 cp/mate 前缀）</li>
 * </ul>
 * 采用 TCHESS 的 flag 状态机思路：遇到 `score` 后看下一 token——
 * 是 `cp`/`mate` 再取数字，否则（直接是数字）按分数解析。
 */
class EngineOutputParser {
    private static final Logger log = LoggerFactory.getLogger(EngineOutputParser.class);

    private EngineOutputParser() {}

    static EngineOutput parseInfo(String line) {
        try {
            int depth = 0, score = 0, mate = 0, pv = 1;
            long time = 0, nps = 0;
            boolean isMate = false;

            String[] tokens = line.split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                switch (tokens[i]) {
                    case "depth" -> { if (++i < tokens.length) depth = Integer.parseInt(tokens[i]); }
                    case "score" -> {
                        if (++i >= tokens.length) break;
                        if ("cp".equals(tokens[i])) {
                            if (++i < tokens.length) score = Integer.parseInt(tokens[i]);
                        } else if ("mate".equals(tokens[i])) {
                            if (++i < tokens.length) {
                                isMate = true;
                                mate = Integer.parseInt(tokens[i]);
                                score = mate;
                            }
                        } else {
                            // UCCI 风格：score 后直接是数字
                            score = Integer.parseInt(tokens[i]);
                        }
                    }
                    case "time" -> { if (++i < tokens.length) time = Long.parseLong(tokens[i]); }
                    case "nps" -> { if (++i < tokens.length) nps = Long.parseLong(tokens[i]); }
                    case "multipv" -> { if (++i < tokens.length) pv = Integer.parseInt(tokens[i]); }
                    case "pv" -> {
                        StringBuilder sb = new StringBuilder();
                        for (int j = i + 1; j < tokens.length; j++) sb.append(tokens[j]).append(" ");
                        return new EngineOutput.ThinkingData(depth, score, isMate, time, nps, pv, sb.toString().trim());
                    }
                }
            }
            return new EngineOutput.ThinkingData(depth, score, isMate, time, nps, pv, "");
        } catch (Exception e) {
            log.debug("Failed to parse engine output: {}", line, e);
            return null;
        }
    }
}
