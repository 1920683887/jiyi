package com.jiyi.core.engine;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 直接测试引擎进程通信，不经过任何抽象层
 * 用于验证引擎是否能正常启动和响应UCI命令
 */
class EngineProcessTest {

    @Test
    void testUciProtocolDirectly() throws Exception {
        // 先在环境变量或固定路径找引擎
        String[] candidates = {
            "pikafish.exe", "pikafish",
            "engine.exe", "engine",
            "../pikafish.exe"
        };
        String enginePath = null;
        for (String c : candidates) {
            var f = new File(c);
            if (f.exists()) {
                enginePath = f.getAbsolutePath();
                break;
            }
        }

        // 也可以从用户指定的路径找
        var configFile = new File("config.json");
        if (configFile.exists() && enginePath == null) {
            // 粗略读取config找引擎路径
            try {
                var content = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                int idx = content.indexOf("path");
                if (idx > 0) {
                    int start = content.indexOf("\"", idx + 6) + 1;
                    int end = content.indexOf("\"", start);
                    if (start > 0 && end > start) {
                        String p = content.substring(start, end);
                        var f = new File(p);
                        if (f.exists()) enginePath = f.getAbsolutePath();
                    }
                }
            } catch (Exception ignored) {}
        }

        if (enginePath == null) {
            System.out.println("⚠ 未找到引擎文件，跳过测试。如需测试请将引擎exe放在项目根目录或配置config.json");
            return;
        }

        System.out.println("测试引擎: " + enginePath);

        // 直接启动引擎进程
        ProcessBuilder pb = new ProcessBuilder(enginePath);
        pb.redirectErrorStream(true);
        pb.directory(new File(".").getAbsoluteFile());
        Process proc = pb.start();

        var reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        var writer = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));

        // 发送 uci 命令
        System.out.println("-> uci");
        writer.write("uci\n");
        writer.flush();

        // 读取响应，寻找 uciok
        boolean foundUciok = false;
        StringBuilder output = new StringBuilder();
        var future = CompletableFuture.supplyAsync(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("<- " + line);
                    output.append(line).append("\n");
                    if (line.contains("uciok")) return true;
                }
            } catch (IOException ignored) {}
            return false;
        });

        foundUciok = future.get(5, TimeUnit.SECONDS);

        System.out.println("找到 uciok: " + foundUciok);
        System.out.println("引擎输出:\n" + output);

        if (!foundUciok) {
            // 尝试 ucci
            System.out.println("-> ucci");
            writer.write("ucci\n");
            writer.flush();
            var future2 = CompletableFuture.supplyAsync(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("<- " + line);
                        if (line.contains("ucciok")) return true;
                    }
                } catch (IOException ignored) {}
                return false;
            });
            boolean foundUcciok = future2.get(5, TimeUnit.SECONDS);
            System.out.println("找到 ucciok: " + foundUcciok);
            assertTrue(foundUciok || foundUcciok, "引擎必须支持 UCI 或 UCCI 协议");
        } else {
            // 测试 position + go + bestmove
            System.out.println("-> isready");
            writer.write("isready\n");
            writer.flush();

            var readyFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("<- " + line);
                        if (line.contains("readyok")) return true;
                    }
                } catch (IOException ignored) {}
                return false;
            });
            assertTrue(readyFuture.get(5, TimeUnit.SECONDS), "引擎应响应 readyok");

            // 发送 position + go
            System.out.println("-> position startpos");
            writer.write("position startpos\n");
            writer.flush();

            System.out.println("-> go movetime 1000");
            writer.write("go movetime 1000\n");
            writer.flush();

            var bestFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("<- " + line);
                        if (line.contains("bestmove")) return line;
                    }
                } catch (IOException ignored) {}
                return null;
            });
            String bestLine = bestFuture.get(10, TimeUnit.SECONDS);
            assertNotNull(bestLine, "引擎应返回 bestmove");
            assertTrue(bestLine.contains("bestmove"), "输出应包含 bestmove");
            System.out.println("✅ 引擎正常! bestmove: " + bestLine);
        }

        writer.write("quit\n");
        writer.flush();
        proc.destroy();
    }
}
