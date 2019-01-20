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
                .newBuilder<DContext>()
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

    test("Test replacement with diff match patch strategy") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .newBuilder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()

        lt.isUseDiffMatchPatchStrategy = true
        val extSb = StringBuilder()

        val events = mutableListOf<ReplaceEvent>()

        lt.addReplaceHandler { start, end, value -> extSb.replace(start, end, value) }
        lt.addReplaceHandler { start, end, value ->
            events += ReplaceEvent(start, end, value)
        }
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe "Foo[MissingOverride]bar"

        events should haveSize(1)

        dc.name.value = "MissingYou"

        lt.value shouldBe "Foo[MissingYou]bar"
        extSb.toString() shouldBe "Foo[MissingYou]bar"

        events should haveSize(2)
        events.last() shouldBe ReplaceEvent(11, 19, "You")
    }


    test("Test replacement in external stringbuilder") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .newBuilder<DContext>()
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


    test("Test adding a handler after binding should insert current text") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .newBuilder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()

        val extSb = StringBuilder()
        val events = mutableListOf<ReplaceEvent>()

        lt.value shouldBe null
        lt.dataContext = DContext()
        lt.value shouldBe "Foo[MissingOverride]bar"
        extSb.toString() shouldBe ""
        events should haveSize(0)

        lt.addReplaceHandler(mirrorChanges(extSb))
        lt.addReplaceHandler(recordEvents(events))

        extSb.toString() shouldBe "Foo[MissingOverride]bar"
        events should haveSize(1)
        events.last() shouldBe ReplaceEvent(0, 0, "Foo[MissingOverride]bar")

        lt.dataContext.name.value = "HELLO"

        lt.value shouldBe "Foo[HELLO]bar"
        extSb.toString() shouldBe "Foo[HELLO]bar"

    }

    test("Test adding a handler with no binding should not push an event") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .newBuilder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()

        val extSb = StringBuilder()
        val events = mutableListOf<ReplaceEvent>()

        lt.value shouldBe null
        extSb.toString() shouldBe ""

        lt.addReplaceHandler(mirrorChanges(extSb))
        lt.addReplaceHandler(recordEvents(events))

        events should haveSize(0)
        extSb.toString() shouldBe ""
    }

    test("A removed handler should not be executed") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .newBuilder<DContext>()
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
                .newBuilder<DContext>()
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
                .newBuilder<DContext>()
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
                .newBuilder<DContext>()
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

    test("f:Test nested template deletion event") {

        class SubDContext {
            val name = Var.newSimpleVar("sub")
            val num = Var.newSimpleVar(4)
        }

        class DContext {
            val name = Var.newSimpleVar("top")
            val sub = Var.newSimpleVar(SubDContext())
        }

        val lt = LiveTemplate
                .newBuilder<DContext>()
                .append("<top name='").bind { it.name }.appendLine("'>")
                .bindTemplate({ it.sub }) { sub ->
                    sub.append("<sub name='").bind { it.name }.append("' num='").bind { it.num }.appendLine("'/>")
                }
                .append("</top>")
                .toTemplate()

        val events = mutableListOf<ReplaceEvent>()

        lt.addReplaceHandler(recordEvents(events))

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

        lt.dataContext.sub.value = null

        lt.value shouldBe """
            <top name='top'>
            </top>
        """.trimIndent()

        events should haveSize(2)
        events.last() shouldBe ReplaceEvent(17, 43, "")

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

        val events = mutableListOf<ReplaceEvent>()

        val lt = LiveTemplate
                .newBuilder<DContext>()
                .append("<top name='").bind { it.name }.appendLine("'>")
                .bindTemplatedSeq({ it.subs }) { sub ->
                    sub.append("<sub name='").bind { it.name }.append("' num='").bind { it.num }.appendLine("'/>")
                }
                .append("</top>")
                .toBoundTemplate(DContext(), ReplaceHandler { start, end, value -> events += ReplaceEvent(start, end, value) })

        lt.isUseDiffMatchPatchStrategy = false

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
                .newBuilder<DContext>()
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

        lt.isUseDiffMatchPatchStrategy = false

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