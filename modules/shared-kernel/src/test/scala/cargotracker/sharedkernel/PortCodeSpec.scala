package cargotracker.sharedkernel

import org.scalatest.funsuite.AnyFunSuite

/** UN/LOCODE は「国 2 文字 + 場所 3 文字（英字と数字 2〜9）」。形式だけを検証する。
  * 実在するコードかどうかは参照データ（reference.port）で確かめる（R-09）。
  */
class PortCodeSpec extends AnyFunSuite:

  test("実在する UN/LOCODE の形式を受け取る") {
    List("JPTYO", "SGSIN", "NLRTM", "CNSHA", "USLAX", "KRPUS", "DEHAM").foreach { code =>
      assert(PortCode.parse(code).map(_.value) == Right(code), code)
    }
  }

  test("小文字と前後の空白は、大文字・空白なしにそろえる") {
    assert(PortCode.parse("  jptyo ").map(_.value) == Right("JPTYO"))
  }

  test("国コードと場所コードを取り出せる") {
    val code = PortCode.parse("JPTYO").toOption.get
    assert(code.country == "JP")
    assert(code.location == "TYO")
  }

  test("場所コードに数字 2〜9 は使える（例：SE2ST のような形式）") {
    assert(PortCode.parse("AA2BC").isRight)
    assert(PortCode.parse("AAB9C").isRight)
  }

  test("長さが違う、数字 0 と 1、記号、国コードに数字を含むものは拒む") {
    List("", "JPTY", "JPTYOO", "JP0YO", "JP1YO", "JP-YO", "J1TYO", "12345", "JP TYO").foreach {
      bad =>
        assert(PortCode.parse(bad).isLeft, s"拒むべき: '$bad'")
    }
  }

  test("拒否理由は、項目名を持つ業務エラーで返る") {
    assert(PortCode.parse("x") == Left(InvalidValue("portCode", "UN/LOCODE（国 2 文字 + 場所 3 文字）ではない")))
  }
