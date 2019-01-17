package com.github.oowekyala.rxstring

import java.util.logging.Handler
import java.util.logging.LogRecord

/**
 * @author Clément Fournier
 * @since 1.0
 */
internal class TestLogHandler : Handler() {

    val log = mutableListOf<LogRecord>()

    override fun publish(record: LogRecord) {
        log += record
    }

    override fun close() {}
    override fun flush() {}
}