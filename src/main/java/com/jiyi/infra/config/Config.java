package com.jiyi.infra.config;

import com.jiyi.core.engine.EngineConfig;
import com.jiyi.infra.platform.Platform;

import java.util.ArrayList;
import java.util.List;

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
    }

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
    }

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
    }

    public static class LinkConfig {
        private long scanIntervalMs = 100;
        private boolean backMode = true;
        private boolean showInfo = true;
        private boolean animation = true;
        private int mouseClickDelayMs = 2;
        private int mouseMoveDelayMs = 0;

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
    }

    public static class BookConfig {
        private List<String> files = new ArrayList<>();
        private boolean localFirst = true;
        private boolean cloudEnabled = true;
        private int cloudTimeoutMs = 2000;
        private boolean onlyCloudFinalPhase = false;
        private int offManualSteps = 9999;
        private String moveRule = "BEST_SCORE";
        private boolean bookSwitch = true;

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
    }

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
    }
}
