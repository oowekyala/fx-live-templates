package com.github.oowekyala.rxstring

import com.github.oowekyala.rxstring.ItemRenderer.asString
import com.github.oowekyala.rxstring.SeqRenderer.delimited
import io.kotlintest.shouldBe
import io.kotlintest.specs.FunSpec
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import io.kotlintest.shouldBe as kotlintestShouldBe

/**
 * @author Clément Fournier
 * @since 1.0
 */
class DelimitedSeqTest : FunSpec() {
    class DContext(ns: List<Int>) {
        val nums: ObservableList<Int> = FXCollections.observableArrayList(ns)
    }

    var lt = LiveTemplate.newBuilder<DContext>().bindSeq({ it.nums }, delimited("[", "]", ",", asString()))

    init {
        var i = 0

        fun String.isValueAfter(name: String, ns: List<Int> = listOf(10, 15), f: (ObservableList<Int>) -> Unit) {
            test("$name: expected ${this@isValueAfter}") {
                val t = lt.toBoundTemplate(DContext(ns))
                f(t.dataContext.nums)
                t.value shouldBe this@isValueAfter
            }
        }

        fun String.fisValueAfter(name: String, ns: List<Int> = listOf(10, 15), f: (ObservableList<Int>) -> Unit) {
            isValueAfter("f:$name", ns, f)
        }

        "[10,15]".isValueAfter("base") { }


        "[]".isValueAfter("clear") { it.clear() }
        "[]".isValueAfter("clear one elt", listOf(2)) { it.clear() }
        "[]".isValueAfter("clear empty", listOf()) { it.clear() }
        "[]".isValueAfter("empty", listOf()) { }

        "[10]".isValueAfter("remove last") { it.removeAt(1) }
        "[15]".isValueAfter("remove fst") { it.removeAt(0) }
        "[]".isValueAfter("remove to empty", listOf(1)) { it.removeAt(0) }
        "[10,12]".isValueAfter("remove mid", listOf(10, 11, 12)) { it.removeAt(1) }

        "[2,15]".isValueAfter("set fst") { it[0] = 2 }
        "[10,2]".isValueAfter("set last") { it[1] = 2 }
        "[10,2,12]".isValueAfter("set mid", listOf(10, 11, 12)) { it[1] = 2 }
        "[5]".isValueAfter("set only elt", listOf(1)) { it[0] = 5 }
        "[1,2,3]".isValueAfter("setAll") { it.setAll(1, 2, 3) }

        "[2,10,15]".isValueAfter("add fst") { it.add(0, 2) }
        "[10,2,15]".isValueAfter("add mid") { it.add(1, 2) }
        "[10,15,2]".isValueAfter("add last") { it.add(2, 2) }
        "[10,15,2]".isValueAfter("add last 2") { it += 2 }
        "[4]".isValueAfter("add to empty", listOf()) { it += 4 }
        "[4,5,6]".isValueAfter("addAll to empty", listOf()) { it.addAll(4, 5, 6) }


    }
}