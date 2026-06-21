package cargotracker.auth.domain.model.valueobjects

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PasswordHashSpec extends AnyFunSuite with Matchers:

  test("正しい平文パスワードからハッシュを生成し、同じ平文で検証できる"):
    val raw = "Sup3rSecret!"
    val hashRes = PasswordHash.fromPlain(raw)

    hashRes.isRight shouldBe true
    val hash = hashRes.toOption.get
    hash.matches(raw) shouldBe true

  test("生成したハッシュは別の平文では一致しない"):
    val hash = PasswordHash.fromPlain("CorrectPass1").toOption.get
    hash.matches("WrongPass1!") shouldBe false

  test("空のパスワードは DomainError として拒否される"):
    PasswordHash.fromPlain("") shouldBe Left(PasswordHash.PasswordTooShort)

  test("8 文字未満のパスワードは拒否される"):
    PasswordHash.fromPlain("Short1") shouldBe Left(PasswordHash.PasswordTooShort)

  test("保存済みハッシュ文字列から復元できる（永続化からの読み戻し）"):
    val original = PasswordHash.fromPlain("Sup3rSecret!").toOption.get
    val restored = PasswordHash.unsafeFromHash(original.value)
    restored.matches("Sup3rSecret!") shouldBe true
