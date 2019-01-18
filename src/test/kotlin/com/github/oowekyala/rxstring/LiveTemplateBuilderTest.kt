package com.github.oowekyala.rxstring

import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import org.reactfx.collection.LiveArrayList
import org.reactfx.value.Var

/**
 * @author Clément Fournier
 * @since 1.0
 */
class LiveTemplateBuilderTest : FunSpec({

    test("Test default indent is inherited with bindTemplate") {
        class SubDContext {
            val name = Var.newSimpleVar("sub")
            val num = Var.newSimpleVar(4)
        }

        class DContext {
            val name = Var.newSimpleVar("top")
            val sub = Var.newSimpleVar(SubDContext())
        }

        val lt = LiveTemplate
                .builder<DContext>().withDefaultIndent("* ")
                .appendIndent(1).append("<top name='").bind { it.name }.appendLine("'>")
                .bindTemplate({ it.sub }) { sub ->
                    sub.appendIndent(2).append("<sub name='").bind { it.name }.append("' num='").bind { it.num }.append("'/>")
                }
                .endLine()
                .appendIndent(1).append("</top>")
                .toTemplate()

        val extSb = StringBuilder()

        val handler = ReplaceHandler { start, end, value -> extSb.replace(start, end, value) }
        lt.addReplaceHandler(handler)
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe """
            * <top name='top'>
            * * <sub name='sub' num='4'/>
            * </top>
        """.trimIndent()

    }


    test("Test default indent is inherited with bindTemplatedSeq") {
        class SubDContext(n: String = "sub", num: Int = 4) {
            val name = Var.newSimpleVar(n)
            val num = Var.newSimpleVar(num)
        }

        class DContext {
            val name = Var.newSimpleVar("top")
            val sub = LiveArrayList(SubDContext())
        }

        val lt = LiveTemplate
                .builder<DContext>().withDefaultIndent("* ")
                .appendIndent(1).append("<top name='").bind { it.name }.appendLine("'>")
                .bindTemplatedSeq({ it.sub }) { sub ->
                    sub.appendIndent(2).append("<sub name='").bind { it.name }.append("' num='").bind { it.num }.appendLine("'/>")
                }
                .appendIndent(1).append("</top>")
                .toTemplate()

        val extSb = StringBuilder()

        val handler = ReplaceHandler { start, end, value -> extSb.replace(start, end, value) }
        lt.addReplaceHandler(handler)
        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc
        lt.value shouldBe """
            * <top name='top'>
            * * <sub name='sub' num='4'/>
            * </top>
        """.trimIndent()

        lt.dataContext.sub.add(SubDContext("foo", 6))

        lt.value shouldBe """
            * <top name='top'>
            * * <sub name='sub' num='4'/>
            * * <sub name='foo' num='6'/>
            * </top>
        """.trimIndent()

    }

})