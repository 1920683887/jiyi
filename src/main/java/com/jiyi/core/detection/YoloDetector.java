package com.jiyi.core.detection;

import ai.onnxruntime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.*;

public class YoloDetector implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(YoloDetector.class);
    private static final int SIZE = 640;
    private static final float NMS_THRESH = 0.45f;
    private static final float BOARD_PADDING = 0.8f;

    /** 置信度阈值：按模型自适应（对齐 C++：yolov5→0.75，yolov11/nano→0.5，其他→0.6） */
    private final float confThresh;

    static final String[] LABELS = {
        "n","b","a","k","r","c","p",
        "R","N","A","K","B","C","P","board"
    };

    private final OrtSession session;
    private final OrtEnvironment env;
    private final int numClasses;
    private final int stride;

    public YoloDetector(String modelPath) {
        try {
            // 置信度按模型自适应（对齐 C++ loadModel）
            String lower = modelPath.toLowerCase();
            if (lower.contains("yolov5") || lower.contains("v5")) {
                this.confThresh = 0.75f;
            } else if (lower.contains("yolov11") || lower.contains("v11") || lower.contains("nano")) {
                this.confThresh = 0.5f;
            } else {
                this.confThresh = 0.6f;
            }
            this.env = OrtEnvironment.getEnvironment();
            var opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(2);
            this.session = env.createSession(modelPath, opts);

            var outputInfo = session.getOutputInfo().values().iterator().next();
            long[] shape = outputInfo.getInfo() instanceof ai.onnxruntime.TensorInfo ti
                ? ti.getShape() : new long[]{1, -1, -1};
            // YOLO11 输出为 [1, C, N]（class-major）：C = 4 + numClasses（无 objectness），N = anchor 数。
            // stride 固定为每 anchor 的属性数，解析时按 class-major 布局转置访问（与 TCHESS reshape 一致）。
            this.numClasses = 15;
            this.stride = 4 + numClasses;
            log.info("YOLO loaded: {} classes, stride={}, shape={}", numClasses, stride, shape);
        } catch (OrtException e) {
            throw new RuntimeException("Failed to load YOLO: " + modelPath, e);
        }
    }

    public DetectionResult detect(BufferedImage img) throws OrtException {
        int iw = img.getWidth(), ih = img.getHeight();
        // letterbox 等比例缩放：长边缩到 SIZE（与 TCHESS Yolo11Model 的 rate=SIZE/max(w,h) 一致）
        float scale = (float) SIZE / Math.max(iw, ih);
        int sw = Math.round(iw * scale), sh = Math.round(ih * scale);
        int dw = (SIZE - sw) / 2, dh = (SIZE - sh) / 2;

        float[] data = new float[3 * SIZE * SIZE];
        var scaled = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
        var g = scaled.createGraphics();
        // 与 TCHESS Yolo11Model.processInput 一致：BILINEAR 高质量缩放（最近邻会产生锯齿影响检测精度）
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, sw, sh, null);
        g.dispose();

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int sx = x - dw, sy = y - dh;
                int rgb;
                if (sx >= 0 && sx < sw && sy >= 0 && sy < sh) {
                    rgb = scaled.getRGB(sx, sy);
                } else {
                    rgb = 0x727272;
                }
                data[y * SIZE + x] = ((rgb >> 16) & 0xFF) / 255.0f;
                data[SIZE * SIZE + y * SIZE + x] = ((rgb >> 8) & 0xFF) / 255.0f;
                data[2 * SIZE * SIZE + y * SIZE + x] = (rgb & 0xFF) / 255.0f;
            }
        }

        try (var tensor = OnnxTensor.createTensor(env,
                FloatBuffer.wrap(data), new long[]{1, 3, SIZE, SIZE});
             var results = session.run(Map.of("images", tensor))) {
            Object value = results.get(0).getValue();
            // ONNX 输出 shape=[1, stride, N]（含 batch 维），实际返回 float[][][]：
            // 取 batch=0 的二维视图 [stride][N] 供 parseOutput 解析
            float[][] output;
            if (value instanceof float[][][] v3) {
                output = v3[0];
            } else if (value instanceof float[][] v2) {
                output = v2;
            } else {
                throw new IllegalStateException("Unexpected model output type: " + value.getClass());
            }
            return parseOutput(output, iw, ih, scale, dw, dh);
        }
    }

    private DetectionResult parseOutput(float[][] output, int iw, int ih,
                                         float scale, int dw, int dh) {
        // 输出为 class-major 布局 [stride][N]（float[19][8400]）：
        // output[c][n] = 第 c 个通道（0-3 坐标，4+ 类别）的第 n 个 anchor。
        // 与 TCHESS Yolo11Model.reshape 的转置访问等价。
        int total = output[0].length;   // anchor 数（=8400），勿除以 stride！
        List<DetectionResult.PieceBox> boxes = new ArrayList<>();
        // 棋盘候选：{x, y, w, h, conf}（可能误检窗口顶部的小棋盘图/横幅）
        List<float[]> rawBoards = new ArrayList<>();

        for (int n = 0; n < total; n++) {
            float cx = (output[0][n] - dw) / scale;                       // 通道0：中心 x
            float cy = (output[1][n] - dh) / scale;                       // 通道1：中心 y
            float bw = output[2][n] / scale;                              // 通道2：宽
            float bh = output[3][n] / scale;                              // 通道3：高

            // YOLO11 无 objectness：类别概率即置信度（通道 4+）
            int cls = 0;
            float maxCls = 0;
            for (int j = 0; j < numClasses; j++) {
                float p = output[4 + j][n];
                if (p > maxCls) { maxCls = p; cls = j; }
            }
            if (maxCls < confThresh) continue;

            int x = Math.max(0, Math.round(cx - bw / 2));
            int y = Math.max(0, Math.round(cy - bh / 2));
            int rw = Math.min(iw - x, Math.round(bw));
            int rh = Math.min(ih - y, Math.round(bh));

            if (cls == numClasses - 1) {
                // 面积过滤：小于窗口 10% 的候选忽略（顶部小棋盘图/横幅误检特征）
                if (rw < iw * 0.1 || rh < ih * 0.1) continue;
                rawBoards.add(new float[]{x, y, rw, rh, maxCls});
            } else {
                // 棋子宽高比过滤（0.3~2.5）：排除拉伸/变形误检（对齐 C++ buildBoardDetections）
                int minSide = Math.min(rw, rh), maxSide = Math.max(rw, rh);
                if (maxSide > 0 && (double) maxSide / minSide > 2.5) continue;
                boxes.add(new DetectionResult.PieceBox(
                    LABELS[cls].charAt(0), maxCls, new Rectangle(x, y, rw, rh)));
            }
        }

        // NMS（per-class：只抑制同类检测，对齐 C++ nms）
        boxes.sort((a, b) -> Float.compare(b.confidence(), a.confidence()));
        List<DetectionResult.PieceBox> kept = new ArrayList<>();
        while (!boxes.isEmpty()) {
            var first = boxes.removeFirst();
            kept.add(first);
            boxes.removeIf(b -> b.label() == first.label()
                && iou(first.rect(), b.rect()) > NMS_THRESH);
        }

        // 棋盘候选：按面积降序取主候选（对齐 TCHESS Yolo5Model "取最大的棋盘区域"），
        // 其余作为 fallback 供 BoardMatcher 校验失败时逐个尝试。
        // 先做 board 类 NMS：同一棋盘的重复检测（位置几乎相同）合并为一个候选
        rawBoards.sort((a, b) -> Float.compare(b[4], a[4]));
        List<float[]> keptBoards = new ArrayList<>();
        for (var b : rawBoards) {
            var r = new Rectangle((int) b[0], (int) b[1], (int) b[2], (int) b[3]);
            boolean dup = false;
            for (var k : keptBoards) {
                var kr = new Rectangle((int) k[0], (int) k[1], (int) k[2], (int) k[3]);
                if (iou(r, kr) > NMS_THRESH) { dup = true; break; }
            }
            if (!dup) keptBoards.add(b);
        }
        rawBoards = keptBoards;
        // 按面积降序（主候选 = 面积最大）
        rawBoards.sort((a, b) -> Long.compare((long) b[2] * (long) b[3], (long) a[2] * (long) a[3]));
        List<Rectangle> boardCandidates = new ArrayList<>();
        for (var b : rawBoards) {
            // 不额外外扩：YOLO 框直接作为格点区（对齐 C++ boardRegion /8 映射）。
            // 外扩 0.8 格在棋盘占满窗口（窗口缩小后）时会 clamp 成全图，导致棋子映射错位。
            int x = Math.max(0, Math.min(iw - 1, (int) b[0]));
            int y = Math.max(0, Math.min(ih - 1, (int) b[1]));
            int w = Math.min(iw - x, (int) b[2]);
            int h = Math.min(ih - y, (int) b[3]);
            boardCandidates.add(new Rectangle(x, y, w, h));
        }
        Rectangle boardRect = boardCandidates.isEmpty() ? null : boardCandidates.get(0);

        return new DetectionResult(boardRect, kept, boardCandidates);
    }

    /**
     * 调试：打印 board 类 top N anchor 的原始输出（640 输入图坐标）与还原坐标，
     * 用于验证输出解析是否正确。
     */
    public List<String> debugBoardRaw(java.awt.image.BufferedImage img) throws OrtException {
        int iw = img.getWidth(), ih = img.getHeight();
        float scale = (float) SIZE / Math.max(iw, ih);
        int sw = Math.round(iw * scale), sh = Math.round(ih * scale);
        int dw = (SIZE - sw) / 2, dh = (SIZE - sh) / 2;

        float[] data = new float[3 * SIZE * SIZE];
        var scaled = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
        var g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, sw, sh, null);
        g.dispose();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int sx = x - dw, sy = y - dh;
                int rgb = (sx >= 0 && sx < sw && sy >= 0 && sy < sh) ? scaled.getRGB(sx, sy) : 0x727272;
                data[y * SIZE + x] = ((rgb >> 16) & 0xFF) / 255.0f;
                data[SIZE * SIZE + y * SIZE + x] = ((rgb >> 8) & 0xFF) / 255.0f;
                data[2 * SIZE * SIZE + y * SIZE + x] = (rgb & 0xFF) / 255.0f;
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add(String.format("scale=%.4f dw=%d dh=%d sw=%d sh=%d", scale, dw, dh, sw, sh));
        try (var tensor = OnnxTensor.createTensor(env,
                FloatBuffer.wrap(data), new long[]{1, 3, SIZE, SIZE})) {
            var results = session.run(Map.of("images", tensor));
            float[][] out = ((float[][][]) results.get(0).getValue())[0];
            List<float[]> boards = new ArrayList<>();
            for (int n = 0; n < out[0].length; n++) {
                float maxCls = 0; int cls = 0;
                for (int j = 0; j < numClasses; j++) {
                    float p = out[4 + j][n];
                    if (p > maxCls) { maxCls = p; cls = j; }
                }
                if (cls == numClasses - 1) {
                    boards.add(new float[]{out[0][n], out[1][n], out[2][n], out[3][n], maxCls});
                }
            }
            boards.sort((a, b) -> Float.compare(b[4], a[4]));
            for (int i = 0; i < Math.min(5, boards.size()); i++) {
                var b = boards.get(i);
                float cx = (b[0] - dw) / scale, cy = (b[1] - dh) / scale;
                float w = b[2] / scale, h = b[3] / scale;
                lines.add(String.format(
                    "raw cx=%.1f cy=%.1f w=%.1f h=%.1f conf=%.3f -> img (%.1f,%.1f) %.1fx%.1f",
                    b[0], b[1], b[2], b[3], b[4], cx, cy, w, h));
            }
        }
        return lines;
    }

    private float iou(Rectangle a, Rectangle b) {
        int x1 = Math.max(a.x, b.x), y1 = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.width, b.x + b.width);
        int y2 = Math.min(a.y + a.height, b.y + b.height);
        float inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float u = a.width * a.height + b.width * b.height - inter;
        return u > 0 ? inter / u : 0;
    }

    @Override
    public void close() {
        try { session.close(); } catch (OrtException e) { log.warn("close", e); }
    }
}

