package com.jiyi.infra.config;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final Path CONFIG_PATH = Path.of("config.json");
    private final ObjectMapper mapper;
    private Config config;

    public ConfigManager() {
        mapper = new ObjectMapper();
        // 旧版 config.json 含未知字段时不崩（B29）
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        mapper.setVisibility(PropertyAccessor.GETTER, Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.SETTER, Visibility.NONE);
        mapper.registerModule(new Jdk8Module());
    }

    public Config load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                config = mapper.readValue(CONFIG_PATH.toFile(), Config.class);
                log.info("Config loaded from {}", CONFIG_PATH.toAbsolutePath());
                return config;
            } catch (IOException e) {
                // 解析失败：备份损坏文件后用默认值，不再静默覆写（B81）
                log.warn("Failed to load config, backing up and using defaults", e);
                backupCorruptFile();
            }
        }
        config = new Config();
        save();
        return config;
    }

    private void backupCorruptFile() {
        try {
            var bak = CONFIG_PATH.resolveSibling(
                "config.json.bak-" + java.time.Instant.now().toString().replace(':', '-').substring(0, 19));
            Files.copy(CONFIG_PATH, bak);
            log.warn("Corrupt config backed up to {}", bak.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to backup corrupt config", e);
        }
    }

    public void save() {
        if (config == null) {
            log.warn("Cannot save null config");
            return;
        }
        try {
            // 原子写：先写临时文件再替换（B81）
            Path tmp = CONFIG_PATH.resolveSibling("config.json.tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), config);
            Files.move(tmp, CONFIG_PATH,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            log.info("Config saved to {}", CONFIG_PATH.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save config to {}", CONFIG_PATH.toAbsolutePath(), e);
        }
    }

    /** 单线程写盘 executor（B15：saveAsync 与 stop 的 save 不再并发写坏文件） */
    private final java.util.concurrent.ExecutorService saveExecutor =
        java.util.concurrent.Executors.newSingleThreadExecutor();

    public void saveAsync() {
        saveExecutor.execute(this::save);
    }

    public boolean reload() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                config = mapper.readValue(CONFIG_PATH.toFile(), Config.class);
                log.info("Config reloaded from {}", CONFIG_PATH.toAbsolutePath());
                return true;
            } else {
                log.warn("Config file does not exist: {}", CONFIG_PATH.toAbsolutePath());
                return false;
            }
        } catch (IOException e) {
            log.error("Failed to reload config", e);
            return false;
        }
    }

    public void reset() {
        config = new Config();
        save();
        log.info("Config reset to defaults");
    }

    public Path getConfigPath() {
        return CONFIG_PATH.toAbsolutePath();
    }

    public Config get() { return config; }
}
