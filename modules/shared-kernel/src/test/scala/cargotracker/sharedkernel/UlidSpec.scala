package cargotracker.sharedkernel

import java.time.Instant
import java.util.Random
import org.scalatest.funsuite.AnyFunSuite

class UlidSpec extends AnyFunSuite:

  private val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toSet
  private def at(s: String) = Clock.fixed(Instant.parse(s))

  test("生成した ULID は 26 文字で、Crockford Base32 の文字だけを使う") {
    val id = Ulid.generate(at("2026-11-04T00:00:00Z"), new Random(1))
    assert(id.value.length == 26)
    assert(id.value.forall(alphabet.contains))
  }

  test("先頭 10 文字は時刻（48 ビット）で、先頭の文字は 7 以下") {
    val id = Ulid.generate(at("2026-11-04T00:00:00Z"), new Random(1))
    assert(id.value.head <= '7')
  }

  test("時刻が後の ULID は、文字列として大きい（時系列に並ぶ）") {
    val rnd = new Random(7)
    val early = Ulid.generate(at("2026-11-04T00:00:00Z"), rnd)
    val late = Ulid.generate(at("2026-11-04T00:00:01Z"), rnd)
    assert(early.value < late.value)
  }

  test("同じ時刻でも、乱数が違えば別の ULID になる") {
    val rnd = new Random(7)
    val a = Ulid.generate(at("2026-11-04T00:00:00Z"), rnd)
    val b = Ulid.generate(at("2026-11-04T00:00:00Z"), rnd)
    assert(a != b)
    assert(a.value.take(10) == b.value.take(10))
  }

  test("同じ時刻・同じ乱数の種からは、同じ ULID ができる（テストで再現できる）") {
    val a = Ulid.generate(at("2026-11-04T00:00:00Z"), new Random(42))
    val b = Ulid.generate(at("2026-11-04T00:00:00Z"), new Random(42))
    assert(a == b)
  }

  test("parse: 正しい ULID を受け取り、小文字は大文字にそろえる") {
    val id = Ulid.generate(at("2026-11-04T00:00:00Z"), new Random(1))
    assert(Ulid.parse(id.value) == Right(id))
    assert(Ulid.parse(id.value.toLowerCase) == Right(id))
  }

  test("parse: 長さが違う、使えない文字（I L O U）を含む、先頭が 7 を超えるものは拒む") {
    val ok = "01ARZ3NDEKTSV4RRFFQ69G5FAV"
    assert(Ulid.parse(ok).isRight)
    List(
      "",
      ok.dropRight(1),
      ok + "0",
      ok.updated(5, 'I'),
      ok.updated(5, 'L'),
      ok.updated(5, 'O'),
      ok.updated(5, 'U'),
      "8" + ok.drop(1),
      ok.updated(5, '-')
    ).foreach(bad => assert(Ulid.parse(bad).isLeft, s"拒むべき: '$bad'"))
  }

  test("parse の拒否理由は、項目名を持つ業務エラーで返る") {
    assert(Ulid.parse("x") == Left(InvalidValue("ulid", "26 文字の Crockford Base32 ではない")))
  }
