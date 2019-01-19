package com.github.oowekyala.rxstring

import io.kotlintest.specs.AbstractFunSpec
import java.util.logging.Handler
import java.util.logging.LogRecord
import io.kotlintest.shouldBe as kotlintestShouldBe

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

/** Defines a group of tests that should be named similarly.*/
fun AbstractFunSpec.testGroup(
        name: String,
        spec: GroupTestCtx.() -> Unit) {
    GroupTestCtx(this, name).spec()
}

class GroupTestCtx(private val funspec: AbstractFunSpec, private val groupName: String) {

    private var i = 0

    infix fun String.shouldBe(matcher: String) {
        funspec.test("$groupName${i++}: '$matcher'") {
            this@shouldBe kotlintestShouldBe matcher
        }
    }
}