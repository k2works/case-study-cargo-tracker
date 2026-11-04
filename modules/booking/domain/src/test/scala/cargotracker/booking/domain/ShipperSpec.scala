package cargotracker.booking.domain

import cargotracker.sharedkernel.{Clock, InvalidValue, Ulid}
import java.time.Instant
import java.util.Random
import org.scalatest.funsuite.AnyFunSuite

/** 荷主（U-02。CRUD の集約。ドメインモデル 5.2）。Bolt 1 は個人荷主のみ（法人は Bolt 13）。 */
class ShipperSpec extends AnyFunSuite:

  private val clock = Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"))
  private val rnd = new Random(1)
  private def newId() = ShipperId(Ulid.generate(clock, rnd))

  private def register(
      name: String = "山田 太郎",
      address: String = "東京都千代田区丸の内 1-1-1",
      contact: String = "03-1234-5678",
      email: String = "taro@example.com",
      duplicateOfEmail: Option[ShipperId] = None,
      acknowledgedDuplicate: Boolean = false
  ): Either[ShipperError, Shipper] =
    for
      n <- ShipperName.parse(name).left.map(ShipperError.Invalid.apply)
      a <- Address.parse(address).left.map(ShipperError.Invalid.apply)
      c <- ContactNumber.parse(contact).left.map(ShipperError.Invalid.apply)
      e <- EmailAddress.parse(email).left.map(ShipperError.Invalid.apply)
      s <- Shipper.register(
        newId(),
        n,
        a,
        c,
        e,
        ShipperKind.Individual,
        duplicateOfEmail,
        acknowledgedDuplicate
      )
    yield s

  test("US02-AC01: 個人の荷主を登録でき、荷主 ID が発行されて種別は個人になる") {
    val s = register().toOption.get
    assert(s.kind == ShipperKind.Individual)
    assert(s.id.value.value.length == 26)
    assert(s.name.value == "山田 太郎")
  }

  test("US02-AC01: メールアドレスは前後の空白を除き、小文字にそろえて持つ") {
    val s = register(email = "  Taro@Example.COM ").toOption.get
    assert(s.email.value == "taro@example.com")
  }

  test("氏名・住所・連絡先・メールアドレスが空なら拒む（項目名が分かる）") {
    assert(register(name = " ") == Left(ShipperError.Invalid(InvalidValue("name", "空にできない"))))
    assert(register(address = "").left.exists(_.isInstanceOf[ShipperError.Invalid]))
    assert(register(contact = "").left.exists(_.isInstanceOf[ShipperError.Invalid]))
    assert(register(email = "").left.exists(_.isInstanceOf[ShipperError.Invalid]))
  }

  test("メールアドレスの形式が正しくなければ拒む") {
    List("taro", "taro@", "@example.com", "taro@example", "ta ro@example.com", "a@b@c.com")
      .foreach { bad =>
        assert(EmailAddress.parse(bad).isLeft, s"拒むべき: '$bad'")
      }
  }

  test("US02-AC02: 同じメールアドレスの荷主が登録済みなら、既存の荷主を示して登録しない") {
    val existing = newId()
    val r = register(duplicateOfEmail = Some(existing))
    assert(r == Left(ShipperError.DuplicateEmail(existing)))
  }

  test("US02-AC02: 重複を承知して「登録を続ける」を選べば、登録できる（一意制約にしない）") {
    val existing = newId()
    val r = register(duplicateOfEmail = Some(existing), acknowledgedDuplicate = true)
    assert(r.isRight)
  }

  test("重複が無いときは、承知の有無に関わらず登録できる") {
    assert(register(duplicateOfEmail = None, acknowledgedDuplicate = true).isRight)
    assert(register(duplicateOfEmail = None, acknowledgedDuplicate = false).isRight)
  }
