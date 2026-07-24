package com.jiyi.core.engine;

import java.util.HashMap;
import java.util.Map;

public class EngineConfig {
    private String name;
    private String path;
    private int threads = 1;
    private int hash = 256;
    private boolean ponder = false;
    private Map<String, String> customOptions = new HashMap<>();

    public EngineConfig() {}

    public EngineConfig(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public String path() { return path; }
    public void setPath(String path) { this.path = path; }
    public int threads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }
    public int hash() { return hash; }
    public void setHash(int hash) { this.hash = hash; }
    public boolean ponder() { return ponder; }
    public void setPonder(boolean ponder) { this.ponder = ponder; }
    public Map<String, String> customOptions() { return customOptions; }
    public void setCustomOptions(Map<String, String> customOptions) { this.customOptions = customOptions; }
}
