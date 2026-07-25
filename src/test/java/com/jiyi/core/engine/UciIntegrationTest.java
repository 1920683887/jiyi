package com.jiyi.core.engine;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端 UCI 引擎通信测试
 * 验证引擎能正常接收命令并返回 bestmove
 */
class UciIntegrationTest {

    @Test
    void engineRespondsToUci() throws Exception {
        // 找到引擎文件
        String enginePath = findEngine();
        if (enginePath == null) {
            System.out.println("⚠ 未找到引擎，跳过测试。将引擎 exe 放在项目目录或 config.json 中配置");
            return;
        }

        System.out.println("测试引擎: " + enginePath);

        // 启动进程
        Process proc = new ProcessBuilder(enginePath)
            .redirectErrorStream(true)
            .directory(new File(".").getAbsoluteFile())
            .start();

        var reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        var writer = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));

        // 检测 UCI
        writer.write("uci\n");
        writer.flush();

        var uciok = CompletableFuture.supplyAsync(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("<- " + line);
                    if (line.contains("uciok")) return true;
                }
            } catch (IOException ignored) {}
            return false;
        });

        assertTrue(uciok.get(10, TimeUnit.SECONDS), "引擎应响应 uciok");

        // 发送 position + go，验证 bestmove
        writer.write("position startpos\n");
        writer.flush();

        writer.write("go movetime 2000\n");
        writer.flush();

        var bestmove = CompletableFuture.supplyAsync(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("<- " + line);
                    if (line.contains("bestmove")) return line;
                }
            } catch (IOException ignored) {}
            return null;
        });

        String result = bestmove.get(15, TimeUnit.SECONDS);
        assertNotNull(result, "引擎应在 15 秒内返回 bestmove");
        System.out.println("✅ 引擎正常! " + result);

        writer.write("quit\n");
        writer.flush();
        proc.destroy();
    }

    private String findEngine() {
        // 从 config.json 中读取引擎路径
        try {
            var configFile = new File("config.json");
            if (configFile.exists()) {
                var content = Files.readString(configFile.toPath());
                int pathIdx = content.indexOf("\"path\"");
                if (pathIdx > 0) {
                    int start = content.indexOf("\"", pathIdx + 7) + 1;
                    int end = content.indexOf("\"", start);
                    if (start > 0 && end > start) {
                        return content.substring(start, end);
                    }
                }
            }
        } catch (IOException ignored) {}

        // 常见文件名
        for (String name : new String[]{"pikafish.exe", "pikafish",
                "engine.exe", "engines/pikafish.exe",
                "../pikafish.exe", "../engines/pikafish.exe"}) {
            if (new File(name).exists()) return name;
        }
        return null;
    }
}
