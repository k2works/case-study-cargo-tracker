package cargotracker.auth.domain

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UserSpec extends AnyFunSuite with Matchers:

  private val sampleHash =
    PasswordHash.fromPlain("Sup3rSecret!").toOption.get

  test("有効なユーザー名・メール・パスワードハッシュ・ロール集合でユーザーを生成できる"):
    val res = User.create(
      username = "sales01",
      email = "sales01@example.com",
      password = sampleHash,
      roles = Set(Role.Sales)
    )

    res.isRight shouldBe true
    val user = res.toOption.get
    user.username shouldBe "sales01"
    user.email shouldBe "sales01@example.com"
    user.hasRole(Role.Sales) shouldBe true
    user.enabled shouldBe true

  test("ロール集合が空のユーザーは拒否される"):
    val res = User.create("admin", "a@x.com", sampleHash, Set.empty)
    res shouldBe Left(User.RolesRequired)

  test("メールアドレス形式が不正なユーザーは拒否される"):
    val res = User.create("u", "not-an-email", sampleHash, Set(Role.Sales))
    res shouldBe Left(User.InvalidEmail)

  test("ユーザー名が空白のみのユーザーは拒否される"):
    val res = User.create("   ", "u@x.com", sampleHash, Set(Role.Sales))
    res shouldBe Left(User.InvalidUsername)

  test("認証は平文パスワードがハッシュと一致する場合のみ成功する"):
    val user = User
      .create("u", "u@x.com", sampleHash, Set(Role.Sales))
      .toOption
      .get

    user.authenticate("Sup3rSecret!") shouldBe true
    user.authenticate("Wrong!Pass1") shouldBe false

  test("無効化されたユーザーは認証成功しても失敗扱いになる"):
    val user = User
      .create("u", "u@x.com", sampleHash, Set(Role.Sales))
      .toOption
      .get
      .disable

    user.enabled shouldBe false
    user.authenticate("Sup3rSecret!") shouldBe false

  test("複数ロールを持つユーザーは個別のロールチェックを通過する"):
    val user = User
      .create(
        "designer",
        "rd@x.com",
        sampleHash,
        Set(Role.RouteDesigner, Role.Tracker)
      )
      .toOption
      .get

    user.hasRole(Role.RouteDesigner) shouldBe true
    user.hasRole(Role.Tracker) shouldBe true
    user.hasRole(Role.Sales) shouldBe false
