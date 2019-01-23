package com.github.oowekyala.rxstring

import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec

/**
 * @author Clément Fournier
 * @since 1.0
 */
class IndentHelperTest : FunSpec({

    fun String.indentNewlines(indent: String): String = IndentHelper.filterIndents(this, indent)
    fun String.selectLines(): List<String> = IndentHelper.preprocessLines(this)

    test("Test filterIndents no newline") {
        "foo".indentNewlines(">") shouldBe "foo"
    }

    test("Test filterIndents w/ newline") {
        """
            Foo
            Bar

            Hey
        """.trimIndent().indentNewlines(">") shouldBe """
            Foo
            >Bar

            >Hey
        """.trimIndent()
    }


    test("Test line selection") {
        """
          I am unable to see the contents of the Image AST
          Attribute and do regexp on it in XPath rules
        """.trimIndent().selectLines() shouldBe listOf(
                "I am unable to see the contents of the Image AST Attribute and do regexp on it in XPath rules"
        )

        """
          I am unable to see the contents of the Image AST

          Attribute and do regexp on it in XPath rules
        """.trimIndent().selectLines() shouldBe listOf(
                "I am unable to see the contents of the Image AST",
                "Attribute and do regexp on it in XPath rules"
        )


        """
          I am unable to see the contents of the Image AST




          Attribute and do regexp on it in XPath rules
        """.trimIndent().selectLines() shouldBe listOf(
                "I am unable to see the contents of the Image AST",
                "Attribute and do regexp on it in XPath rules"
        )

        """
          I am unable to see the contents of the Image AST




          Attribute and do regexp on it in XPath rules
             foo bar
        """.trimIndent().selectLines() shouldBe listOf(
                "I am unable to see the contents of the Image AST",
                "Attribute and do regexp on it in XPath rules foo bar"
        )

    }

})