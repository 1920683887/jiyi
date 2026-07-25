package com.jiyi.ui.controller;

import com.google.inject.Inject;
import com.jiyi.core.engine.EngineConfig;
import com.jiyi.core.event.EngineEvent;
import com.jiyi.core.event.EventBus;
import com.jiyi.core.event.GameEvent;
import com.jiyi.core.model.Board;
import com.jiyi.core.model.GameStatus;
import com.jiyi.core.model.Move;
import com.jiyi.core.model.Piece;
import com.jiyi.infra.config.Config;
import com.jiyi.infra.config.ConfigManager;
import com.jiyi.infra.util.WinRateCalculator;
import com.jiyi.service.EngineService;
import com.jiyi.service.GameService;
import com.jiyi.service.BookService;
import com.jiyi.service.ManualService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.geometry.VPos;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @Inject private EventBus eventBus;
    @Inject private GameService gameService;
    @Inject private EngineService engineService;
    @Inject private ManualService manualService;
    @Inject private BookService bookService;
    @Inject private Config config;
    @Inject private ConfigManager configManager;

    @FXML private Canvas boardCanvas;
    @FXML private ListView<String> engineOutput;
    @FXML private TableView<String> recordTable;
    @FXML private TextArea remarkText;
    @FXML private ComboBox<String> engineCombo;
    @FXML private ComboBox<Integer> threadCombo;
    @FXML private ComboBox<Integer> hashCombo;
    @FXML private Button tag1Btn;
    @FXML private Button tag2Btn;
    @FXML private Button tag3Btn;
    @FXML private Button tag4Btn;
    @FXML private Button playBtn;
    @FXML private Button pauseBtn;
    @FXML private Label manualInfoLabel;
    @FXML private TableView<String> bookTable;
    @FXML private Label statusLabel;
    @FXML private Label turnLabel;
    @FXML private Label winRateLabel;
    @FXML private BorderPane root;
    @FXML private Button engineRedButton;
    @FXML private Button engineBlackButton;
    @FXML private Button analysisButton;
    @FXML private Button flipButton;

    private Stage stage;
    private int selectedRow = -1, selectedCol = -1;
    private Move lastMove;
    private boolean isReverse = false;
    private int currentScore;
    private boolean currentIsRed;
    private volatile boolean engineThinking;
    private String engineSide = ""; // "red", "black", ""
    private boolean initialized;

    @FXML
    public void initialize() {
        // Prevent double initialization (called by FXMLLoader AND by App.start)
        if (initialized) return;
        initialized = true;

        eventBus.register(GameEvent.MoveExecuted.class, this::onMoveExecuted, EventBus.Dispatch.PLATFORM);
        eventBus.register(GameEvent.GameStarted.class, e -> {
            recordTable.getItems().clear();
            lastMove = null; selectedRow = -1;
            redrawBoard(e.board());
            turnLabel.setText("红方走棋");
            statusLabel.setText("新局");
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(GameEvent.BoardChanged.class, e -> {
            redrawBoard(e.board());
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(GameEvent.GameEnded.class, e -> {
            statusLabel.setText("对局结束: " + e.result());
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(EngineEvent.ThinkingUpdate.class, e -> {
            var d = e.data();
            String line = String.format("d%d %s %s %s",
                d.depth(), d.isMate() ? "M" + Math.abs(d.score()) : String.valueOf(d.score()),
                formatTime(d.timeMs()), d.pvLine());
            engineOutput.getItems().add(0, line);
            if (engineOutput.getItems().size() > 256)
                engineOutput.getItems().remove(256);
            currentScore = d.score();
            currentIsRed = gameService.isRedToGo();
            updateWinRate();
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(EngineEvent.EngineStarted.class, e -> {
            engineThinking = true;
            statusLabel.setText("引擎已启动: " + e.name());
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(EngineEvent.EngineStopped.class, e -> {
            engineThinking = false;
            statusLabel.setText("引擎已停止: " + e.name());
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(EngineEvent.BestMove.class, e -> {
            if (e.move() != null && isEngineTurn()) {
                gameService.executeMove(e.move());
            }
        }, EventBus.Dispatch.PLATFORM);

        threadCombo.getItems().addAll(1, 2, 4, 8, 16);
        threadCombo.setValue(4);
        hashCombo.getItems().addAll(16, 32, 64, 128, 256, 512, 1024, 2048, 4096);
        hashCombo.setValue(256);
        refreshEngineList();

        gameService.startNewGame();
        manualService.startNewRecord();
        setupTagButtons();
    }

    public void initialize(Stage stage) {
        this.stage = stage;
        initAccelerators(stage);
        log.info("极弈 started");
    }

    private void initAccelerators(Stage stage) {
        var scene = stage.getScene();
        if (scene == null) return;
        scene.getAccelerators().put(
            new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.Z,
                javafx.scene.input.KeyCombination.CONTROL_DOWN), () -> gameService.undo());
        scene.getAccelerators().put(
            new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.N,
                javafx.scene.input.KeyCombination.CONTROL_DOWN), () -> gameService.startNewGame());
        scene.getAccelerators().put(
            new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F5), this::flipBoard);
    }

    private void refreshEngineList() {
        engineCombo.getItems().clear();
        var list = config.engine().list();
        if (list.isEmpty()) {
            engineCombo.getItems().add("(无引擎)");
        } else {
            for (var e : list) engineCombo.getItems().add(e.name());
            String def = config.engine().defaultEngine();
            if (!def.isEmpty() && engineCombo.getItems().contains(def)) {
                engineCombo.setValue(def);
            } else {
                engineCombo.setValue(list.get(0).name());
            }
        }
    }

    private EngineConfig findSelectedEngine() {
        String name = engineCombo.getValue();
        if (name == null || name.equals("(无引擎)")) return null;
        return config.engine().list().stream()
            .filter(e -> e.name().equals(name)).findFirst().orElse(null);
    }

    @FXML
    public void onEngineManage() {
        try {
            var loader = new FXMLLoader(getClass().getResource("/fxml/engine_manager.fxml"));
            loader.setControllerFactory(com.jiyi.di.AppModule.getInjector()::getInstance);
            var scene = new Scene(loader.load(), 500, 400);
            var win = new Stage();
            win.setTitle("引擎管理");
            win.setScene(scene);
            win.initModality(Modality.WINDOW_MODAL);
            win.initOwner(stage);
            win.showAndWait();
            refreshEngineList();
        } catch (Exception e) {
            log.error("Failed to open engine manager", e);
        }
    }

    @FXML
    public void onEngineRed() {
        var cfg = findSelectedEngine();
        if (cfg == null) { statusLabel.setText("请先添加引擎"); return; }
        engineSide = "red";
        if (!startEngine(cfg)) return;
        if (gameService.isRedToGo()) {
            engineService.analyze(gameService.getCurrentBoard());
        }
        engineRedButton.setText("引擎红 ✓");
        engineBlackButton.setText("引擎黑");
        statusLabel.setText("引擎执红");
    }

    @FXML
    public void onEngineBlack() {
        var cfg = findSelectedEngine();
        if (cfg == null) { statusLabel.setText("请先添加引擎"); return; }
        engineSide = "black";
        if (!startEngine(cfg)) return;
        if (!gameService.isRedToGo()) {
            engineService.analyze(gameService.getCurrentBoard());
        }
        engineBlackButton.setText("引擎黑 ✓");
        engineRedButton.setText("引擎红");
        statusLabel.setText("引擎执黑");
    }

    @FXML
    public void onAnalysis() {
        var cfg = findSelectedEngine();
        if (cfg == null) { statusLabel.setText("请先添加引擎"); return; }
        if (engineService.isRunning()) {
            engineService.stopEngine();
            engineSide = "";
            analysisButton.setText("分析");
            engineRedButton.setText("引擎红");
            engineBlackButton.setText("引擎黑");
        } else {
            engineSide = "all";
            if (!startEngine(cfg)) return;
            engineService.analyze(gameService.getCurrentBoard());
            analysisButton.setText("停止");
        }
    }

    private boolean isEngineTurn() {
        return switch (engineSide) {
            case "red" -> gameService.isRedToGo();
            case "black" -> !gameService.isRedToGo();
            default -> false;
        };
    }

    @FXML
    public void onManualFirst() { manualNavigateTo(0); }

    @FXML
    public void onManualPrev() {
        if (manualService.currentIndex() > 0) manualNavigateTo(manualService.currentIndex() - 1);
    }

    @FXML
    public void onManualNext() {
        if (manualService.currentIndex() < manualService.totalMoves() - 1)
            manualNavigateTo(manualService.currentIndex() + 1);
    }

    @FXML
    public void onManualLast() { manualNavigateTo(manualService.totalMoves() - 1); }

    private void manualNavigateTo(int index) {
        if (index < 0 || index >= manualService.totalMoves()) return;
        manualService.setCurrentIndex(index);
        Board b = gameService.getBoardAtMove(index);
        eventBus.post(new GameEvent.BoardChanged(b));
        redrawBoard(b);
        updateManualInfo();
    }

    private void updateManualInfo() {
        int total = manualService.totalMoves();
        int cur = manualService.currentIndex() + 1;
        manualInfoLabel.setText("步数: " + cur + "/" + total);
    }

    private volatile boolean manualPlaying;
    private Thread manualPlayThread;

    @FXML
    public void onManualPlay() {
        if (manualPlaying) return;
        manualPlaying = true;
        playBtn.setDisable(true);
        pauseBtn.setDisable(false);
        manualPlayThread = Thread.ofVirtual().start(() -> {
            while (manualPlaying && manualService.currentIndex() < manualService.totalMoves() - 1) {
                javafx.application.Platform.runLater(() -> onManualNext());
                try { Thread.sleep(1200); } catch (InterruptedException e) { break; }
            }
            manualPlaying = false;
            javafx.application.Platform.runLater(() -> {
                playBtn.setDisable(false);
                pauseBtn.setDisable(true);
            });
        });
    }

    @FXML
    public void onManualPause() {
        manualPlaying = false;
        playBtn.setDisable(false);
        pauseBtn.setDisable(true);
        if (manualPlayThread != null) manualPlayThread.interrupt();
    }

    @FXML
    public void onManualDelete() {
        int idx = manualService.currentIndex();
        if (idx < 0 || idx >= manualService.totalMoves()) return;
        manualService.getRecord().mainLine().remove(idx);
        if (idx >= manualService.totalMoves()) idx = manualService.totalMoves() - 1;
        manualService.setCurrentIndex(idx);
        updateManualInfo();
    }

    @FXML
    public void onManualSave() {
        var fc = new javafx.stage.FileChooser();
        fc.setTitle("保存棋谱");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PGN 棋谱", "*.pgn"));
        var file = fc.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            manualService.savePgn(file.toPath());
            statusLabel.setText("已保存: " + file.getName());
        } catch (Exception e) {
            statusLabel.setText("保存失败");
        }
    }

    @FXML
    public void onManualOpen() {
        var fc = new javafx.stage.FileChooser();
        fc.setTitle("打开棋谱");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PGN 棋谱", "*.pgn"));
        var file = fc.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            manualService.loadPgn(file.toPath());
            gameService.startNewGame();
            if (manualService.totalMoves() > 0) {
                manualNavigateTo(0);
            }
            statusLabel.setText("已加载: " + file.getName());
        } catch (Exception e) {
            statusLabel.setText("加载失败: " + e.getMessage());
        }
    }

    private void setupTagButtons() {
        var tags = new String[]{"好棋!", "缓手", "漏招", "经典"};
        for (var btn : new Button[]{tag1Btn, tag2Btn, tag3Btn, tag4Btn}) {
            if (btn == null) continue;
            int idx = java.util.Arrays.asList(tag1Btn, tag2Btn, tag3Btn, tag4Btn).indexOf(btn);
            if (idx >= 0 && idx < tags.length) {
                btn.setText(tags[idx]);
                btn.setOnAction(e -> {
                    if (remarkText != null) remarkText.appendText("[" + btn.getText() + "] ");
                });
            }
        }
    }

    private boolean startEngine(EngineConfig cfg) {
        if (engineService.isRunning()) engineService.stopEngine();
        cfg.setThreads(threadCombo.getValue());
        cfg.setHash(hashCombo.getValue());
        engineService.startEngine(cfg);
        if (!engineService.isRunning()) {
            statusLabel.setText("引擎启动失败");
            return false;
        }
        return true;
    }

    @FXML
    public void newGame() { gameService.startNewGame(); }

    @FXML
    public void undoGame() { gameService.undo(); }

    @FXML
    public void exit() {
        if (engineService.isRunning()) engineService.stopEngine();
        if (stage != null) stage.close();
    }

    @FXML
    public void flipBoard() {
        isReverse = !isReverse;
        redrawBoard(gameService.getCurrentBoard());
    }

    @FXML
    public void copyFen() {
        var cb = javafx.scene.input.Clipboard.getSystemClipboard();
        var content = new javafx.scene.input.ClipboardContent();
        content.putString(gameService.getCurrentBoard().toFen(gameService.isRedToGo()));
        cb.setContent(content);
    }

    @FXML
    public void pasteFen() {
        var cb = javafx.scene.input.Clipboard.getSystemClipboard();
        String fen = cb.getString();
        if (fen != null && !fen.isEmpty()) {
            try {
                Board b = Board.fromFen(fen);
                gameService.startNewGame();
                redrawBoard(b);
            } catch (Exception e) {
                statusLabel.setText("无效的FEN");
            }
        }
    }

    @FXML
    public void onCanvasClicked(MouseEvent e) {
        Board board = gameService.getCurrentBoard();
        if (board == null) return;
        int[] grid = screenToBoard(e.getX(), e.getY());
        if (grid == null) return;
        int row = grid[0], col = grid[1];
        if (isReverse) { row = 9 - row; col = 8 - col; }

        if (selectedRow == -1) {
            char piece = board.pieceAt(row, col);
            if (piece != ' ' && Piece.isRedChar(piece) == gameService.isRedToGo()) {
                selectedRow = row; selectedCol = col;
                redrawBoard(board);
            }
        } else {
            Move move = new Move(selectedRow, selectedCol, row, col);
            if (gameService.executeMove(move)) {
                selectedRow = -1; selectedCol = -1;
            } else {
                char piece = board.pieceAt(row, col);
                if (piece != ' ' && Piece.isRedChar(piece) == gameService.isRedToGo()) {
                    selectedRow = row; selectedCol = col;
                }
                redrawBoard(board);
            }
        }
    }

    private void onMoveExecuted(GameEvent.MoveExecuted event) {
        redrawBoard(event.board());
        lastMove = event.move();
        selectedRow = -1;
        recordTable.getItems().add(event.move().toUci());
        recordTable.scrollTo(recordTable.getItems().size() - 1);
        turnLabel.setText(event.isRed() ? "黑方走棋" : "红方走棋");
        if (engineService.isRunning() && isEngineTurn()) {
            engineService.analyze(event.board());
        }
    }

    private int[] screenToBoard(double x, double y) {
        double w = boardCanvas.getWidth();
        double h = boardCanvas.getHeight();
        double padding = w / 12;
        double cellW = (w - 2 * padding) / 8;
        double cellH = (h - 2 * padding) / 9;
        int col = (int) Math.round((x - padding) / cellW);
        int row = (int) Math.round((y - padding) / cellH);
        if (row < 0 || row > 9 || col < 0 || col > 8) return null;
        return new int[]{row, col};
    }

    private void drawBoard(GraphicsContext gc, Board board, double w, double h) {
        Board displayBoard = isReverse ? board.mirrorVertical() : board;
        double padding = w / 12;
        double cellW = (w - 2 * padding) / 8;
        double cellH = (h - 2 * padding) / 9;
        double pieceR = cellW * 0.42;
        Color lineColor = Color.rgb(80, 50, 20);

        var woodGrad = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.rgb(232, 197, 138)), new Stop(1, Color.rgb(200, 165, 105)));
        gc.setFill(woodGrad);
        gc.fillRect(0, 0, w, h);

        gc.setStroke(lineColor);
        gc.setLineWidth(1.2);
        double m = padding * 0.3;
        gc.strokeRect(padding - m, padding - m, w - 2 * padding + 2 * m, h - 2 * padding + 2 * m);
        gc.setLineWidth(0.7);
        gc.strokeRect(padding - m * 0.5, padding - m * 0.5, w - 2 * padding + m, h - 2 * padding + m);

        for (int i = 0; i < 10; i++)
            gc.strokeLine(padding, padding + i * cellH, w - padding, padding + i * cellH);
        for (int i = 0; i < 9; i++) {
            if (i == 0 || i == 8)
                gc.strokeLine(padding + i * cellW, padding, padding + i * cellW, h - padding);
            else {
                gc.strokeLine(padding + i * cellW, padding, padding + i * cellW, padding + 4 * cellH);
                gc.strokeLine(padding + i * cellW, padding + 5 * cellH, padding + i * cellW, h - padding);
            }
        }

        gc.strokeLine(padding + 3 * cellW, padding, padding + 5 * cellW, padding + 2 * cellH);
        gc.strokeLine(padding + 5 * cellW, padding, padding + 3 * cellW, padding + 2 * cellH);
        gc.strokeLine(padding + 3 * cellW, h - padding, padding + 5 * cellW, h - padding - 2 * cellH);
        gc.strokeLine(padding + 5 * cellW, h - padding, padding + 3 * cellW, h - padding - 2 * cellH);

        int[][] starPos = {{1,2},{1,6},{2,1},{2,7},{7,1},{7,7},{8,2},{8,6}};
        for (int[] p : starPos) {
            double sx = padding + p[1] * cellW;
            double sy = padding + p[0] * cellH;
            double sl = cellW * 0.1;
            gc.strokeLine(sx - sl, sy, sx + sl, sy);
            gc.strokeLine(sx, sy - sl, sx, sy + sl);
        }

        gc.setFont(Font.font("楷体", cellH * 0.48));
        gc.setFill(Color.color(0.3, 0.2, 0.1, 0.55));
        double riverY = padding + 4.55 * cellH;
        gc.fillText("楚  河", padding + 1.2 * cellW, riverY);
        gc.fillText("漢  界", padding + 5.8 * cellW, riverY);

        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 9; c++) {
                char p = displayBoard.pieceAt(r, c);
                if (p == ' ') continue;
                double px = padding + c * cellW;
                double py = padding + r * cellH;
                boolean isRed = Piece.isRedChar(p);
                Color pc = isRed ? Color.rgb(190, 30, 10) : Color.rgb(25, 100, 110);

                gc.setFill(Color.gray(0, 0.15));
                gc.fillOval(px - pieceR + 2, py - pieceR + 2, pieceR * 2, pieceR * 2);

                var pg = new RadialGradient(0, 0, 0.3, 0.3, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.WHITE), new Stop(0.7, Color.rgb(245, 240, 230)),
                    new Stop(1, Color.rgb(220, 210, 190)));
                gc.setFill(pg);
                gc.fillOval(px - pieceR, py - pieceR, pieceR * 2, pieceR * 2);

                gc.setStroke(pc);
                gc.setLineWidth(2.5);
                gc.strokeOval(px - pieceR, py - pieceR, pieceR * 2, pieceR * 2);
                gc.setStroke(pc.deriveColor(0, 1, 0.7, 0.3));
                gc.setLineWidth(1);
                gc.strokeOval(px - pieceR * 0.78, py - pieceR * 0.78, pieceR * 1.56, pieceR * 1.56);

                gc.setFill(pc);
                gc.setFont(Font.font("楷体", cellH * 0.38));
                String name = switch (Character.toLowerCase(p)) {
                    case 'k' -> isRed ? "帅" : "将"; case 'a' -> isRed ? "仕" : "士";
                    case 'b' -> isRed ? "相" : "象"; case 'n' -> "马";
                    case 'r' -> "车"; case 'c' -> "炮";
                    case 'p' -> isRed ? "兵" : "卒"; default -> "?";
                };
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                gc.fillText(name, px, py);
            }
        }

        if (selectedRow >= 0) {
            double sx = padding + (isReverse ? (8 - selectedCol) : selectedCol) * cellW;
            double sy = padding + (isReverse ? (9 - selectedRow) : selectedRow) * cellH;
            gc.setStroke(Color.rgb(50, 140, 255));
            gc.setLineWidth(3);
            double r2 = pieceR + 4;
            gc.strokeRect(sx - r2, sy - r2, r2 * 2, r2 * 2);
        }

        if (lastMove != null) {
            for (int[] pos : new int[][]{
                {isReverse ? 9 - lastMove.fromRow() : lastMove.fromRow(),
                 isReverse ? 8 - lastMove.fromCol() : lastMove.fromCol()},
                {isReverse ? 9 - lastMove.toRow() : lastMove.toRow(),
                 isReverse ? 8 - lastMove.toCol() : lastMove.toCol()}
            }) {
                double sx = padding + pos[1] * cellW;
                double sy = padding + pos[0] * cellH;
                gc.setStroke(Color.rgb(200, 50, 50, 0.7));
                gc.setLineWidth(2.5);
                gc.strokeOval(sx - pieceR - 3, sy - pieceR - 3, (pieceR + 3) * 2, (pieceR + 3) * 2);
            }
        }
    }

    private void redrawBoard(Board board) {
        if (board == null) return;
        GraphicsContext gc = boardCanvas.getGraphicsContext2D();
        drawBoard(gc, board, boardCanvas.getWidth(), boardCanvas.getHeight());
    }

    private void updateWinRate() {
        winRateLabel.setText(WinRateCalculator.format(currentScore, currentIsRed));
    }

    private String formatTime(long ms) {
        if (ms < 1000) return ms + "ms";
        return String.format("%.1fs", ms / 1000.0);
    }
}
