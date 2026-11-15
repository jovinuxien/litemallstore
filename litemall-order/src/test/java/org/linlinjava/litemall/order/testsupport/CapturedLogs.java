package org.linlinjava.litemall.order.testsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Captures one class's log events so a test can assert that a line was written. */
public final class CapturedLogs implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public CapturedLogs(Class<?> loggingClass) {
        this.logger = (Logger) LoggerFactory.getLogger(loggingClass);
        appender.start();
        logger.addAppender(appender);
    }

    /** Formatted messages logged at WARN, in order. */
    public List<String> warnings() {
        return List.copyOf(appender.list).stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
    }
}
