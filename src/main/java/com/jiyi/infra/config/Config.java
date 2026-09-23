package com.jiyi.infra.config;

import com.jiyi.core.engine.EngineConfig;
import com.jiyi.infra.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 主配置类，包含应用程序的所有配置项
 * Main configuration class containing all application settings
 */
public class Config {
    private AppConfig app = new AppConfig();
    private BoardConfig board = new BoardConfig();
    private EngineConfigList engine = new EngineConfigList();
    private LinkConfig link = new LinkConfig();
    private BookConfig book = new BookConfig();
    private ManualConfig manual = new ManualConfig();

    public AppConfig app() { return app; }
    public void setApp(AppConfig app) { this.app = app; }
    public BoardConfig board() { return board; }
    public void setBoard(BoardConfig board) { this.board = board; }
    public EngineConfigList engine() { return engine; }
    public void setEngine(EngineConfigList engine) { this.engine = engine; }
    public LinkConfig link() { return link; }
    public void setLink(LinkConfig link) { this.link = link; }
    public BookConfig book() { return book; }
    public void setBook(BookConfig book) { this.book = book; }
    public ManualConfig manual() { return manual; }
    public void setManual(ManualConfig manual) { this.manual = manual; }

    /**
     * 验证配置的有效性
     * Validate configuration validity
     */
    public boolean validate() {
        return app.validate() && board.validate() && engine.validate()
            && link.validate() && book.validate() && manual.validate();
    }

    /**
     * 重置为默认配置
     * Reset to default configuration
     */
    public void resetToDefaults() {
        this.app = new AppConfig();
        this.board = new BoardConfig();
        this.engine = new EngineConfigList();
        this.link = new LinkConfig();
        this.book = new BookConfig();
        this.manual = new ManualConfig();
    }

    /**
     * 应用程序配置
     * Application configuration
     */
    public static class AppConfig {
        private String language = "zh-CN";
        private String theme = "default";
        private double windowWidth = 1169;
        private double windowHeight = 712;
        private double splitPosMain = 0.642;
        private boolean topWindow = false;

        public String language() { return language; }
        public void setLanguage(String v) { language = v; }
        public String theme() { return theme; }
        public void setTheme(String v) { theme = v; }
        public double windowWidth() { return windowWidth; }
        public void setWindowWidth(double v) { windowWidth = v; }
        public double windowHeight() { return windowHeight; }
        public void setWindowHeight(double v) { windowHeight = v; }
        public double splitPosMain() { return splitPosMain; }
        public void setSplitPosMain(double v) { splitPosMain = v; }
        public boolean topWindow() { return topWindow; }
        public void setTopWindow(boolean v) { topWindow = v; }

        public boolean validate() {
            return windowWidth > 0 && windowHeight > 0
                && splitPosMain >= 0 && splitPosMain <= 1
                && language != null && !language.isEmpty()
                && theme != null && !theme.isEmpty();
        }
    }

    /**
     * 棋盘配置
     * Board configuration
     */
    public static class BoardConfig {
        private String style = "default";
        private String size = "autofit";
        private boolean showNumbers = true;
        private boolean stepSound = true;
        private boolean stepTip = true;

        public String style() { return style; }
        public void setStyle(String v) { style = v; }
        public String size() { return size; }
        public void setSize(String v) { size = v; }
        public boolean showNumbers() { return showNumbers; }
        public void setShowNumbers(boolean v) { showNumbers = v; }
        public boolean stepSound() { return stepSound; }
        public void setStepSound(boolean v) { stepSound = v; }
        public boolean stepTip() { return stepTip; }
        public void setStepTip(boolean v) { stepTip = v; }

        public boolean validate() {
            return style != null && !style.isEmpty()
                && size != null && !size.isEmpty();
        }
    }

    /**
     * 引擎配置列表
     * Engine configuration list
     */
    public static class EngineConfigList {
        private String defaultEngine = "";
        private List<EngineConfig> list = new ArrayList<>();
        private String analysisModel = "FIXED_TIME";
        private long analysisValue = 5000;
        private int delayStartMs = 0;
        private int delayEndMs = 0;

        public String defaultEngine() { return defaultEngine; }
        public void setDefaultEngine(String v) { defaultEngine = v; }
        public List<EngineConfig> list() { return list; }
        public void setList(List<EngineConfig> v) { list = v; }
        public String analysisModel() { return analysisModel; }
        public void setAnalysisModel(String v) { analysisModel = v; }
        public long analysisValue() { return analysisValue; }
        public void setAnalysisValue(long v) { analysisValue = v; }
        public int delayStartMs() { return delayStartMs; }
        public void setDelayStartMs(int v) { delayStartMs = v; }
        public int delayEndMs() { return delayEndMs; }
        public void setDelayEndMs(int v) { delayEndMs = v; }

        /**
         * 根据名称查找引擎配置
         * Find engine configuration by name
         */
        public Optional<EngineConfig> findByName(String name) {
            return list.stream()
                .filter(e -> e.name() != null && e.name().equals(name))
                .findFirst();
        }

        /**
         * 获取默认引擎配置
         * Get default engine configuration
         */
        public Optional<EngineConfig> getDefaultEngineConfig() {
            if (defaultEngine == null || defaultEngine.isEmpty()) {
                return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
            }
            return findByName(defaultEngine);
        }

        /**
         * 添加引擎配置
         * Add engine configuration
         */
        public void addEngine(EngineConfig config) {
            if (config != null && config.name() != null) {
                list.add(config);
            }
        }

        /**
         * 移除引擎配置
         * Remove engine configuration
         */
        public boolean removeEngine(String name) {
            return list.removeIf(e -> e.name() != null && e.name().equals(name));
        }

        public boolean validate() {
            return analysisModel != null && !analysisModel.isEmpty()
                && analysisValue > 0
                && delayStartMs >= 0
                && delayEndMs >= 0
                && list != null;
        }
    }

    /**
     * 联机配置
     * Link/Connection configuration
     */
    public static class LinkConfig {
        private long scanIntervalMs = 100;
        // ★ 默认值与 config.json 实际值对齐：后台点击默认关（PostMessage 注入部分窗口不响应）、
        //   点击/移动延迟与文件一致（原 2/0ms 在删配置或重置后行为翻转：后台模式+超快连点）
        private boolean backMode = false;
        private boolean showInfo = true;
        private boolean animation = true;
        private int mouseClickDelayMs = 40;
        private int mouseMoveDelayMs = 20;
        /** 连线模式下引擎是否执红（默认执黑） */
        private boolean enginePlaysRed = false;
        /** 引擎执色：AUTO（首帧按外部行棋方自动判定）/ RED / BLACK（覆盖 enginePlaysRed） */
        private String engineColor = "AUTO";
        /** 稳定帧确认数：连续 N 帧识别相同才当作有效局面（对齐 C++ waitForStableBoard，对抗动画中间态/识别抖动） */
        private int stableConfirmCount = 2;
        /** YOLO 检测模型路径 */
        private String modelPath = "./models/yolov11.onnx";
        /** 截图方式：AUTO / PRINT_WINDOW / ROBOT（默认 ROBOT；AUTO 自动降级链 PrintWindow→Robot；BITBLT 已移除） */
        private String captureMethod = "ROBOT";
        /** 调试：保存识别截图到桌面（定位识别问题时开启） */
        private boolean saveScreenshot = false;
        /** 自动点击续盘（VinXiangQi AutoClick 方式）：独立循环截图找标定模板，找到即点击） */
        private boolean autoClick = false;
        /** 将死停止：检测到将死局面时停止自动点击续盘 */
        private boolean stopWhenMate = false;
        /** 续盘按钮模板目录（用户标定的"再来一局"按钮图存放处） */
        private String autoclickDir = "./autoclick";

        public boolean autoClick() { return autoClick; }
        public void setAutoClick(boolean v) { autoClick = v; }
        public boolean stopWhenMate() { return stopWhenMate; }
        public void setStopWhenMate(boolean v) { stopWhenMate = v; }
        public String autoclickDir() { return autoclickDir; }
        public void setAutoclickDir(String v) { autoclickDir = v; }

        public boolean saveScreenshot() { return saveScreenshot; }
        public void setSaveScreenshot(boolean v) { saveScreenshot = v; }

        public String engineColor() { return engineColor; }
        public void setEngineColor(String v) { engineColor = v; }
        public int stableConfirmCount() { return stableConfirmCount; }
        public void setStableConfirmCount(int v) { stableConfirmCount = Math.max(1, v); }

        public String captureMethod() { return captureMethod; }
        public void setCaptureMethod(String v) { captureMethod = v; }

        public String modelPath() { return modelPath; }
        public void setModelPath(String v) { modelPath = v; }

        public boolean enginePlaysRed() { return enginePlaysRed; }
        public void setEnginePlaysRed(boolean v) { enginePlaysRed = v; }

        public long scanIntervalMs() { return scanIntervalMs; }
        public void setScanIntervalMs(long v) { scanIntervalMs = v; }
        public boolean backMode() { return backMode; }
        public void setBackMode(boolean v) { backMode = v; }
        public boolean showInfo() { return showInfo; }
        public void setShowInfo(boolean v) { showInfo = v; }
        public boolean animation() { return animation; }
        public void setAnimation(boolean v) { animation = v; }
        public int mouseClickDelayMs() { return mouseClickDelayMs; }
        public void setMouseClickDelayMs(int v) { mouseClickDelayMs = v; }
        public int mouseMoveDelayMs() { return mouseMoveDelayMs; }
        public void setMouseMoveDelayMs(int v) { mouseMoveDelayMs = v; }

        public boolean validate() {
            return scanIntervalMs > 0
                && mouseClickDelayMs >= 0
                && mouseMoveDelayMs >= 0;
        }
    }

    /**
     * 开局库配置
     * Opening book configuration
     */
    public static class BookConfig {
        private List<String> files = new ArrayList<>();
        private boolean localFirst = true;
        private boolean cloudEnabled = true;
        private int cloudTimeoutMs = 2000;
        private boolean onlyCloudFinalPhase = false;
        private int offManualSteps = 9999;
        private String moveRule = "BEST_SCORE";
        private boolean bookSwitch = true;
        private int bookDelayStartMs = 0;
        private int bookDelayEndMs = 0;

        public int bookDelayStartMs() { return bookDelayStartMs; }
        public void setBookDelayStartMs(int v) { bookDelayStartMs = v; }
        public int bookDelayEndMs() { return bookDelayEndMs; }
        public void setBookDelayEndMs(int v) { bookDelayEndMs = v; }

        public List<String> files() { return files; }
        public void setFiles(List<String> v) { files = v; }
        public boolean localFirst() { return localFirst; }
        public void setLocalFirst(boolean v) { localFirst = v; }
        public boolean cloudEnabled() { return cloudEnabled; }
        public void setCloudEnabled(boolean v) { cloudEnabled = v; }
        public int cloudTimeoutMs() { return cloudTimeoutMs; }
        public void setCloudTimeoutMs(int v) { cloudTimeoutMs = v; }
        public boolean onlyCloudFinalPhase() { return onlyCloudFinalPhase; }
        public void setOnlyCloudFinalPhase(boolean v) { onlyCloudFinalPhase = v; }
        public int offManualSteps() { return offManualSteps; }
        public void setOffManualSteps(int v) { offManualSteps = v; }
        public String moveRule() { return moveRule; }
        public void setMoveRule(String v) { moveRule = v; }
        public boolean bookSwitch() { return bookSwitch; }
        public void setBookSwitch(boolean v) { bookSwitch = v; }

        /**
         * 添加开局库文件
         * Add opening book file
         */
        public void addBookFile(String filePath) {
            if (filePath != null && !filePath.isEmpty() && !files.contains(filePath)) {
                files.add(filePath);
            }
        }

        /**
         * 移除开局库文件
         * Remove opening book file
         */
        public boolean removeBookFile(String filePath) {
            return files.remove(filePath);
        }

        /**
         * 清空所有开局库文件
         * Clear all opening book files
         */
        public void clearBookFiles() {
            files.clear();
        }

        public boolean validate() {
            return files != null
                && cloudTimeoutMs > 0
                && offManualSteps >= 0
                && moveRule != null && !moveRule.isEmpty();
        }
    }

    /**
     * 棋谱配置
     * Manual/Game record configuration
     */
    public static class ManualConfig {
        private String path = "./manuals";
        private boolean showChessNotation = false;
        private boolean manualTip = true;

        public String path() { return path; }
        public void setPath(String v) { path = v; }
        public boolean showChessNotation() { return showChessNotation; }
        public void setShowChessNotation(boolean v) { showChessNotation = v; }
        public boolean manualTip() { return manualTip; }
        public void setManualTip(boolean v) { manualTip = v; }

        public boolean validate() {
            return path != null && !path.isEmpty();
        }
    }
}
