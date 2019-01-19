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

/**
 * Object describing a replacement that has occurred inside a live template
 * because of a change in a bound value.
 *
 * @author Clément Fournier
 */
internal data class ReplaceEvent(val startIndex: Int, val endIndex: Int, val value: String)
