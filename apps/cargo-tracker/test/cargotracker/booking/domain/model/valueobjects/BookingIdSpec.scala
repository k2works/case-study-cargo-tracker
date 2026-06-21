package cargotracker.booking.domain.model.valueobjects

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BookingIdSpec extends AnyFunSuite with Matchers:

  test("BK-NNNNNN 形式の文字列から BookingId を生成できる"):
    val res = BookingId("BK-000001")
    res.isRight shouldBe true
    res.toOption.get.value shouldBe "BK-000001"

  test("形式不正の文字列は InvalidFormat を返す"):
    BookingId("000001") shouldBe Left(BookingId.InvalidFormat)
    BookingId("bk-000001") shouldBe Left(BookingId.InvalidFormat)

  test("空文字列は EmptyValue を返す"):
    BookingId("") shouldBe Left(BookingId.EmptyValue)

  test("永続化からの読み戻し用 unsafeFrom はバリデーションを省く"):
    BookingId.unsafeFrom("anything").value shouldBe "anything"

  test("BookingStatus は文字列名で双方向変換できる"):
    BookingStatus.fromName("Preliminary") shouldBe Some(
      BookingStatus.Preliminary
    )
    BookingStatus.Preliminary.toString shouldBe "Preliminary"

  test("RouteAssigned からは Confirmed / Cancelled / RouteProposed に遷移できる（US13 / IT4 タスク 4.1）"):
    BookingStatus.RouteAssigned.canTransitionTo(BookingStatus.Confirmed) shouldBe true
    BookingStatus.RouteAssigned.canTransitionTo(BookingStatus.Cancelled) shouldBe true
    BookingStatus.RouteAssigned.canTransitionTo(BookingStatus.RouteProposed) shouldBe true

  test("RouteAssigned から TrackingIssued や InTransit には直接遷移できない"):
    BookingStatus.RouteAssigned.canTransitionTo(BookingStatus.TrackingIssued) shouldBe false
    BookingStatus.RouteAssigned.canTransitionTo(BookingStatus.InTransit) shouldBe false

  test("Confirmed / Cancelled は終端方向にしか遷移しない"):
    BookingStatus.Confirmed.canTransitionTo(BookingStatus.TrackingIssued) shouldBe true
    BookingStatus.Confirmed.canTransitionTo(BookingStatus.Cancelled) shouldBe true
    BookingStatus.Confirmed.canTransitionTo(BookingStatus.RouteAssigned) shouldBe false
    BookingStatus.Cancelled.canTransitionTo(BookingStatus.Confirmed) shouldBe false
