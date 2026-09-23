package com.jiyi.di;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.jiyi.core.book.BookSelector;
import com.jiyi.core.book.ZobristHasher;
import com.jiyi.core.detection.AutoClicker;
import com.jiyi.core.detection.BoardComparator;
import com.jiyi.core.detection.BoardMatcher;
import com.jiyi.core.event.EventBus;
import com.jiyi.core.rule.CheckDetector;
import com.jiyi.core.rule.ChineseTranslator;
import com.jiyi.core.rule.MateDetector;
import com.jiyi.core.rule.MoveGenerator;
import com.jiyi.core.rule.MoveValidator;
import com.jiyi.infra.config.Config;
import com.jiyi.infra.config.ConfigManager;
import com.jiyi.infra.platform.Platform;
import com.jiyi.infra.platform.WindowsPlatform;
import com.jiyi.infra.util.SoundPlayer;
import com.jiyi.infra.util.WinRateCalculator;
import com.jiyi.service.AutomationService;
import com.jiyi.service.BookService;
import com.jiyi.service.DetectionService;
import com.jiyi.service.EngineService;
import com.jiyi.service.GameService;
import com.jiyi.service.ManualService;

public class AppModule extends AbstractModule {

    private static Injector injector;

    @Override
    protected void configure() {
        // Event System
        bind(EventBus.class).in(Singleton.class);

        // Core Rules
        bind(CheckDetector.class).in(Singleton.class);
        bind(MateDetector.class).in(Singleton.class);
        bind(MoveGenerator.class).in(Singleton.class);
        bind(MoveValidator.class).in(Singleton.class);
        bind(ChineseTranslator.class).in(Singleton.class);

        // Core Detection Components
        bind(BoardMatcher.class).in(Singleton.class);
        bind(BoardComparator.class).in(Singleton.class);

        // Core Book Components
        bind(BookSelector.class).in(Singleton.class);
        bind(ZobristHasher.class).in(Singleton.class);

        // Utility Components
        bind(SoundPlayer.class).in(Singleton.class);
        bind(WinRateCalculator.class).in(Singleton.class);

        // Services
        bind(GameService.class).in(Singleton.class);
        bind(EngineService.class).in(Singleton.class);
        bind(ManualService.class).in(Singleton.class);
        bind(BookService.class).in(Singleton.class);
        bind(DetectionService.class).in(Singleton.class);
        bind(AutomationService.class).in(Singleton.class);
    }

    public static Injector createInjector() {
        injector = Guice.createInjector(new AppModule());
        return injector;
    }

    public static Injector getInjector() {
        return injector;
    }

    @Provides
    @Singleton
    public Config provideConfig(ConfigManager manager) {
        return manager.load();
    }

    @Provides
    @Singleton
    public ConfigManager provideConfigManager() {
        return new ConfigManager();
    }

    @Provides
    @Singleton
    public Platform providePlatform() {
        return new WindowsPlatform();
    }
}
