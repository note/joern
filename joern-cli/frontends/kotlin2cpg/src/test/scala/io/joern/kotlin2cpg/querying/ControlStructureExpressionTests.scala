package io.joern.kotlin2cpg.querying

import io.joern.kotlin2cpg.Kt2CpgTestContext
import io.shiftleft.codepropertygraph.generated.Operators
import io.shiftleft.semanticcpg.language._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ControlStructureExpressionTests extends AnyFreeSpec with Matchers {
  "CPG for code with simple `if`-expression" - {
    lazy val cpg = Kt2CpgTestContext.buildCpg("""
        |package baz
        |
        |fun main(argc: Int): Int {
        |   val z: Int = if(argc > 0) argc else 0
        |   return z
        |}
        | """.stripMargin)

    "should contain a CALL for the `if`-expression with the correct props set" in {
      val List(c) = cpg.call.methodFullNameExact(Operators.conditional).l
      c.code shouldBe "if(argc > 0) argc else 0"
      c.lineNumber shouldBe Some(4)
      c.columnNumber shouldBe Some(16)
      c.argument.size shouldBe 3
    }

    "should contain a ARGUMENT nodes for the `if`-expression CALL with the correct props set" in {
      val List(a1) = cpg.call.methodFullNameExact(Operators.conditional).argument(1).l
      a1.code shouldBe "argc > 0"
      a1.lineNumber shouldBe Some(4)
      a1.columnNumber shouldBe Some(19)
      a1.order shouldBe 1

      val List(a2) = cpg.call.methodFullNameExact(Operators.conditional).argument(2).l
      a2.code shouldBe "argc"
      a2.lineNumber shouldBe Some(4)
      a2.columnNumber shouldBe Some(29)
      a2.order shouldBe 2

      val List(a3) = cpg.call.methodFullNameExact(Operators.conditional).argument(3).l
      a3.code shouldBe "0"
      a3.lineNumber shouldBe Some(4)
      a3.columnNumber shouldBe Some(39)
      a3.order shouldBe 3
    }
  }

  "CPG for code with simple `if`-expression with fn calls in branches" - {
    lazy val cpg = Kt2CpgTestContext.buildCpg("""
        |package mypkg
        |
        |fun some1(): Int {
        |  return 1
        |}
        |
        |fun some2(): Int {
        |  return 1
        |}
        |
        |fun main(argc: Int): Int {
        |   val z: Int = if(argc == 0) {
        |     some1()
        |   } else {
        |     some2()
        |   }
        |   return z
        |}
        | """.stripMargin)

    "should contain a CALL for the `if`-expression with the correct props set" in {
      val List(c) = cpg.call.methodFullNameExact(Operators.conditional).l
      c.lineNumber shouldBe Some(12)
      c.columnNumber shouldBe Some(16)
      c.argument.size shouldBe 3
    }

    "should contain a ARGUMENT nodes for the `if`-expression CALL with the correct props set" in {
      val List(a1) = cpg.call.methodFullNameExact(Operators.conditional).argument(1).l
      a1.code shouldBe "argc == 0"
      a1.lineNumber shouldBe Some(12)
      a1.columnNumber shouldBe Some(19)
      a1.order shouldBe 1

      val List(a2) = cpg.call.methodFullNameExact(Operators.conditional).argument(2).l
      a2.code shouldBe "some1()"
      a2.lineNumber shouldBe Some(12)
      a2.columnNumber shouldBe Some(30)
      a2.order shouldBe 2

      val List(a3) = cpg.call.methodFullNameExact(Operators.conditional).argument(3).l
      a3.code shouldBe "some2()"
      a3.lineNumber shouldBe Some(14)
      a3.columnNumber shouldBe Some(10)
      a3.order shouldBe 3
    }
  }

  "CPG for code with simple `if`-expression with DQEs in branches" - {
    lazy val cpg = Kt2CpgTestContext.buildCpg("""
        |package mypkg
        |
        |import kotlin.random.Random
        |
        |class MyClass {
        |    fun suffixWithX(what: String): String {
        |        return what + "X"
        |    }
        |
        |    fun suffixWithY(what: String): String {
        |        return what + "Y"
        |    }
        |}
        |
        |fun main(args: Array<String>) {
        |    val c = MyClass()
        |    val r = Random.nextInt(0, 100)
        |    val x =
        |        if (r < 50) {
        |            c.suffixWithX("mystring")
        |        } else {
        |            c.suffixWithY("mystring")
        |        }
        |    println(x)
        |}
        | """.stripMargin)

    "should contain a CALL for the `if`-expression with the correct props set" in {
      val List(c) = cpg.call.methodFullNameExact(Operators.conditional).l
      c.lineNumber shouldBe Some(19)
      c.columnNumber shouldBe Some(8)
      c.argument.size shouldBe 3
    }

    "should contain a ARGUMENT nodes for the `if`-expression CALL with the correct props set" in {
      val List(a1) = cpg.call.methodFullNameExact(Operators.conditional).argument(1).l
      a1.lineNumber shouldBe Some(19)
      a1.columnNumber shouldBe Some(12)
      a1.order shouldBe 1

      val List(a2) = cpg.call.methodFullNameExact(Operators.conditional).argument(2).l
      a2.lineNumber shouldBe Some(19)
      a2.columnNumber shouldBe Some(20)
      a2.order shouldBe 2

      val List(a3) = cpg.call.methodFullNameExact(Operators.conditional).argument(3).l
      a3.lineNumber shouldBe Some(21)
      a3.columnNumber shouldBe Some(15)
      a3.order shouldBe 3
    }
  }

  "CPG for code with simple `if`-expression with enum in branches" - {
    lazy val cpg = Kt2CpgTestContext.buildCpg("""
        |package mypkg
        |
        |import kotlin.random.Random
        |
        |enum class Direction {
        |    NORTH, SOUTH, WEST, EAST
        |}
        |
        |fun main(args: Array<String>) {
        |    val r = Random.nextInt(0, 100)
        |    val x =
        |        if (r < 50) {
        |           Direction.NORTH
        |        } else {
        |           Direction.SOUTH
        |        }
        |    println(x)
        |}
        | """.stripMargin)

    "should contain a CALL for the `if`-expression with the correct props set" in {
      val List(c) = cpg.call.methodFullNameExact(Operators.conditional).l
      c.lineNumber shouldBe Some(12)
      c.columnNumber shouldBe Some(8)
      c.argument.size shouldBe 3
    }

    "should contain a ARGUMENT nodes for the `if`-expression CALL with the correct props set" in {
      val List(a1) = cpg.call.methodFullNameExact(Operators.conditional).argument(1).l
      a1.lineNumber shouldBe Some(12)
      a1.columnNumber shouldBe Some(12)
      a1.order shouldBe 1

      val List(a2) = cpg.call.methodFullNameExact(Operators.conditional).argument(2).l
      a2.lineNumber shouldBe Some(12)
      a2.columnNumber shouldBe Some(20)
      a2.order shouldBe 2

      val List(a3) = cpg.call.methodFullNameExact(Operators.conditional).argument(3).l
      a3.lineNumber shouldBe Some(14)
      a3.columnNumber shouldBe Some(15)
      a3.order shouldBe 3
    }
  }

  "CPG for code with simple `if`-expression inside method" - {
    lazy val cpg = Kt2CpgTestContext.buildCpg("""
        |package mypkg
        |
        |import kotlin.random.Random
        |
        |class MyClass {
        |    fun suffix(what: String): String {
        |      val r = Random.nextInt(0, 100)
        |      val x =
        |           if (r < 50) {
        |               what + "mystring"
        |           } else {
        |               what + "mystring"
        |           }
        |        return x
        |    }
        |}
        |
        |fun main(args: Array<String>) {
        |    val c = MyClass()
        |    val x = c.suffix("A_STRING")
        |    println(x)
        |}
        | """.stripMargin)

    "should contain a CALL for the `if`-expression with the correct props set" in {
      val List(c) = cpg.call.methodFullNameExact(Operators.conditional).l
      c.lineNumber shouldBe Some(9)
      c.columnNumber shouldBe Some(11)
      c.argument.size shouldBe 3
    }

    "should contain a ARGUMENT nodes for the `if`-expression CALL with the correct props set" in {
      val List(a1) = cpg.call.methodFullNameExact(Operators.conditional).argument(1).l
      a1.lineNumber shouldBe Some(9)
      a1.columnNumber shouldBe Some(15)
      a1.order shouldBe 1

      val List(a2) = cpg.call.methodFullNameExact(Operators.conditional).argument(2).l
      a2.lineNumber shouldBe Some(9)
      a2.columnNumber shouldBe Some(23)
      a2.order shouldBe 2

      val List(a3) = cpg.call.methodFullNameExact(Operators.conditional).argument(3).l
      a3.lineNumber shouldBe Some(11)
      a3.columnNumber shouldBe Some(18)
      a3.order shouldBe 3
    }
  }

  "CPG for code with simple `when`-expression" - {
    lazy val cpg = Kt2CpgTestContext.buildCpg("""
        |package mypkg
        |
        |fun myfun() {
        |  val x =  Random.nextInt(0, 3)
        |  val foo = when (x) {
        |      1 -> 123
        |      2 -> 234
        |      else -> {
        |          456
        |      }
        |  }
        |  println(foo)
        |}
        | """.stripMargin)

    // TODO: add test case
  }

}
