package com.jiyi.service;

import com.google.inject.Inject;
import com.jiyi.core.book.*;
import com.jiyi.core.model.Board;
import com.jiyi.infra.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BookService {
    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final Config config;
    private final BookSelector selector = new BookSelector();
    private final List<OpenBook> localBooks = new ArrayList<>();
    private CloudOpenBook cloudBook;

    @Inject
    public BookService(Config config) {
        this.config = config;
        initLocalBooks();
        if (config.book().cloudEnabled()) {
            cloudBook = new CloudOpenBook(config.book().cloudTimeoutMs());
        }
    }

    private void initLocalBooks() {
        for (String path : config.book().files()) {
            try {
                String lower = path.toLowerCase();
                if (lower.endsWith(".obk")) {
                    localBooks.add(new BhOpenBook(path));
                    log.info("Loaded OBK book: {}", path);
                } else if (lower.endsWith(".pfbook")) {
                    localBooks.add(new PfOpenBook(path));
                    log.info("Loaded PF book: {}", path);
                } else {
                    log.warn("Unsupported book format (skipped): {}", path);
                }
            } catch (Exception e) {
                log.warn("Failed to load book: {}", path, e);
            }
        }
    }

    /**
     * 查询库招。
     * @param moveCount 当前已走步数（用于脱谱判定：达到 offManualSteps 后不再查库，回退引擎）
     */
    public String queryBestMove(Board board, boolean redGo, int moveCount) {
        // ★ 脱谱步数（对齐 C++ bookDepth）：已走步数 >= offManualSteps 后不再查库，回退引擎分析
        int offManualSteps = config.book().offManualSteps();
        if (offManualSteps >= 0 && moveCount >= offManualSteps) {
            log.debug("Off-manual steps reached ({}>={}), falling back to engine", moveCount, offManualSteps);
            return null;
        }

        BookSelector.Strategy strategy;
        try {
            strategy = BookSelector.Strategy.valueOf(config.book().moveRule());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid moveRule '{}', falling back to BEST_SCORE", config.book().moveRule());
            strategy = BookSelector.Strategy.BEST_SCORE;
        }

        // 库招延迟（防检测）：范围内随机等待
        applyBookDelay();

        if (config.book().localFirst()) {
            for (var book : localBooks) {
                var entries = book.query(board, redGo);
                if (!entries.isEmpty()) {
                    var selected = selector.select(entries, strategy);
                    if (selected != null) {
                        log.debug("Local book: {} score={}", selected.move(), selected.score());
                        return selected.move();
                    }
                }
            }
        }

        if (config.book().cloudEnabled() && cloudBook != null) {
            var entries = cloudBook.query(board, redGo);
            if (!entries.isEmpty()) {
                var selected = selector.select(entries, strategy);
                if (selected != null) {
                    log.debug("Cloud book: {} score={}", selected.move(), selected.score());
                    return selected.move();
                }
            }
        }

        return null;
    }

    private void applyBookDelay() {
        int start = config.book().bookDelayStartMs();
        int end = config.book().bookDelayEndMs();
        if (end > start && start >= 0) {
            int delay = start + (int) (Math.random() * (end - start));
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void close() {
        for (var book : localBooks) book.close();
    }
}
