package com.jiyi.tools;

import com.jiyi.core.detection.BoardComparator;
import com.jiyi.core.detection.BoardMatcher;
import com.jiyi.core.detection.DetectionResult;
import com.jiyi.core.detection.YoloDetector;
import com.jiyi.core.model.Board;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.Arrays;

/**
 * 连线序列验证：对目录下的连续截图逐张识别，相邻帧 compare，
 * 验证"识别抖动下 compare 能否正确配对走子"（决定决策层是否可用）。
 * 用法: mvn exec:java -Dexec.mainClass=com.jiyi.tools.SequenceValidator -Dexec.args="<dir> <namePrefix>"
 */
public class SequenceValidator {
    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "./captures";
        String model = args.length > 1 ? args[1] : "./models/yolov11.onnx";

        File[] files = new File(dir).listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (files == null || files.length == 0) {
            System.out.println("no images in " + dir);
            return;
        }
        Arrays.sort(files);  // 按文件名（时间戳）排序
        // 取前 12 张作为一段对局序列
        int limit = Math.min(12, files.length);
        files = Arrays.copyOf(files, limit);

        var detector = new YoloDetector(model);
        var matcher = new BoardMatcher();
        var comparator = new BoardComparator();

        Board prevBoard = null;
        int detected = 0, matched = 0, changed = 0, opponent = 0, engine = 0, unclear = 0, newGame = 0;
        for (var f : files) {
            var img = ImageIO.read(f);
            if (img == null) continue;
            detected++;
            var result = detector.detect(img);
            if (result.boardRect() == null) {
                System.out.println(f.getName() + ": no board");
                continue;
            }
            // 候选 fallback（同 DetectionService）
            var match = matcher.match(result);
            if (match == null) {
                for (var cand : result.boardCandidates()) {
                    if (cand.equals(result.boardRect())) continue;
                    var m = matcher.match(new DetectionResult(cand, result.pieces()));
                    if (m != null) { match = m; result = new DetectionResult(cand, result.pieces()); break; }
                }
            }
            if (match == null) {
                System.out.println(f.getName() + ": match failed (pieces=" + result.pieces().size() + ")");
                continue;
            }
            matched++;
            Board board = match.board();

            if (prevBoard == null) {
                prevBoard = board;
                System.out.println(f.getName() + " [base] " + board.toFen(true));
                continue;
            }

            if (board.equals(prevBoard)) {
                System.out.println(f.getName() + " [same]");
                continue;
            }
            changed++;

            var cmp = comparator.compare(board, prevBoard, false, false);
            if (cmp == null) {
                System.out.println(f.getName() + " [null-diff?] " + board.toFen(true));
            } else {
                String detail = cmp.diff() == null ? "-" : cmp.diff().toMove().toUci();
                System.out.println(f.getName() + " -> " + cmp.action() + " " + detail
                    + " | " + board.toFen(true));
                switch (cmp.action()) {
                    case OPPONENT_MOVED -> opponent++;
                    case ENGINE_MOVED -> engine++;
                    case UNCLEAR -> unclear++;
                    case NEW_GAME -> newGame++;
                }
            }
            prevBoard = board;
        }

        System.out.println("\n===== SUMMARY =====");
        System.out.println("detected=" + detected + " matched=" + matched + " changed=" + changed
            + " opponent=" + opponent + " engine=" + engine + " unclear=" + unclear + " newGame=" + newGame);
    }
}
