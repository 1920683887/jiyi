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
import com.jiyi.infra.platform.WindowsPlatform;
import com.jiyi.service.AutomationService;
import com.jiyi.service.BookService;
import com.jiyi.service.DetectionService;
import com.jiyi.service.ManualService;
import com.jiyi.ui.component.BoardContextMenu;
import com.jiyi.ui.component.TrendChartView;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
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

    public static class MoveRow {
        private final javafx.beans.property.IntegerProperty num = new javafx.beans.property.SimpleIntegerProperty();
        private final javafx.beans.property.StringProperty move = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty score = new javafx.beans.property.SimpleStringProperty();
        public MoveRow(int num, String move, String score) {
            this.num.set(num); this.move.set(move); this.score.set(score);
        }
        public int getNum() { return num.get(); }
        public String getMove() { return move.get(); }
        public String getScore() { return score.get(); }
        public javafx.beans.property.IntegerProperty numProperty() { return num; }
        public javafx.beans.property.StringProperty moveProperty() { return move; }
        public javafx.beans.property.StringProperty scoreProperty() { return score; }
    }

    public static class BookRow {
        private final javafx.beans.property.StringProperty move = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty score = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty winRate = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty win = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty draw = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty lose = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty remark = new javafx.beans.property.SimpleStringProperty();
        private final javafx.beans.property.StringProperty source = new javafx.beans.property.SimpleStringProperty();
        public BookRow(String move, String score, String winRate, String win, String draw, String lose, String remark, String source) {
            this.move.set(move); this.score.set(score); this.winRate.set(winRate);
            this.win.set(win); this.draw.set(draw); this.lose.set(lose);
            this.remark.set(remark); this.source.set(source);
        }
        public String getMove() { return move.get(); }
        public String getScore() { return score.get(); }
        public String getWinRate() { return winRate.get(); }
        public String getWin() { return win.get(); }
        public String getDraw() { return draw.get(); }
        public String getLose() { return lose.get(); }
        public String getRemark() { return remark.get(); }
        public String getSource() { return source.get(); }
        public javafx.beans.property.StringProperty moveProperty() { return move; }
        public javafx.beans.property.StringProperty scoreProperty() { return score; }
        public javafx.beans.property.StringProperty winRateProperty() { return winRate; }
        public javafx.beans.property.StringProperty winProperty() { return win; }
        public javafx.beans.property.StringProperty drawProperty() { return draw; }
        public javafx.beans.property.StringProperty loseProperty() { return lose; }
        public javafx.beans.property.StringProperty remarkProperty() { return remark; }
        public javafx.beans.property.StringProperty sourceProperty() { return source; }
    }

    @Inject private EventBus eventBus;
    @Inject private GameService gameService;
    @Inject private EngineService engineService;
    @Inject private ManualService manualService;
    @Inject private BookService bookService;
    @Inject private DetectionService detectionService;
    @Inject private AutomationService automationService;
    @Inject private Config config;
    @Inject private ConfigManager configManager;

    @FXML private Canvas boardCanvas;
    @FXML private ListView<String> engineOutput;
    @FXML private TableView<MoveRow> recordTable;
    @FXML private TableColumn<MoveRow, Integer> numCol;
    @FXML private TableColumn<MoveRow, String> moveCol;
    @FXML private TableColumn<MoveRow, String> scoreCol;
    @FXML private TextArea remarkText;
    @FXML private ComboBox<String> engineCombo;
    @FXML private ComboBox<Integer> threadCombo;
    @FXML private ComboBox<Integer> hashCombo;
    @FXML private ComboBox<String> analysisModelCombo;
    @FXML private TextField analysisValueField;
    @FXML private Button tag1Btn;
    @FXML private Button tag2Btn;
    @FXML private Button tag3Btn;
    @FXML private Button tag4Btn;
    @FXML private Button playBtn;
    @FXML private Button pauseBtn;
    @FXML private Label manualInfoLabel;
    @FXML private TableView<BookRow> bookTable;
    @FXML private TableColumn<MainController.BookRow, String> bookMoveCol;
    @FXML private TableColumn<MainController.BookRow, String> bookScoreCol;
    @FXML private TableColumn<MainController.BookRow, String> bookWinRateCol;
    @FXML private TableColumn<MainController.BookRow, String> bookWinCol;
    @FXML private TableColumn<MainController.BookRow, String> bookDrawCol;
    @FXML private TableColumn<MainController.BookRow, String> bookLoseCol;
    @FXML private TableColumn<MainController.BookRow, String> bookRemarkCol;
    @FXML private TableColumn<MainController.BookRow, String> bookSourceCol;
    @FXML private Label statusLabel;
    @FXML private Label turnLabel;
    @FXML private Label winRateLabel;
    @FXML private BorderPane root;
    @FXML private Button engineRedButton;
    @FXML private Button engineBlackButton;
    @FXML private Button analysisButton;
    @FXML private Button flipButton;
    @FXML private Button linkWindowBtn;
    @FXML private ComboBox<String> linkModeCombo;
    @FXML private Button linkStartBtn;
    @FXML private Button linkStopBtn;
    @FXML private Label linkStatusLabel;
    @FXML private AnchorPane trendChartPane;
    @FXML private Button immediateMoveButton;
    @FXML private Button alternativeMoveButton;
    @FXML private ToggleButton bookSwitchToggle;
    @FXML private ListView<String> variationList;
    @FXML private RadioMenuItem boardSizeLarge;
    @FXML private RadioMenuItem boardSizeMedium;
    @FXML private RadioMenuItem boardSizeSmall;
    @FXML private RadioMenuItem boardSizeAuto;
    @FXML private ToggleGroup boardSizeGroup;
    @FXML private CheckMenuItem topWindowMenuItem;
    @FXML private CheckMenuItem stepNumbersMenuItem;

    private volatile long linkWindowHwnd;
    private TrendChartView trendChartView;
    private Stage stage;
    private int selectedRow = -1, selectedCol = -1;
    private Move lastMove;
    private boolean isReverse = false;
    private int currentScore;
    private boolean currentIsRed;
    private volatile boolean engineThinking;
    private String engineSide = ""; // "red", "black", ""
    private boolean initialized;
    private boolean showStepNumbers = false;

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
            if (trendChartView != null) {
                trendChartView.clear();
            }
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(GameEvent.BoardChanged.class, e -> {
            redrawBoard(e.board());
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(GameEvent.UndoExecuted.class, e -> {
            redrawBoard(e.board());
            recordTable.getItems().remove(recordTable.getItems().size() - 1);
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(GameEvent.SideSwitched.class, e -> {
            turnLabel.setText(e.redToGo() ? "红方走棋" : "黑方走棋");
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
            // 更新趋势图
            if (trendChartView != null && recordTable.getItems().size() > 0) {
                trendChartView.addScore(recordTable.getItems().size(), d.score(), currentIsRed);
            }
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(EngineEvent.EngineStarted.class, e -> {
            statusLabel.setText("引擎已启动: " + e.name());
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(EngineEvent.EngineStopped.class, e -> {
            engineThinking = false;
            statusLabel.setText("引擎已停止: " + e.name());
        }, EventBus.Dispatch.PLATFORM);
        eventBus.register(EngineEvent.BestMove.class, e -> {
            engineThinking = false;
            if (e.move() != null && isEngineTurn()) {
                gameService.executeMove(e.move());
            }
        }, EventBus.Dispatch.PLATFORM);

        threadCombo.getItems().addAll(1, 2, 4, 8, 16);
        threadCombo.setValue(4);
        hashCombo.getItems().addAll(16, 32, 64, 128, 256, 512, 1024, 2048, 4096);
        hashCombo.setValue(256);

        // 线程/哈希变更时实时更新引擎配置
        threadCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val == null) return;
            var cfg = findSelectedEngine();
            if (cfg != null && engineService.isRunning()) {
                cfg.setThreads(val);
                engineService.applyOptions();
            }
        });
        hashCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val == null) return;
            var cfg = findSelectedEngine();
            if (cfg != null && engineService.isRunning()) {
                cfg.setHash(val);
                engineService.applyOptions();
            }
        });
        numCol.setCellValueFactory(cellData -> cellData.getValue().numProperty().asObject());
        moveCol.setCellValueFactory(cellData -> cellData.getValue().moveProperty());
        scoreCol.setCellValueFactory(cellData -> cellData.getValue().scoreProperty());

        // 库招表格列绑定
        bookTable.getItems().clear();
        bookMoveCol.setCellValueFactory(cd -> cd.getValue().moveProperty());
        bookScoreCol.setCellValueFactory(cd -> cd.getValue().scoreProperty());
        bookWinRateCol.setCellValueFactory(cd -> cd.getValue().winRateProperty());
        bookWinCol.setCellValueFactory(cd -> cd.getValue().winProperty());
        bookDrawCol.setCellValueFactory(cd -> cd.getValue().drawProperty());
        bookLoseCol.setCellValueFactory(cd -> cd.getValue().loseProperty());
        bookRemarkCol.setCellValueFactory(cd -> cd.getValue().remarkProperty());
        bookSourceCol.setCellValueFactory(cd -> cd.getValue().sourceProperty());

        // 开局库开关初始化
        bookSwitchToggle.setSelected(config.book().bookSwitch());

        // 棋盘大小初始化
        initBoardSizeMenu();

        analysisModelCombo.getItems().addAll("固定时间", "固定深度", "无限");
        analysisModelCombo.setValue("固定时间");
        analysisValueField.setText("5000");

        // 连线模式选择
        linkModeCombo.getItems().addAll("自动走棋", "观战模式");
        linkModeCombo.setValue("自动走棋");

        engineCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, name) -> {
            if (name == null || name.equals("(无引擎)")) return;
            config.engine().list().stream()
                .filter(e -> e.name().equals(name)).findFirst()
                .ifPresent(this::loadEngineParams);
        });
        refreshEngineList();

        gameService.startNewGame();
        manualService.startNewRecord();
        setupTagButtons();
        setupVariationList();
        initializeTrendChart();
        setupContextMenu();
    }

    public void initialize(Stage stage) {
        this.stage = stage;
        initAccelerators(stage);
        // 窗口置顶：从配置读取并应用
        boolean topWindow = config.app().topWindow();
        stage.setAlwaysOnTop(topWindow);
        if (topWindowMenuItem != null) topWindowMenuItem.setSelected(topWindow);
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

    private void loadEngineParams(EngineConfig cfg) {
        String model = cfg.analysisModel();
        if ("FIXED_TIME".equals(model)) analysisModelCombo.setValue("固定时间");
        else if ("FIXED_STEPS".equals(model)) analysisModelCombo.setValue("固定深度");
        else analysisModelCombo.setValue("无限");
        analysisValueField.setText(String.valueOf(cfg.analysisValue()));
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
        gameService.startNewGame();
        engineSide = "red";
        if (!startEngine(cfg)) return;
        if (gameService.isRedToGo()) {
            engineThinking = true;
            engineService.analyze(gameService.getCurrentBoard(), true);
        }
        engineRedButton.setText("引擎红 ✓");
        engineBlackButton.setText("引擎黑");
        statusLabel.setText("引擎执红");
    }

    @FXML
    public void onEngineBlack() {
        var cfg = findSelectedEngine();
        if (cfg == null) { statusLabel.setText("请先添加引擎"); return; }
        gameService.startNewGame();
        engineSide = "black";
        if (!startEngine(cfg)) return;
        if (!gameService.isRedToGo()) {
            engineThinking = true;
            engineService.analyze(gameService.getCurrentBoard(), false);
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
            engineRedButton.setDisable(false);
            engineBlackButton.setDisable(false);
            immediateMoveButton.setDisable(false);
        } else {
            engineSide = "all";
            if (!startEngine(cfg)) return;
            engineThinking = true;
            engineService.analyze(gameService.getCurrentBoard(), gameService.isRedToGo());
            analysisButton.setText("停止");
            engineRedButton.setDisable(true);
            engineBlackButton.setDisable(true);
            immediateMoveButton.setDisable(true);
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
    public void onLinkSelectWindow() {
        log.info("onLinkSelectWindow called");
        linkStatusLabel.setText("请点击目标窗口...");

        try {
            var platform = com.jiyi.di.AppModule.getInjector()
                .getInstance(com.jiyi.infra.platform.Platform.class);
            log.info("Platform instance obtained: {}", platform.getClass().getName());

            if (!(platform instanceof WindowsPlatform)) {
                log.error("Platform is not WindowsPlatform: {}", platform.getClass().getName());
                linkStatusLabel.setText("仅支持 Windows 平台");
                return;
            }

            var wp = (WindowsPlatform) platform;
            log.info("Starting window selection via WindowsPlatform");

            wp.startWindowSelection(hwnd -> {
                log.info("Window selection callback invoked with hwnd: 0x{}", Long.toHexString(hwnd));
                linkWindowHwnd = hwnd;

                // 必须在 JavaFX 线程中更新 UI
                javafx.application.Platform.runLater(() -> {
                    log.info("Updating UI on JavaFX thread");
                    linkStatusLabel.setText("已选窗口: 0x" + Long.toHexString(hwnd));
                    linkStartBtn.setDisable(false);
                    linkWindowBtn.setDisable(true);
                    statusLabel.setText("窗口选择成功");
                    log.info("UI updated successfully");
                });
            });

            log.info("Window selection initiated");
        } catch (Exception e) {
            log.error("Failed to start window selection", e);
            linkStatusLabel.setText("窗口选择失败: " + e.getMessage());
            statusLabel.setText("错误: " + e.getMessage());
        }
    }

    @FXML
    public void onLinkStart() {
        if (linkWindowHwnd == 0) return;
        if (!detectionService.start(linkWindowHwnd)) {
            linkStatusLabel.setText("检测启动失败（模型未加载）");
            return;
        }
        boolean isSpectator = "观战模式".equals(linkModeCombo.getValue());
        automationService.start(false, isSpectator);
        linkStartBtn.setDisable(true);
        linkStopBtn.setDisable(false);
        linkWindowBtn.setDisable(true);
        // 禁用引擎模式切换按钮，防止连线时切换模式
        engineRedButton.setDisable(true);
        engineBlackButton.setDisable(true);
        analysisButton.setDisable(true);
        linkStatusLabel.setText(isSpectator ? "观战模式..." : "连线中...");
        statusLabel.setText(isSpectator ? "观战模式已启动" : "连线模式已启动");
    }

    @FXML
    public void onLinkStop() {
        detectionService.stop();
        automationService.stop();
        linkStartBtn.setDisable(false);
        linkStopBtn.setDisable(true);
        linkWindowBtn.setDisable(false);
        // 恢复引擎按钮状态
        engineRedButton.setDisable(false);
        engineBlackButton.setDisable(false);
        analysisButton.setDisable(false);
        linkStatusLabel.setText("连线已停止");
        statusLabel.setText("");
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

    private void setupVariationList() {
        if (variationList == null) return;
        variationList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                int selIdx = variationList.getSelectionModel().getSelectedIndex();
                if (selIdx < 0) return;
                int moveIdx = manualService.currentIndex();
                var variations = manualService.getVariations(moveIdx);
                if (selIdx < variations.size()) {
                    manualService.navigateToVariation(moveIdx, selIdx);
                    // 刷新棋盘
                    Board b = manualService.getCurrentBoard();
                    eventBus.post(new GameEvent.BoardChanged(b));
                    redrawBoard(b);
                    updateManualInfo();
                    updateVariationList();
                    statusLabel.setText("已切换到变招 " + (selIdx + 1));
                }
            }
        });
    }

    private void updateVariationList() {
        if (variationList == null) return;
        variationList.getItems().clear();
        int moveIdx = manualService.currentIndex();
        var variations = manualService.getVariations(moveIdx);
        for (int i = 0; i < variations.size(); i++) {
            var v = variations.get(i);
            String display = (i + 1) + ". " + v.moveUci();
            if (!v.chineseMove().isEmpty()) {
                display += " (" + v.chineseMove() + ")";
            }
            if (!v.remark().isEmpty()) {
                display += " " + v.remark();
            }
            variationList.getItems().add(display);
        }
    }

    @FXML
    public void onBookSwitch() {
        boolean enabled = bookSwitchToggle.isSelected();
        config.book().setBookSwitch(enabled);
        configManager.saveAsync();
        statusLabel.setText(enabled ? "开局库已开启" : "开局库已关闭");
    }

    @FXML
    public void onBoardSizeSelected() {
        RadioMenuItem selected = (RadioMenuItem) boardSizeGroup.getSelectedToggle();
        if (selected == null) return;
        String size;
        if (selected == boardSizeLarge) size = "large";
        else if (selected == boardSizeMedium) size = "medium";
        else if (selected == boardSizeSmall) size = "small";
        else size = "autofit";

        config.board().setSize(size);
        configManager.saveAsync();

        applyBoardSize(size);
        statusLabel.setText("棋盘大小: " + selected.getText());
    }

    private void initBoardSizeMenu() {
        String size = config.board().size();
        switch (size) {
            case "large" -> boardSizeLarge.setSelected(true);
            case "medium" -> boardSizeMedium.setSelected(true);
            case "small" -> boardSizeSmall.setSelected(true);
            default -> boardSizeAuto.setSelected(true);
        }
        applyBoardSize(size);
    }

    private void applyBoardSize(String size) {
        double w, h;
        switch (size) {
            case "large"  -> { w = 700.0; h = 760.0; }
            case "medium" -> { w = 600.0; h = 650.0; }
            case "small"  -> { w = 480.0; h = 520.0; }
            default -> {
                // 自适应：根据父容器宽度计算
                var parent = boardCanvas.getParent();
                if (parent != null && parent.getLayoutBounds().getWidth() > 0) {
                    double pw = parent.getLayoutBounds().getWidth() - 10;
                    w = Math.max(400.0, Math.min(pw, 700.0));
                } else {
                    w = 600.0;
                }
                h = w * 650.0 / 600.0;
            }
        }
        boardCanvas.setWidth(w);
        boardCanvas.setHeight(h);
        redrawBoard(gameService.getCurrentBoard());
    }

    private void initializeTrendChart() {
        if (trendChartPane == null) return;
        trendChartView = new TrendChartView();
        var chart = trendChartView.getChart();
        AnchorPane.setTopAnchor(chart, 0.0);
        AnchorPane.setBottomAnchor(chart, 0.0);
        AnchorPane.setLeftAnchor(chart, 0.0);
        AnchorPane.setRightAnchor(chart, 0.0);
        trendChartPane.getChildren().add(chart);

        // 设置点击事件，双击趋势图数据点可以跳转到对应步数
        trendChartView.setOnPointClicked(moveNum -> {
            manualNavigateTo(moveNum - 1);
        });
    }

    private void setupContextMenu() {
        if (boardCanvas == null) return;
        boardCanvas.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                showContextMenu(event);
            } else if (event.getButton() == MouseButton.PRIMARY) {
                onCanvasClicked(event);
            }
        });
    }

    private void showContextMenu(MouseEvent event) {
        var contextMenu = new BoardContextMenu(
            gameService.getCurrentBoard(),
            gameService.isRedToGo(),
            v -> onEditBoard(),
            v -> flipBoard(),
            v -> onAlternativeMove(),
            v -> onManualDelete(),
            v -> exportImage(),
            v -> copyImage()
        );
        contextMenu.show(boardCanvas, event.getScreenX(), event.getScreenY());
    }

    @FXML
    public void onTimeSettings() {
        try {
            var loader = new FXMLLoader(getClass().getResource("/fxml/time_settings.fxml"));
            loader.setControllerFactory(com.jiyi.di.AppModule.getInjector()::getInstance);
            var scene = new Scene(loader.load());
            var win = new Stage();
            win.setTitle("时间设置");
            win.setScene(scene);
            win.initModality(Modality.WINDOW_MODAL);
            win.initOwner(stage);
            win.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open time settings", e);
            statusLabel.setText("打开时间设置失败");
        }
    }

    @FXML
    public void onLinkSettings() {
        try {
            var loader = new FXMLLoader(getClass().getResource("/fxml/link_settings.fxml"));
            loader.setControllerFactory(com.jiyi.di.AppModule.getInjector()::getInstance);
            var scene = new Scene(loader.load());
            var win = new Stage();
            win.setTitle("连线设置");
            win.setScene(scene);
            win.initModality(Modality.WINDOW_MODAL);
            win.initOwner(stage);
            win.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open link settings", e);
            statusLabel.setText("打开连线设置失败");
        }
    }

    @FXML
    public void onBookSettings() {
        try {
            var loader = new FXMLLoader(getClass().getResource("/fxml/book_settings.fxml"));
            loader.setControllerFactory(com.jiyi.di.AppModule.getInjector()::getInstance);
            var scene = new Scene(loader.load());
            var win = new Stage();
            win.setTitle("开局库设置");
            win.setScene(scene);
            win.initModality(Modality.WINDOW_MODAL);
            win.initOwner(stage);
            win.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open book settings", e);
            statusLabel.setText("打开开局库设置失败");
        }
    }

    @FXML
    public void onEditBoard() {
        try {
            var loader = new FXMLLoader(getClass().getResource("/fxml/edit_board.fxml"));
            loader.setControllerFactory(com.jiyi.di.AppModule.getInjector()::getInstance);
            var scene = new Scene(loader.load());
            var win = new Stage();
            win.setTitle("编辑局面");
            win.setScene(scene);
            win.initModality(Modality.WINDOW_MODAL);
            win.initOwner(stage);

            // 获取控制器并传递当前局面
            var controller = (com.jiyi.ui.controller.EditBoardController) loader.getController();
            controller.setBoard(gameService.getCurrentBoard(), gameService.isRedToGo());

            win.showAndWait();

            // 如果用户确认了修改，更新当前局面
            if (controller.isConfirmed()) {
                gameService.loadFen(controller.getFen());
                statusLabel.setText("局面已更新");
                if (trendChartView != null) {
                    trendChartView.clear();
                }
            }
        } catch (Exception e) {
            log.error("Failed to open edit board", e);
            statusLabel.setText("打开编辑局面失败");
        }
    }

    private boolean startEngine(EngineConfig cfg) {
        if (engineService.isRunning()) engineService.stopEngine();
        cfg.setThreads(threadCombo.getValue());
        cfg.setHash(hashCombo.getValue());
        String model = analysisModelCombo.getValue();
        cfg.setAnalysisModel(switch (model) {
            case "固定时间" -> "FIXED_TIME";
            case "固定深度" -> "FIXED_STEPS";
            default -> "INFINITE";
        });
        try {
            cfg.setAnalysisValue(Long.parseLong(analysisValueField.getText()));
        } catch (NumberFormatException ignored) {}
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
        if (stage != null) {
            config.app().setTopWindow(stage.isAlwaysOnTop());
            configManager.saveAsync();
            stage.close();
        }
    }

    @FXML
    public void onToggleTopWindow() {
        boolean selected = topWindowMenuItem.isSelected();
        config.app().setTopWindow(selected);
        if (stage != null) stage.setAlwaysOnTop(selected);
        configManager.saveAsync();
    }

    @FXML
    public void onToggleStepNumbers() {
        showStepNumbers = stepNumbersMenuItem.isSelected();
        redrawBoard(gameService.getCurrentBoard());
    }

    @FXML
    public void onEditManualInfo() {
        var record = manualService.getRecord();

        var dialog = new Dialog<String>();
        dialog.setTitle("编辑棋谱信息");
        dialog.setHeaderText("编辑赛事元数据");

        var btnType = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnType, ButtonType.CANCEL);

        var grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 40, 10, 10));

        var eventField = new TextField(record.eventName());
        var siteField = new TextField(record.site());
        var dateField = new TextField(record.date());
        var redField = new TextField(record.redPlayer());
        var blackField = new TextField(record.blackPlayer());

        grid.add(new Label("赛事名称:"), 0, 0);
        grid.add(eventField, 1, 0);
        grid.add(new Label("城市/地点:"), 0, 1);
        grid.add(siteField, 1, 1);
        grid.add(new Label("日期:"), 0, 2);
        grid.add(dateField, 1, 2);
        grid.add(new Label("红方:"), 0, 3);
        grid.add(redField, 1, 3);
        grid.add(new Label("黑方:"), 0, 4);
        grid.add(blackField, 1, 4);

        eventField.setPrefWidth(250);
        siteField.setPrefWidth(250);
        dateField.setPrefWidth(250);
        redField.setPrefWidth(250);
        blackField.setPrefWidth(250);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> btn == btnType ? "ok" : null);

        dialog.showAndWait().ifPresent(result -> {
            manualService.setMetadata(
                eventField.getText(), siteField.getText(),
                redField.getText(), blackField.getText(), "");
            manualService.getRecord().setDate(dateField.getText());
            statusLabel.setText("棋谱信息已更新");
        });
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
                gameService.loadFen(fen);
                statusLabel.setText("已加载局面");
            } catch (Exception e) {
                statusLabel.setText("无效的FEN");
            }
        }
    }

    @FXML
    public void onImmediateMove() {
        if (!engineService.isRunning()) {
            statusLabel.setText("引擎未启动");
            return;
        }
        engineService.stopThinkingAndMove();
        statusLabel.setText("立即出招");
    }

    @FXML
    public void onAlternativeMove() {
        if (!engineService.isRunning()) {
            statusLabel.setText("引擎未启动");
            return;
        }
        // 排除当前最佳着法，分析次优着法
        Move lastMove = engineService.getLastBestMove();
        if (lastMove != null) {
            engineService.analyzeWithExcludedMoves(
                gameService.getCurrentBoard(),
                gameService.isRedToGo(),
                java.util.List.of(lastMove)
            );
            statusLabel.setText("分析变招中（排除 " + lastMove.toUci() + "）");
        } else {
            // 没有最佳着法记录，使用标准分析
            engineService.analyze(gameService.getCurrentBoard(), gameService.isRedToGo());
            statusLabel.setText("分析中...");
        }
    }

    @FXML
    public void exportImage() {
        var fc = new javafx.stage.FileChooser();
        fc.setTitle("导出图片");
        fc.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("PNG图片", "*.png")
        );
        fc.setInitialFileName("棋盘.png");
        var file = fc.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;

        boolean success = com.jiyi.infra.util.BoardImageExporter.exportToPng(boardCanvas, file);
        if (success) {
            statusLabel.setText("已导出: " + file.getName());
        } else {
            statusLabel.setText("导出失败");
        }
    }

    @FXML
    public void copyImage() {
        boolean success = com.jiyi.infra.util.BoardImageExporter.copyToClipboard(boardCanvas);
        if (success) {
            statusLabel.setText("图片已复制到剪贴板");
        } else {
            statusLabel.setText("复制失败");
        }
    }

    @FXML
    public void onCanvasClicked(MouseEvent e) {
        if (engineThinking) return;
        Board board = gameService.getCurrentBoard();
        if (board == null) return;
        int[] grid = screenToBoard(e.getX(), e.getY());
        if (grid == null) return;
        int row = grid[0], col = grid[1];
        if (isReverse) { row = 9 - row; col = 8 - col; }

        // 添加反转后的边界检查
        if (row < 0 || row > 9 || col < 0 || col > 8) {
            log.warn("Invalid position after reverse: ({}, {})", row, col);
            return;
        }

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
                redrawBoard(gameService.getCurrentBoard());
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
        int moveNum = recordTable.getItems().size() + 1;
        recordTable.getItems().add(new MoveRow(moveNum, event.move().toUci(), ""));
        recordTable.scrollTo(recordTable.getItems().size() - 1);
        turnLabel.setText(event.isRed() ? "黑方走棋" : "红方走棋");
        // 更新变招列表
        updateVariationList();
        if (engineService.isRunning() && isEngineTurn()) {
            engineThinking = true;
            engineService.analyze(event.board(), gameService.isRedToGo());
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

        // 步数提示
        if (showStepNumbers) {
            int stepCount = recordTable.getItems().size();
            gc.setFill(Color.rgb(0, 0, 0, 0.6));
            gc.setFont(Font.font("Consolas", cellH * 0.35));
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.setTextBaseline(VPos.BOTTOM);
            gc.fillText("第 " + stepCount + " 步", w - padding, h - padding);
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
