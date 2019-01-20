package com.github.oowekyala.rxstring

import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import javafx.collections.FXCollections
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

        lt.textChanges().subscribe { extSb.replace(it.startIndex, it.endIndex, it.replacementText) }
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

        lt.textChanges().subscribe { extSb.replace(it.startIndex, it.endIndex, it.replacementText) }
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


    test("Test escape function inheritance with bind and bindTemplatedSeq") {
        class SubDContext(n: String = "sub", num: Int = 4) {
            val name = Var.newSimpleVar(n)
            val num = Var.newSimpleVar(num)
        }

        class DContext {
            val name = Var.newSimpleVar("top")
            val sub = LiveArrayList(SubDContext())
        }

        val lt =
                LiveTemplate.builder<DContext>()
                        .withDefaultEscape { s -> "@$s@" }
                        .append("<top name='").bind { it.name }.appendLine("'>")
                        .bindTemplatedSeq({ it.sub }) { sub ->
                            sub.append("<sub name='").bind { it.name }.append("' num='").bind { it.num }.appendLine("'/>")
                        }
                        .append("</top>")
                        .toBoundTemplate(DContext())

        lt.value shouldBe """
            <top name='@top@'>
            <sub name='@sub@' num='@4@'/>
            </top>
        """.trimIndent()

        lt.dataContext.sub.add(SubDContext("foo", 6))

        lt.value shouldBe """
            <top name='@top@'>
            <sub name='@sub@' num='@4@'/>
            <sub name='@foo@' num='@6@'/>
            </top>
        """.trimIndent()

    }



    test("Test escape function inheritance with bind and bindTemplate") {
        class SubDContext {
            val name = Var.newSimpleVar("sub")
            val num = Var.newSimpleVar(4)
        }

        class DContext {
            val name = Var.newSimpleVar("top")
            val sub = Var.newSimpleVar(SubDContext())
        }

        val lt =
                LiveTemplate.builder<DContext>()
                        .withDefaultEscape { "@$it@" }
                        .append("<top name='").bind { it.name }.appendLine("'>")
                        .bindTemplate({ it.sub }) { sub ->
                            sub.append("<sub name='").bind { it.name }.append("' num='").bind { it.num }.append("'/>")
                        }
                        .endLine()
                        .append("</top>")
                        .toBoundTemplate(DContext())

        lt.value shouldBe """
            <top name='@top@'>
            <sub name='@sub@' num='@4@'/>
            </top>
        """.trimIndent()

        lt.dataContext.sub.value.name.value = "foo"

        lt.value shouldBe """
            <top name='@top@'>
            <sub name='@foo@' num='@4@'/>
            </top>
        """.trimIndent()

    }

    test("Test mapped bind") {

        class DContext {
            val name = Var.newSimpleVar("top")
        }

        val lt =
                LiveTemplate.builder<DContext>()
                        .append("<top name='").bind({ it.name }) { "@$it" }.append("'/>")
                        .toBoundTemplate(DContext())

        lt.value shouldBe """
            <top name='@top'/>
        """.trimIndent()

        lt.dataContext.name.value = "foo"

        lt.value shouldBe """
            <top name='@foo'/>
        """.trimIndent()

    }

    test("Test mapped bindSeq") {

        class DContext {
            val nums = FXCollections.observableArrayList(10, 15)
        }

        val lt =
                LiveTemplate.builder<DContext>()
                        .append("<top nums='").bindSeq({ it.nums }) { Integer.toHexString(it) + "," }.append("'/>")
                        .toBoundTemplate(DContext())

        lt.value shouldBe """
            <top nums='a,f,'/>
        """.trimIndent()

        lt.dataContext.nums += 256

        lt.value shouldBe """
            <top nums='a,f,100,'/>
        """.trimIndent()

    }

    test("Test delimited bindSeq") {

        class DContext {
            val nums = FXCollections.observableArrayList(10, 15)
        }

        val lt =
                LiveTemplate.builder<DContext>()
                        .append("<top nums='").bindSeq({ it.nums }, SeqRenderer.delimited(ItemRenderer.asString(), "[", "]", ",")).append("'/>")
                        .toBoundTemplate(DContext())

        lt.value shouldBe """
            <top nums='[10,15]'/>
        """.trimIndent()

        lt.dataContext.nums += 256

        lt.value shouldBe """
            <top nums='[10,15,256]'/>
        """.trimIndent()

        lt.dataContext.nums.clear()

        lt.value shouldBe """
            <top nums='[]'/>
        """.trimIndent()

        lt.dataContext.nums.add(4)

        lt.value shouldBe """
            <top nums='[4]'/>
        """.trimIndent()

        lt.dataContext.nums.add(5)

        lt.value shouldBe """
            <top nums='[4,5]'/>
        """.trimIndent()

        lt.dataContext.nums.removeAt(1)

        lt.value shouldBe """
            <top nums='[4]'/>
        """.trimIndent()

        lt.dataContext.nums.setAll(1, 2, 3)

        lt.value shouldBe """
            <top nums='[1,2,3]'/>
        """.trimIndent()

        lt.dataContext.nums[2] = 2

        lt.value shouldBe """
            <top nums='[1,2,2]'/>
        """.trimIndent()

        lt.dataContext.nums[0] = 2

        lt.value shouldBe """
            <top nums='[2,2,2]'/>
        """.trimIndent()

        lt.dataContext.nums.add(0, 4)

        lt.value shouldBe """
            <top nums='[4,2,2,2]'/>
        """.trimIndent()

    }

    test("Test empty string constants are pruned") {

        class DContext {
            val nums = FXCollections.observableArrayList(10, 15)
        }

        val lt =
                LiveTemplate.builder<DContext>()
                        .append("<top nums='").bindSeq({ it.nums }, SeqRenderer.delimited(ItemRenderer.asString(), "", "", ",")).append("'/>")
                        .toBoundTemplate(DContext())

        (lt as LiveTemplateImpl).totalSubscriptions().value shouldBe 5L

    }

    test("Test null string constants are pruned") {

        class DContext {
            val nums = FXCollections.observableArrayList(10, 15)
        }

        val lt =
                LiveTemplate.builder<DContext>()
                        .append("<top nums='").bindSeq({ it.nums }) { null }.append("'/>")
                        .toBoundTemplate(DContext())

        (lt as LiveTemplateImpl).totalSubscriptions().value shouldBe 2L

    }

})