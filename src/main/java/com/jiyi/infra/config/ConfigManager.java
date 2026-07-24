package com.jiyi.infra.config;

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
        mapper.registerModule(new Jdk8Module());
        mapper.writerWithDefaultPrettyPrinter();
    }

    public Config load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                config = mapper.readValue(CONFIG_PATH.toFile(), Config.class);
                log.info("Config loaded from {}", CONFIG_PATH.toAbsolutePath());
                return config;
            } catch (IOException e) {
                log.warn("Failed to load config, using defaults", e);
            }
        }
        config = new Config();
        save();
        return config;
    }

    public void save() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(CONFIG_PATH.toFile(), config);
        } catch (IOException e) {
            log.error("Failed to save config", e);
        }
    }

    public Config get() { return config; }
}
