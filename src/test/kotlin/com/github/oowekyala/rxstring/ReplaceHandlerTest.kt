package com.github.oowekyala.rxstring

import io.kotlintest.matchers.beOfType
import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import org.reactfx.value.Var
import java.util.logging.Logger

/**
 * @author Clément Fournier
 * @since 1.0
 */
class ReplaceHandlerTest : FunSpec({

    test("Test initialisation causes full text insertion") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()

        val extSb = StringBuilder()

        val handler = ReplaceHandler { start, end, value -> extSb.replace(start, end, value) }
        lt.replaceHandler = handler
        lt.replaceHandler shouldBe handler
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

    }

    test("Test replacement in external stringbuilder") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()

        val extSb = StringBuilder()

        val handler = ReplaceHandler { start, end, value -> extSb.replace(start, end, value) }
        lt.replaceHandler = handler
        lt.replaceHandler shouldBe handler
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

        dc.name.value = "HELLO"

        lt.value shouldBe "Foo[HELLO]bar"
        extSb.toString() shouldBe "Foo[HELLO]bar"

    }



    test("Exceptions in the handler should not affect the consistency of the live template") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()

        val extSb = StringBuilder()

        val handler = ReplaceHandler { start, end, value -> extSb.replace(start, end, value) }
        lt.replaceHandler = handler
        lt.replaceHandler shouldBe handler
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

        extSb.clear()

        val testLogHandler = TestLogHandler()
        Logger.getLogger(LiveTemplate::class.java.name).addHandler(testLogHandler)

        dc.name.value = "HELLO" // catches a StringIndexOutOfBoundsException

        testLogHandler.log should haveSize(1)
        testLogHandler.log[0].thrown should beOfType<StringIndexOutOfBoundsException>()

        // the value stayed consistent
        lt.value shouldBe "Foo[HELLO]bar"

    }

    test("Test nested template minimal change") {

        class SubDContext {
            val name = Var.newSimpleVar("sub")
            val num = Var.newSimpleVar(4)
        }

        class DContext {
            val name = Var.newSimpleVar("top")
            val sub = Var.newSimpleVar(SubDContext())
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("<top name='").bind { it.name }.appendLine("'>")
                .bindTemplate({ it.sub }) { sub ->
                    sub.append("<sub name='").bind { it.name }.append("' num='").bind { it.num }.append("'/>")
                }
                .endLine()
                .append("</top>")
                .toTemplate()

        val events = mutableListOf<ReplaceEvent>()

        lt.setReplaceHandler { start, end, value ->
            events += ReplaceEvent(start, end, value)
        }

        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc

        val afterValue1 = """
            <top name='top'>
            <sub name='sub' num='4'/>
            </top>
        """.trimIndent()

        lt.value shouldBe afterValue1

        events should haveSize(1)
        events.last() shouldBe ReplaceEvent(0, 0, afterValue1)

        lt.dataContext.name.value = "foo"

        events should haveSize(2)
        events.last() shouldBe ReplaceEvent(11, 14, "foo")

        lt.dataContext.sub.value.name.value = "foo"

        events should haveSize(3)
        events.last() shouldBe ReplaceEvent(28, 31, "foo")

        lt.value shouldBe """
            <top name='foo'>
            <sub name='foo' num='4'/>
            </top>
        """.trimIndent()
    }

})