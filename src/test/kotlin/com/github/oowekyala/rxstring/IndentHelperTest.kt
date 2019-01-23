package com.github.oowekyala.rxstring

import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec

/**
 * @author Clément Fournier
 * @since 1.0
 */
class IndentHelperTest : FunSpec({

    fun String.indentNls(indent: String): String = IndentHelper.filterIndents(this, indent)

    test("Test filterIndents no newline") {
        "foo".indentNls(">") shouldBe "foo"
    }

    test("Test filterIndents w/ newline") {
        """
            Foo
            Bar

            Hey
        """.trimIndent().indentNls(">") shouldBe """
            Foo
            >Bar

            >Hey
        """.trimIndent()
    }

})