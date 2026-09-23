package com.jiyi.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * 续盘按钮标定对话框（对齐 VinXiangQi ImageEditForm 的做法）：
 * 显示截图 → 用户拖拽框选"再来一局"按钮区域 → 保存为模板图到 autoclick 目录。
 * 用 Stage 模态窗口实现（JavaFX Dialog 无 ButtonType 时 X 关闭不可靠，表现为"关不掉"）。
 */
public final class AutoClickCalibrateDialog {
    private static final Logger log = LoggerFactory.getLogger(AutoClickCalibrateDialog.class);

    private AutoClickCalibrateDialog() {}

    public static void show(Window owner, java.awt.image.BufferedImage shot, File saveDir) {
        if (shot == null) {
            log.warn("Calibrate dialog: no screenshot provided");
            return;
        }
        if (saveDir != null && !saveDir.exists()) {
            saveDir.mkdirs();
        }

        // 截图 → JavaFX Image
        java.awt.image.BufferedImage rgb = new java.awt.image.BufferedImage(
            shot.getWidth(), shot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(shot, 0, 0, null);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(rgb, "png", baos);
        } catch (Exception e) {
            log.error("Failed to encode screenshot", e);
            return;
        }
        Image fxImage = new Image(new ByteArrayInputStream(baos.toByteArray()));
        ImageView imageView = new ImageView(fxImage);
        imageView.setPreserveRatio(true);
        // 限制显示尺寸（等比缩放，不放大），超出部分由 ScrollPane 滚动
        double imgW = fxImage.getWidth();
        double imgH = fxImage.getHeight();
        double fitScale = Math.min(1.0, Math.min(760 / imgW, 560 / imgH));
        imageView.setFitWidth(imgW * fitScale);
        imageView.setFitHeight(imgH * fitScale);

        // 缩放比：原图坐标 = 显示坐标 / fitScale（含 fitHeight 限制的精确值）
        double scale = fitScale;

        // 框选覆盖层
        Rectangle selection = new Rectangle();
        selection.setFill(Color.rgb(0, 150, 255, 0.25));
        selection.setStroke(Color.BLUE);
        selection.setStrokeWidth(1.5);
        selection.setVisible(false);

        // ★ 用 Pane（绝对定位）：StackPane 布局会强制子节点居中并覆盖 setX/setY，
        //   导致框选矩形与鼠标位置错位。Pane 下 Rectangle.setX/setY 直接生效。
        Pane imagePane = new Pane(imageView, selection);
        imagePane.setPrefSize(imgW * fitScale, imgH * fitScale);
        imagePane.setStyle("-fx-background-color: #222;");

        double[] dragStart = new double[2];  // 显示坐标
        boolean[] dragging = new boolean[]{false};

        imagePane.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            dragStart[0] = e.getX();
            dragStart[1] = e.getY();
            dragging[0] = true;
            selection.setVisible(true);
        });
        imagePane.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!dragging[0]) return;
            double x = Math.min(dragStart[0], e.getX());
            double y = Math.min(dragStart[1], e.getY());
            double w = Math.abs(e.getX() - dragStart[0]);
            double h = Math.abs(e.getY() - dragStart[1]);
            selection.setX(x);
            selection.setY(y);
            selection.setWidth(w);
            selection.setHeight(h);
        });
        imagePane.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> dragging[0] = false);

        ScrollPane imageScroll = new ScrollPane(imagePane);
        // fitToWidth=false：内容按实际尺寸滚动，避免拉伸导致框选坐标与显示错位
        imageScroll.setPrefSize(800, 620);
        imageScroll.setStyle("-fx-background-color: #222;");

        Label hint = new Label("在截图中用鼠标拖拽框选\"再来一局\"按钮区域，然后点\"保存模板\"。可多次框选保存多个模板（匹配到任一即点击）。");
        hint.setWrapText(true);

        Button saveBtn = new Button("保存模板");
        Button closeBtn = new Button("完成");

        // 已存模板列表（右侧）
        Label listTitle = new Label("已保存模板：");
        VBox templateList = new VBox(4);
        templateList.setPadding(new Insets(0, 4, 0, 4));

        // 数组包装实现自引用刷新（lambda 捕获自身需先赋值完成）
        Runnable[] refreshRef = new Runnable[1];
        refreshRef[0] = () -> {
            templateList.getChildren().clear();
            if (saveDir == null || !saveDir.isDirectory()) {
                templateList.getChildren().add(new Label("（无模板）"));
                return;
            }
            File[] files = saveDir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
            if (files == null || files.length == 0) {
                templateList.getChildren().add(new Label("（无模板）"));
                return;
            }
            Arrays.sort(files);
            for (File f : files) {
                HBox row = new HBox(6);
                Label name = new Label(f.getName());
                name.setMaxWidth(170);
                Button del = new Button("删除");
                del.setStyle("-fx-text-fill: red;");
                del.setOnAction(ev -> {
                    f.delete();
                    refreshRef[0].run();
                });
                row.getChildren().addAll(name, del);
                templateList.getChildren().add(row);
            }
        };
        Runnable refreshList = () -> refreshRef[0].run();

        saveBtn.setOnAction(e -> {
            if (!selection.isVisible() || selection.getWidth() < 5 || selection.getHeight() < 5) {
                hint.setText("请先在截图中拖拽框选按钮区域。");
                return;
            }
            int x = (int) Math.round(selection.getX() / scale);
            int y = (int) Math.round(selection.getY() / scale);
            int w = (int) Math.round(selection.getWidth() / scale);
            int h = (int) Math.round(selection.getHeight() / scale);
            x = Math.max(0, x); y = Math.max(0, y);
            w = Math.min(shot.getWidth() - x, w);
            h = Math.min(shot.getHeight() - y, h);
            if (w <= 0 || h <= 0) return;
            try {
                java.awt.image.BufferedImage crop = shot.getSubimage(x, y, w, h);
                File out = new File(saveDir, "template-" + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png");
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    ImageIO.write(crop, "png", fos);
                }
                hint.setText("已保存模板: " + out.getName() + "，可继续框选保存或点\"完成\"。");
                refreshList.run();
            } catch (Exception ex) {
                log.error("Failed to save template", ex);
                hint.setText("保存失败: " + ex.getMessage());
            }
        });

        HBox buttons = new HBox(10, saveBtn, closeBtn);
        buttons.setPadding(new Insets(8));

        ScrollPane listScroll = new ScrollPane(templateList);
        listScroll.setFitToWidth(true);
        VBox right = new VBox(8, listTitle, listScroll);
        right.setPrefWidth(200);

        BorderPane root = new BorderPane();
        root.setCenter(imageScroll);
        root.setRight(right);
        BorderPane.setMargin(right, new Insets(8));

        VBox bottom = new VBox(4, hint, buttons);
        bottom.setPadding(new Insets(8));
        root.setBottom(bottom);

        // ★ 用 Stage 模态窗口：X/ESC/完成按钮都能可靠关闭（JavaFX Dialog 无按钮时 X 关闭不可靠）
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("标定续盘按钮");
        stage.setMinWidth(900);
        stage.setMinHeight(700);
        stage.setScene(new Scene(root));
        closeBtn.setOnAction(e -> stage.close());
        stage.setOnCloseRequest(e -> stage.close());

        refreshList.run();
        stage.showAndWait();
    }
}
