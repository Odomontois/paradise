// NOTE: a lot of these tests are taken from https://github.com/liufengyun/eden/blob/master/src/test/scala/dotty/eden/UntpdSuite.scala

class Syntactic extends ConverterSuite {
  // terms
  syntactic("null")
  syntactic("""println("hello, world")""")
  syntactic("println(42)")
  syntactic("f(this)")
  syntactic("f(A.this)")
  syntactic("this.age")
  syntactic("C.this.age")
  syntactic("super.age")
  syntactic("super[A].age")
  syntactic("C.super[A].age")
  syntactic("f[Int](3)")
  syntactic("f(x = 3)")
  syntactic("f(x = 3, y = 6 * 8)")
  syntactic("f(x:_*)")
  syntactic("a.f(this.age)")
  syntactic("a + b")
  syntactic("a + b + c + this.age")
  syntactic("a :+ b")
  syntactic("a :+ (b, c)")
  syntactic("a :+[Int] b")
  syntactic("a :+[Int] (b, c)")
  syntactic("a +: b")
  syntactic("a +: (b, c)")
  syntactic("a +:[Int] b")
  syntactic("a +:[Int] (b, c)")
  syntactic("a*")
  syntactic("++a")
  syntactic("a++")
  syntactic("a = b")
  syntactic("{ a = 1; b += 2 }")
  syntactic("{ }")
  syntactic("()")
  syntactic("(2)")
  syntactic("(2, 4)")
  syntactic("a -> b")
  syntactic("if (cond) a else b")
  syntactic("if (cond) return a")
  syntactic("while (a > 5) { println(a); a++; }")
  syntactic("do { println(a); a++; } while (a > 5)")
  syntactic("return a")
  syntactic("new List(5)")
  syntactic("new List[Int](5)")
  syntactic("new List[List[Int]](List(5))")
  syntactic("new Map[Int, String]")
  syntactic("new Map[Int, String]()")
  syntactic("new Map[Int, String](a -> b)")
  syntactic("new B")
  syntactic("new B()")
  syntactic("new c.B")
  syntactic("new C#B")
  syntactic("new o.C#B")
  syntactic("new B { }")
  syntactic("new B { val a = 3 }")
  syntactic("new B { def f(x: Int): Int = x*x }")
  syntactic("new B(3) { println(5); def f(x: Int): Int = x*x }")
  syntactic("throw new A(4)")
  syntactic("try { throw new A(4) } catch { case _: Throwable => 4 } finally { println(6) }")
  syntactic("try f(4) catch { case _: Throwable => 4 } finally println(6)")
  syntactic("try f(4) catch { case _: Throwable => 4 }")
  syntactic("try f(4) finally println(6)")
  syntactic("try {} finally println(6)")
  // TODO: https://github.com/scalameta/paradise/issues/75
  // syntactic("try foo catch bar")
  // TODO: https://github.com/scalameta/paradise/issues/74
  // syntactic("for (arg <- args) result += arg * arg")
  // syntactic("for (arg <- args; double = arg * 2) result += arg * arg")
  // syntactic("""
  //   for { i<-1 until n
  //         j <- 1 until i
  //         if isPrime(i+j) } yield (i, j)
  // """)
  // syntactic("""
  //   for { i<-1 until n
  //         j <- 1 until i
  //         k = i + j
  //         if isPrime(i+j) } yield (i, j)
  // """)

  // interpolation
  // TODO: https://github.com/scalameta/paradise/issues/76
  // syntactic("""s"hello, $world"""")
  // syntactic("""s"hello, $world, ${1 + 2}"""")

  // random stuff
  syntactic("case class C()")
  syntactic("object M { override val toString = test5 }")
  syntactic("foo(named = arg)")
  syntactic("""
    1 match {
      case 0 | 1           => true
      case (2 | 3 | 4 | 5) => false
    }
  """)
  syntactic("def add(a: Int)(implicit z: Int = 0) = a + z")
}