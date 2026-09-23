package com.jiyi.ui.controller;

import com.jiyi.core.model.Board;
import com.jiyi.core.model.Piece;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.geometry.VPos;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 局面编辑器控制器
 * 功能：
 * 1. 支持拖拽棋子到棋盘
 * 2. 右键删除棋子
 * 3. 底部工具栏：清空棋盘、初始局面、确定、取消
 * 4. 实时显示 FEN 字符串
 * 5. 支持红黑方选择
 */
public class EditBoardController {
    private static final Logger log = LoggerFactory.getLogger(EditBoardController.class);

    @FXML private Canvas boardCanvas;
    @FXML private TextField fenTextField;
    @FXML private RadioButton redToMoveRadio;
    @FXML private RadioButton blackToMoveRadio;
    @FXML private ToggleGroup sideToMoveGroup;
    @FXML private Button redKingBtn;
    @FXML private Button redAdvisorBtn;
    @FXML private Button redBishopBtn;
    @FXML private Button redKnightBtn;
    @FXML private Button redRookBtn;
    @FXML private Button redCannonBtn;
    @FXML private Button redPawnBtn;
    @FXML private Button blackKingBtn;
    @FXML private Button blackAdvisorBtn;
    @FXML private Button blackBishopBtn;
    @FXML private Button blackKnightBtn;
    @FXML private Button blackRookBtn;
    @FXML private Button blackCannonBtn;
    @FXML private Button blackPawnBtn;
    @FXML private Button eraserBtn;
    @FXML private Button clearBtn;
    @FXML private Button resetBtn;
    @FXML private Button confirmBtn;
    @FXML private Button cancelBtn;

    private Stage stage;
    private Board currentBoard;
    private boolean redToGo = true;
    private Board resultBoard;
    private boolean confirmed = false;

    // 拖拽状态
    private Character draggedPiece = null;
    private int dragFromRow = -1;
    private int dragFromCol = -1;
    private boolean isDraggingFromBoard = false;

    @FXML
    public void initialize() {
        // 初始化为标准开局
        currentBoard = Board.STANDARD;

        // 设置走子方选择
        sideToMoveGroup = new ToggleGroup();
        redToMoveRadio.setToggleGroup(sideToMoveGroup);
        blackToMoveRadio.setToggleGroup(sideToMoveGroup);
        redToMoveRadio.setSelected(true);

        sideToMoveGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            if (newVal == redToMoveRadio) {
                redToGo = true;
            } else {
                redToGo = false;
            }
            updateFen();
        });

        // 初始化棋子按钮
        setupPieceButtons();

        // 绘制棋盘
        redrawBoard();
        updateFen();

        // 设置按钮事件
        clearBtn.setOnAction(e -> clearBoard());
        resetBtn.setOnAction(e -> resetToInitial());
        confirmBtn.setOnAction(e -> confirm());
        cancelBtn.setOnAction(e -> cancel());
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setBoard(Board board, boolean redToGo) {
        this.currentBoard = board != null ? board : Board.STANDARD;
        this.redToGo = redToGo;
        if (redToGo) {
            redToMoveRadio.setSelected(true);
        } else {
            blackToMoveRadio.setSelected(true);
        }
        redrawBoard();
        updateFen();
    }

    /**
     * 设置棋子按钮
     */
    private void setupPieceButtons() {
        // 红方棋子
        setupPieceButton(redKingBtn, 'K');
        setupPieceButton(redAdvisorBtn, 'A');
        setupPieceButton(redBishopBtn, 'B');
        setupPieceButton(redKnightBtn, 'N');
        setupPieceButton(redRookBtn, 'R');
        setupPieceButton(redCannonBtn, 'C');
        setupPieceButton(redPawnBtn, 'P');

        // 黑方棋子
        setupPieceButton(blackKingBtn, 'k');
        setupPieceButton(blackAdvisorBtn, 'a');
        setupPieceButton(blackBishopBtn, 'b');
        setupPieceButton(blackKnightBtn, 'n');
        setupPieceButton(blackRookBtn, 'r');
        setupPieceButton(blackCannonBtn, 'c');
        setupPieceButton(blackPawnBtn, 'p');

        // 橡皮擦
        eraserBtn.setOnAction(e -> {
            draggedPiece = ' ';
        });
    }

    /**
     * 设置单个棋子按钮
     */
    private void setupPieceButton(Button btn, char piece) {
        btn.setUserData(piece);
        btn.setOnAction(e -> {
            draggedPiece = piece;
        });
    }

    /**
     * 棋盘鼠标按下事件
     */
    @FXML
    public void onBoardMousePressed(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY) {
            int[] pos = screenToBoard(e.getX(), e.getY());
            if (pos != null) {
                if (draggedPiece != null) {
                    // 如果已选择棋子，直接放置
                    if (draggedPiece == ' ') {
                        removePiece(pos[0], pos[1]);
                    } else {
                        placePiece(pos[0], pos[1], draggedPiece);
                    }
                } else {
                    // 否则拾取棋盘上的棋子
                    char piece = currentBoard.pieceAt(pos[0], pos[1]);
                    if (piece != ' ') {
                        draggedPiece = piece;
                        dragFromRow = pos[0];
                        dragFromCol = pos[1];
                        isDraggingFromBoard = true;
                    }
                }
            }
        }
    }

    /**
     * 棋盘鼠标释放事件
     */
    @FXML
    public void onBoardMouseReleased(MouseEvent e) {
        if (draggedPiece != null && isDraggingFromBoard) {
            int[] pos = screenToBoard(e.getX(), e.getY());
            if (pos != null) {
                // 从棋盘拖动：移动棋子
                movePiece(dragFromRow, dragFromCol, pos[0], pos[1]);
            }
            draggedPiece = null;
            dragFromRow = -1;
            dragFromCol = -1;
            isDraggingFromBoard = false;
        }
    }

    /**
     * 棋盘点击事件（右键删除）
     */
    @FXML
    public void onBoardClicked(MouseEvent e) {
        if (e.getButton() == MouseButton.SECONDARY) {
            // 右键删除棋子
            int[] pos = screenToBoard(e.getX(), e.getY());
            if (pos != null) {
                removePiece(pos[0], pos[1]);
            }
        }
    }

    /**
     * 放置棋子
     */
    private void placePiece(int row, int col, char piece) {
        char[][] chars = currentBoard.toCharMatrix();
        chars[row][col] = piece;
        currentBoard = Board.fromChars(chars);
        redrawBoard();
        updateFen();
    }

    /**
     * 移动棋子（从棋盘上的一个位置到另一个位置）
     */
    private void movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        char[][] chars = currentBoard.toCharMatrix();
        chars[toRow][toCol] = chars[fromRow][fromCol];
        chars[fromRow][fromCol] = ' ';
        currentBoard = Board.fromChars(chars);
        redrawBoard();
        updateFen();
    }

    /**
     * 删除棋子
     */
    private void removePiece(int row, int col) {
        char[][] chars = currentBoard.toCharMatrix();
        chars[row][col] = ' ';
        currentBoard = Board.fromChars(chars);
        redrawBoard();
        updateFen();
    }

    /**
     * 清空棋盘
     */
    private void clearBoard() {
        char[][] chars = new char[Board.ROWS][Board.COLS];
        for (int i = 0; i < Board.ROWS; i++) {
            for (int j = 0; j < Board.COLS; j++) {
                chars[i][j] = ' ';
            }
        }
        currentBoard = Board.fromChars(chars);
        redrawBoard();
        updateFen();
    }

    /**
     * 重置为初始局面
     */
    private void resetToInitial() {
        currentBoard = Board.STANDARD;
        redToGo = true;
        redToMoveRadio.setSelected(true);
        redrawBoard();
        updateFen();
    }

    /**
     * 确定按钮
     */
    private void confirm() {
        // 验证局面合法性
        if (!isValidBoard()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("局面不合法");
            alert.setHeaderText("当前局面不合法");
            alert.setContentText("必须有且仅有一个红帅和一个黑将");
            alert.showAndWait();
            return;
        }

        resultBoard = currentBoard;
        confirmed = true;
        if (stage != null) {
            stage.close();
        }
    }

    /**
     * 取消按钮
     */
    private void cancel() {
        confirmed = false;
        if (stage != null) {
            stage.close();
        }
    }

    /**
     * 验证局面是否合法
     */
    private boolean isValidBoard() {
        int redKingCount = 0;
        int blackKingCount = 0;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                char piece = currentBoard.pieceAt(r, c);
                if (piece == 'K') redKingCount++;
                if (piece == 'k') blackKingCount++;
            }
        }

        return redKingCount == 1 && blackKingCount == 1;
    }

    /**
     * 更新FEN显示
     */
    private void updateFen() {
        String fen = currentBoard.toFen(redToGo);
        fenTextField.setText(fen);
    }

    /**
     * 屏幕坐标转棋盘坐标
     */
    private int[] screenToBoard(double x, double y) {
        double w = boardCanvas.getWidth();
        double h = boardCanvas.getHeight();
        double padding = w / 12;
        double cellW = (w - 2 * padding) / 8;
        double cellH = (h - 2 * padding) / 9;

        int col = (int) Math.round((x - padding) / cellW);
        int row = (int) Math.round((y - padding) / cellH);

        if (row < 0 || row >= Board.ROWS || col < 0 || col >= Board.COLS) {
            return null;
        }
        return new int[]{row, col};
    }

    /**
     * 重绘棋盘
     */
    private void redrawBoard() {
        GraphicsContext gc = boardCanvas.getGraphicsContext2D();
        double w = boardCanvas.getWidth();
        double h = boardCanvas.getHeight();
        drawBoard(gc, w, h);
    }

    /**
     * 绘制棋盘
     */
    private void drawBoard(GraphicsContext gc, double w, double h) {
        double padding = w / 12;
        double cellW = (w - 2 * padding) / 8;
        double cellH = (h - 2 * padding) / 9;
        double pieceR = cellW * 0.42;
        Color lineColor = Color.rgb(80, 50, 20);

        // 背景
        var woodGrad = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.rgb(232, 197, 138)),
            new Stop(1, Color.rgb(200, 165, 105)));
        gc.setFill(woodGrad);
        gc.fillRect(0, 0, w, h);

        // 边框
        gc.setStroke(lineColor);
        gc.setLineWidth(1.2);
        double m = padding * 0.3;
        gc.strokeRect(padding - m, padding - m, w - 2 * padding + 2 * m, h - 2 * padding + 2 * m);
        gc.setLineWidth(0.7);
        gc.strokeRect(padding - m * 0.5, padding - m * 0.5, w - 2 * padding + m, h - 2 * padding + m);

        // 横线
        for (int i = 0; i < 10; i++) {
            gc.strokeLine(padding, padding + i * cellH, w - padding, padding + i * cellH);
        }

        // 竖线
        for (int i = 0; i < 9; i++) {
            if (i == 0 || i == 8) {
                gc.strokeLine(padding + i * cellW, padding, padding + i * cellW, h - padding);
            } else {
                gc.strokeLine(padding + i * cellW, padding, padding + i * cellW, padding + 4 * cellH);
                gc.strokeLine(padding + i * cellW, padding + 5 * cellH, padding + i * cellW, h - padding);
            }
        }

        // 九宫格斜线
        gc.strokeLine(padding + 3 * cellW, padding, padding + 5 * cellW, padding + 2 * cellH);
        gc.strokeLine(padding + 5 * cellW, padding, padding + 3 * cellW, padding + 2 * cellH);
        gc.strokeLine(padding + 3 * cellW, h - padding, padding + 5 * cellW, h - padding - 2 * cellH);
        gc.strokeLine(padding + 5 * cellW, h - padding, padding + 3 * cellW, h - padding - 2 * cellH);

        // 兵/炮位标记
        int[][] starPos = {{1,2},{1,6},{2,1},{2,7},{7,1},{7,7},{8,2},{8,6}};
        for (int[] p : starPos) {
            double sx = padding + p[1] * cellW;
            double sy = padding + p[0] * cellH;
            double sl = cellW * 0.1;
            gc.strokeLine(sx - sl, sy, sx + sl, sy);
            gc.strokeLine(sx, sy - sl, sx, sy + sl);
        }

        // 楚河汉界
        gc.setFont(Font.font("楷体", cellH * 0.48));
        gc.setFill(Color.color(0.3, 0.2, 0.1, 0.55));
        double riverY = padding + 4.55 * cellH;
        gc.fillText("楚  河", padding + 1.2 * cellW, riverY);
        gc.fillText("漢  界", padding + 5.8 * cellW, riverY);

        // 绘制棋子
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                char piece = currentBoard.pieceAt(r, c);
                if (piece != ' ') {
                    double px = padding + c * cellW;
                    double py = padding + r * cellH;
                    drawPiece(gc, px, py, pieceR, piece);
                }
            }
        }
    }

    /**
     * 绘制单个棋子
     */
    private void drawPiece(GraphicsContext gc, double x, double y, double radius, char piece) {
        boolean isRed = Piece.isRedChar(piece);
        Color pieceColor = isRed ? Color.rgb(190, 30, 10) : Color.rgb(25, 100, 110);

        // 阴影
        gc.setFill(Color.gray(0, 0.15));
        gc.fillOval(x - radius + 2, y - radius + 2, radius * 2, radius * 2);

        // 棋子底色
        var pieceGrad = new RadialGradient(0, 0, 0.3, 0.3, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.WHITE),
            new Stop(0.7, Color.rgb(245, 240, 230)),
            new Stop(1, Color.rgb(220, 210, 190)));
        gc.setFill(pieceGrad);
        gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);

        // 外圈
        gc.setStroke(pieceColor);
        gc.setLineWidth(2.5);
        gc.strokeOval(x - radius, y - radius, radius * 2, radius * 2);

        // 内圈
        gc.setStroke(pieceColor.deriveColor(0, 1, 0.7, 0.3));
        gc.setLineWidth(1);
        gc.strokeOval(x - radius * 0.78, y - radius * 0.78, radius * 1.56, radius * 1.56);

        // 文字
        gc.setFill(pieceColor);
        gc.setFont(Font.font("楷体", radius * 0.9));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        String name = getPieceName(piece, isRed);
        gc.fillText(name, x, y);
    }

    /**
     * 获取棋子中文名
     */
    private String getPieceName(char piece, boolean isRed) {
        return switch (Character.toLowerCase(piece)) {
            case 'k' -> isRed ? "帅" : "将";
            case 'a' -> isRed ? "仕" : "士";
            case 'b' -> isRed ? "相" : "象";
            case 'n' -> "马";
            case 'r' -> "车";
            case 'c' -> "炮";
            case 'p' -> isRed ? "兵" : "卒";
            default -> "?";
        };
    }

    /**
     * 获取结果棋盘（确定后）
     */
    public Board getResultBoard() {
        return resultBoard;
    }

    /**
     * 获取走子方
     */
    public boolean isRedToGo() {
        return redToGo;
    }

    /**
     * 是否确认
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * 获取当前局面的FEN字符串
     */
    public String getFen() {
        return currentBoard.toFen(redToGo);
    }
}
