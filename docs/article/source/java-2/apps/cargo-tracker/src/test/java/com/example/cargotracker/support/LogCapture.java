package com.example.cargotracker.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * 指定したロガーの出力を捕まえるテスト用ヘルパー。
 *
 * <p>監査ログは「出しているつもり」になりやすい。**コードに
 * {@code logger.info(...)} が書いてあることと、実際にその内容が出ていることは別である**
 * （ロガー名を間違える、レベルで抑止される、例外経路では通らない）。
 * 出力そのものを検証するために用いる（IT1 持ち越し C4）。
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Level originalLevel;

    private LogCapture(String loggerName) {
        this.logger = (Logger) LoggerFactory.getLogger(loggerName);
        this.originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    /** 指定したロガーの捕捉を開始する。 */
    public static LogCapture of(String loggerName) {
        return new LogCapture(loggerName);
    }

    /** 捕捉したメッセージ（引数を展開済み）。 */
    public List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }
}
