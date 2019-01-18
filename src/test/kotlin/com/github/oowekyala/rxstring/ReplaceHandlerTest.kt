package com.github.oowekyala.rxstring

import io.kotlintest.matchers.beOfType
import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import javafx.collections.FXCollections
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
        lt.addReplaceHandler(handler)
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
        lt.addReplaceHandler(handler)
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

        dc.name.value = "HELLO"

        lt.value shouldBe "Foo[HELLO]bar"
        extSb.toString() shouldBe "Foo[HELLO]bar"

    }

    test("A removed handler should not be executed") {
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
        lt.addReplaceHandler(handler)
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc

        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

        lt.removeReplaceHandler(handler)
        dc.name.value = "HELLO"

        lt.value shouldBe "Foo[HELLO]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

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
        lt.addReplaceHandler(handler)
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

        extSb.clear() // Now extSb isn't long enough and will throw StringIndexOutOfBoundsException on replace

        val testLogHandler = TestLogHandler()
        Logger.getLogger(LiveTemplate::class.java.name).addHandler(testLogHandler)

        dc.name.value = "HELLO"

        testLogHandler.log should haveSize(1)
        testLogHandler.log[0].thrown should beOfType<StringIndexOutOfBoundsException>()

        // the value stayed consistent
        lt.value shouldBe "Foo[HELLO]bar"

    }


    test("Exceptions in a handler should not prevent other handlers from executing") {
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

        lt.addReplaceHandler { start, end, value -> extSb.replace(start, end, value) }
        lt.addReplaceHandler { _, _, _ -> throw IllegalStateException() }
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

        val testLogHandler = TestLogHandler()
        Logger.getLogger(LiveTemplate::class.java.name).addHandler(testLogHandler)

        dc.name.value = "HELLO"

        testLogHandler.log should haveSize(1)
        testLogHandler.log[0].thrown should beOfType<IllegalStateException>()

        lt.value shouldBe "Foo[HELLO]bar"
        extSb.toString() shouldBe "Foo[HELLO]bar" // this one got executed
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

        lt.addReplaceHandler { start, end, value ->
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


    test("Test nested template sequence minimal change") {

        class SubDContext {
            val name = Var.newSimpleVar("sub")
            val num = Var.newSimpleVar(4)
        }

        class DContext {
            val name = Var.newSimpleVar("top")
            val subs = FXCollections.observableArrayList(SubDContext())
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("<top name='").bind { it.name }.appendLine("'>")
                .bindTemplatedSeq({ it.subs }) { sub ->
                    sub.append("<sub name='").bind { it.name }.append("' num='").bind { it.num }.appendLine("'/>")
                }
                .append("</top>")
                .toTemplate()

        val events = mutableListOf<ReplaceEvent>()

        lt.addReplaceHandler { start, end, value ->
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

        lt.dataContext.subs[0].name.value = "foo"

        events should haveSize(3)
        events.last() shouldBe ReplaceEvent(28, 31, "foo")

        lt.value shouldBe """
            <top name='foo'>
            <sub name='foo' num='4'/>
            </top>
        """.trimIndent()

        lt.dataContext.subs.add(SubDContext())

        lt.value shouldBe """
            <top name='foo'>
            <sub name='foo' num='4'/>
            <sub name='sub' num='4'/>
            </top>
        """.trimIndent()

        events should haveSize(4)
        events.last() shouldBe ReplaceEvent(43, 43, "<sub name='sub' num='4'/>\n")
    }

    test("Test binding a nested template") {

        class SubDContext {
            val name = Var.newSimpleVar("sub")
            val num = Var.newSimpleVar(4)
        }

        class DContext {
            val name = Var.newSimpleVar("top")
            val subs = FXCollections.observableArrayList(SubDContext())
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("<top name='").bind { it.name }.appendLine("'>")
                .bindTemplatedSeq({ it.subs }) { sub ->
                    sub.append("<sub name='").bind { it.name }.append("' num='").bind { it.num }.appendLine("'/>")
                }
                .append("</top>")
                .toTemplate()

        val events = mutableListOf<ReplaceEvent>()

        lt.addReplaceHandler { start, end, value ->
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

        val nameBinding = Var.newSimpleVar("bindSource")
        val numBinding = Var.newSimpleVar(10)

        lt.dataContext.subs[0].name.bind(nameBinding)
        lt.dataContext.subs[0].num.bind(numBinding)


        lt.value shouldBe """
            <top name='foo'>
            <sub name='bindSource' num='10'/>
            </top>
        """.trimIndent()

        events should haveSize(4)
        events[2] shouldBe ReplaceEvent(28, 31, "bindSource")
        events.last() shouldBe ReplaceEvent(45, 46, "10")

        numBinding.value = 15

        events should haveSize(5)
        events.last() shouldBe ReplaceEvent(45, 47, "15")

        lt.value shouldBe """
            <top name='foo'>
            <sub name='bindSource' num='15'/>
            </top>
        """.trimIndent()
    }

})