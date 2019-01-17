package com.github.oowekyala.rxstring

import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import org.reactfx.value.Var

/**
 * @author Clément Fournier
 * @since 1.0
 */
class ValBehaviorTest : FunSpec({

    test("Test value changes") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
            val clazz = Var.newSimpleVar<Class<*>>(FunSpec::class.java)
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()


        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc


        lt.value shouldBe "Foo[MissingOverride]bar"

        dc.name.value = "hehe"

        lt.value shouldBe "Foo[hehe]bar"

        dc.name.value = null
        lt.value shouldBe "Foo[null]bar"
    }


    test("Test multiple value change propagation") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
            val clazz = Var.newSimpleVar<Class<*>>(FunSpec::class.java)
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar<")
                .bind { it.clazz.map { it.simpleName } }
                .append(">")
                .toTemplate()


        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc


        lt.value shouldBe "Foo[MissingOverride]bar<FunSpec>"

        dc.name.value = "hehe"

        lt.value shouldBe "Foo[hehe]bar<FunSpec>"

        dc.clazz.value = DContext::class.java
        lt.value shouldBe "Foo[hehe]bar<DContext>"
    }

    test("Test null value handling") {
        class DContext {
            val name = Var.newSimpleVar("MissingOverride")
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()


        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc

        lt.value shouldBe "Foo[MissingOverride]bar"

        dc.name.value = null
        lt.value shouldBe "Foo[null]bar"
        dc.name.value = "FOO"
        lt.value shouldBe "Foo[FOO]bar"
    }

    test("Test nested template") {

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


        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc

        lt.value shouldBe """
            <top name='top'>
            <sub name='sub' num='4'/>
            </top>
        """.trimIndent()

        lt.dataContext.name.value = "foo"


        lt.value shouldBe """
            <top name='foo'>
            <sub name='sub' num='4'/>
            </top>
        """.trimIndent()

        lt.dataContext.sub.value.name.value = "foo"


        lt.value shouldBe """
            <top name='foo'>
            <sub name='foo' num='4'/>
            </top>
        """.trimIndent()
    }

    test("Test initial null value") {
        class DContext {
            val name: Var<String?> = Var.newSimpleVar(null)
        }

        val lt = LiveTemplate
                .builder<DContext>()
                .append("Foo[")
                .bind { it.name }
                .append("]bar")
                .toTemplate()


        lt.value shouldBe null

        val dc = DContext()
        lt.dataContext = dc

        lt.value shouldBe "Foo[null]bar"
    }

})