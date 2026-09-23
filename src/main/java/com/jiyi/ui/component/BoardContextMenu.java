package com.jiyi.ui.component;

import com.jiyi.core.model.Board;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.util.function.Consumer;

/**
 * 棋盘右键菜单
 */
public class BoardContextMenu extends ContextMenu {

    public BoardContextMenu(
        Board currentBoard,
        boolean redToGo,
        Consumer<Void> onEditBoard,
        Consumer<Void> onFlipBoard,
        Consumer<Void> onAddVariation,
        Consumer<Void> onDeleteMove
    ) {
        this(currentBoard, redToGo, onEditBoard, onFlipBoard, onAddVariation, onDeleteMove, null, null, null);
    }

    public BoardContextMenu(
        Board currentBoard,
        boolean redToGo,
        Consumer<Void> onEditBoard,
        Consumer<Void> onFlipBoard,
        Consumer<Void> onAddVariation,
        Consumer<Void> onDeleteMove,
        Consumer<Void> onExportImage,
        Consumer<Void> onCopyImage
    ) {
        this(currentBoard, redToGo, onEditBoard, onFlipBoard, onAddVariation, onDeleteMove, onExportImage, onCopyImage, null);
    }

    public BoardContextMenu(
        Board currentBoard,
        boolean redToGo,
        Consumer<Void> onEditBoard,
        Consumer<Void> onFlipBoard,
        Consumer<Void> onAddVariation,
        Consumer<Void> onDeleteMove,
        Consumer<Void> onExportImage,
        Consumer<Void> onCopyImage,
        Consumer<Void> onPasteFen
    ) {
        // 复制 FEN
        MenuItem copyFen = new MenuItem("复制 FEN");
        copyFen.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(currentBoard.toFen(redToGo));
            clipboard.setContent(content);
        });

        // 粘贴 FEN
        MenuItem pasteFen = new MenuItem("粘贴 FEN");
        if (onPasteFen != null) {
            pasteFen.setOnAction(e -> onPasteFen.accept(null));
        } else {
            pasteFen.setDisable(true);
        }

        // 编辑局面
        MenuItem editBoard = new MenuItem("编辑局面");
        editBoard.setOnAction(e -> onEditBoard.accept(null));

        // 翻转棋盘
        MenuItem flipBoard = new MenuItem("翻转棋盘");
        flipBoard.setOnAction(e -> onFlipBoard.accept(null));

        // 分隔符
        SeparatorMenuItem separator1 = new SeparatorMenuItem();

        // 导出图片
        MenuItem exportImage = new MenuItem("导出图片");
        if (onExportImage != null) {
            exportImage.setOnAction(e -> onExportImage.accept(null));
        } else {
            exportImage.setDisable(true);
        }

        // 复制图片
        MenuItem copyImage = new MenuItem("复制图片");
        if (onCopyImage != null) {
            copyImage.setOnAction(e -> onCopyImage.accept(null));
        } else {
            copyImage.setDisable(true);
        }

        // 分隔符
        SeparatorMenuItem separator2 = new SeparatorMenuItem();

        // 添加为变招
        MenuItem addVariation = new MenuItem("添加为变招");
        addVariation.setOnAction(e -> onAddVariation.accept(null));

        // 删除此着法
        MenuItem deleteMove = new MenuItem("删除此着法");
        deleteMove.setOnAction(e -> onDeleteMove.accept(null));

        // 添加所有菜单项
        getItems().addAll(
            copyFen,
            pasteFen,
            editBoard,
            flipBoard,
            separator1,
            exportImage,
            copyImage,
            separator2,
            addVariation,
            deleteMove
        );
    }

    /**
     * 创建简化版右键菜单（仅基本功能）
     */
    public static BoardContextMenu createSimple(Board currentBoard, boolean redToGo) {
        return new BoardContextMenu(
            currentBoard,
            redToGo,
            v -> {}, // 空实现
            v -> {},
            v -> {},
            v -> {}
        );
    }
}
