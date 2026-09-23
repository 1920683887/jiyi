package com.jiyi.infra.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 棋盘图片导出工具
 */
public class BoardImageExporter {
    private static final Logger log = LoggerFactory.getLogger(BoardImageExporter.class);

    /**
     * 导出Canvas为PNG图片
     *
     * @param canvas 棋盘Canvas
     * @param file 目标文件
     * @return 是否成功
     */
    public static boolean exportToPng(Canvas canvas, File file) {
        try {
            requireFxThread();
            // 截取Canvas内容
            WritableImage writableImage = new WritableImage(
                (int) canvas.getWidth(),
                (int) canvas.getHeight()
            );

            SnapshotParameters params = new SnapshotParameters();
            canvas.snapshot(params, writableImage);

            // 直接保存
            javax.imageio.ImageIO.write(
                javafx.embed.swing.SwingFXUtils.fromFXImage(writableImage, null),
                "PNG",
                file
            );

            log.info("棋盘图片已导出：{}", file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            log.error("导出图片失败", e);
            return false;
        }
    }

    /**
     * 导出Canvas为指定大小的PNG图片（按目标尺寸缩放）
     *
     * @param canvas 棋盘Canvas
     * @param file 目标文件
     * @param width 目标宽度
     * @param height 目标高度
     * @return 是否成功
     */
    public static boolean exportToPng(Canvas canvas, File file, int width, int height) {
        try {
            requireFxThread();
            // 先按画布原始尺寸快照
            WritableImage writableImage = new WritableImage(
                (int) canvas.getWidth(),
                (int) canvas.getHeight()
            );
            SnapshotParameters params = new SnapshotParameters();
            canvas.snapshot(params, writableImage);

            // 转换为BufferedImage
            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(writableImage, null);

            // 按目标尺寸绘制缩放
            if (bufferedImage.getWidth() != width || bufferedImage.getHeight() != height) {
                BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = scaledImage.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(bufferedImage, 0, 0, width, height, null);
                g.dispose();
                bufferedImage = scaledImage;
            }

            // 保存为PNG
            ImageIO.write(bufferedImage, "PNG", file);

            log.info("棋盘图片已导出（{}x{}）：{}", width, height, file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            log.error("导出图片失败", e);
            return false;
        }
    }

    /** canvas.snapshot 必须在 JavaFX 线程执行，否则抛 IllegalStateException（非 IOException） */
    private static void requireFxThread() {
        if (!javafx.application.Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Canvas snapshot must run on JavaFX application thread");
        }
    }

    /**
     * 将Canvas复制到系统剪贴板
     *
     * @param canvas 棋盘Canvas
     * @return 是否成功
     */
    public static boolean copyToClipboard(Canvas canvas) {
        try {
            WritableImage writableImage = new WritableImage(
                (int) canvas.getWidth(),
                (int) canvas.getHeight()
            );

            SnapshotParameters params = new SnapshotParameters();
            canvas.snapshot(params, writableImage);

            // 复制到剪贴板
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putImage(writableImage);
            clipboard.setContent(content);

            log.info("棋盘图片已复制到剪贴板");
            return true;
        } catch (Exception e) {
            log.error("复制图片失败", e);
            return false;
        }
    }
}
