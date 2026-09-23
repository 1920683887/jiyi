package com.jiyi.tools;

import com.jiyi.core.detection.BoardMatcher;
import com.jiyi.core.detection.DetectionResult;
import com.jiyi.core.detection.YoloDetector;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * 识别管线离线诊断工具：
 * 扫描 ./test-imgs 目录下的所有截图，逐个跑完整管线（YOLO → 匹配），
 * 输出棋盘候选、棋子检测、匹配结果与失败原因，便于定位预处理/检测/匹配问题。
 * 运行方式：测试识别.bat（或 mvn exec:java -Dexec.mainClass=com.jiyi.tools.PipelineDebug）
 * 结果写入：桌面/极弈日志/pipeline-debug.txt
 */
public class PipelineDebug {
    private static final String IMG_DIR = "./test-imgs";
    private static final String MODEL = "./models/yolov11.onnx";

    public static void main(String[] args) throws Exception {
        var dir = new File(IMG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("created " + dir.getAbsolutePath() + " - 请把截图放进去后重试");
            return;
        }
        File[] imgs = dir.listFiles((d, n) -> n.toLowerCase().matches(".*\\.(png|jpe?g|bmp)"));
        if (imgs == null || imgs.length == 0) {
            System.out.println("test-imgs 目录下没有图片，请放入截图");
            return;
        }

        var detector = new YoloDetector(MODEL);
        var matcher = new BoardMatcher();
        StringBuilder out = new StringBuilder();

        for (var f : imgs) {
            out.append("\n========== ").append(f.getName()).append(" ==========\n");
            try {
                var img = ImageIO.read(f);
                if (img == null) {
                    out.append("无法读取图片（格式不支持）\n");
                    continue;
                }
                out.append("image: ").append(img.getWidth()).append("x").append(img.getHeight()).append("\n");

                long t0 = System.currentTimeMillis();
                DetectionResult result = detector.detect(img);
                out.append("infer: ").append(System.currentTimeMillis() - t0).append("ms\n");

                // 模型原始输出（board 类 top5）：验证解析
                out.append("board raw (top 5):\n");
                for (var line : detector.debugBoardRaw(img)) {
                    out.append("  ").append(line).append("\n");
                }

                out.append("board candidates (").append(result.boardCandidates().size()).append("):\n");
                for (var c : result.boardCandidates()) {
                    out.append("  ").append(c).append(" area=").append(c.width * c.height).append("\n");
                }

                out.append("pieces (").append(result.pieces().size()).append("):\n");
                for (var p : result.pieces()) {
                    int cx = p.rect().x + p.rect().width / 2;
                    int cy = p.rect().y + p.rect().height / 2;
                    out.append(String.format("  %s conf=%.3f center=(%d,%d) box=%s%n",
                        p.label(), p.confidence(), cx, cy, p.rect()));
                }

                if (result.boardCandidates().isEmpty()) {
                    out.append(">> 无棋盘候选：检测阶段失败（模型/预处理/图片内容问题）\n");
                    continue;
                }

                // 每个候选逐个匹配（生产逻辑：主候选失败时依次 fallback）
                for (var cand : result.boardCandidates()) {
                    var diag = matcher.diagnose(new DetectionResult(cand, result.pieces()));
                    if (diag.error() == null) {
                        out.append("MATCH OK  候选=").append(cand)
                            .append(" flipped=").append(diag.flipped())
                            .append(" fen=").append(diag.board().toFen(true)).append("\n");
                    } else {
                        out.append("MATCH FAIL 候选=").append(cand)
                            .append(" 原因=").append(diag.error())
                            .append(" 映射后局面=").append(boardToString(diag.board())).append("\n");
                    }
                }
            } catch (Exception e) {
                out.append("ERROR: ").append(e).append("\n");
            }
        }

        String text = out.toString();
        System.out.print(text);
        var desktop = new File(System.getProperty("user.home") + "/Desktop/极弈日志");
        desktop.mkdirs();
        var res = new File(desktop, "pipeline-debug.txt");
        Files.writeString(res.toPath(), text, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("\n结果已写入: " + res.getAbsolutePath());
    }

    /** 把匹配出的原始局面打印为 10x9 网格，便于肉眼核对 */
    private static String boardToString(com.jiyi.core.model.Board board) {
        var sb = new StringBuilder();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                char p = board.pieceAt(r, c);
                sb.append(p == ' ' ? '.' : p);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
