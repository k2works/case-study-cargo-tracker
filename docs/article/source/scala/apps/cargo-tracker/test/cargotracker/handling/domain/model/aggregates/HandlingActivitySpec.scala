package cargotracker.handling.domain.model.aggregates

import cargotracker.handling.domain.model.enums.{HandlingType, RecipientConfirmationType}
import cargotracker.handling.domain.model.valueobjects.HandlingVoyageNumber
import cargotracker.shared.domain.Location
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class HandlingActivitySpec extends AnyFunSuite with Matchers:

  private val now = Instant.parse("2026-08-17T10:00:00Z")
  private val tyo = Location.unsafeFrom("JPTYO")
  private val vy = HandlingVoyageNumber.unsafeFrom("VY-1")

  test("register: Receive は voyageNumber なしで成立する"):
    val Right(ha) = HandlingActivity.register(
      HandlingActivity.RegisterRequest(
        bookingId = "BK-000001",
        eventType = HandlingType.Receive,
        completionTime = now,
        location = tyo,
        voyageNumber = None,
        operatorName = Some("山田")
      )
    ): @unchecked
    ha.eventType shouldBe HandlingType.Receive
    ha.voyageNumber shouldBe None
    ha.operatorName shouldBe Some("山田")
    ha.routeDeviation shouldBe false
    ha.version shouldBe 0

  test("register: Load は voyageNumber 必須"):
    HandlingActivity.register(
      HandlingActivity.RegisterRequest(
        bookingId = "BK-000002",
        eventType = HandlingType.Load,
        completionTime = now,
        location = tyo,
        voyageNumber = None,
        operatorName = None
      )
    ) shouldBe Left(HandlingActivity.VoyageRequired)

  test("register: Load + voyageNumber 提供で成立する"):
    val Right(ha) = HandlingActivity.register(
      HandlingActivity.RegisterRequest(
        bookingId = "BK-000003",
        eventType = HandlingType.Load,
        completionTime = now,
        location = tyo,
        voyageNumber = Some(vy),
        operatorName = None
      )
    ): @unchecked
    ha.eventType shouldBe HandlingType.Load
    ha.voyageNumber shouldBe Some(vy)

  test("register: 空 bookingId は EmptyBookingId"):
    HandlingActivity.register(
      HandlingActivity.RegisterRequest(
        bookingId = "",
        eventType = HandlingType.Receive,
        completionTime = now,
        location = tyo,
        voyageNumber = None,
        operatorName = None
      )
    ) shouldBe Left(HandlingActivity.EmptyBookingId)

  test("register: Claim は recipientConfirmation 必須 (US16 / IT6)"):
    HandlingActivity.register(
      HandlingActivity.RegisterRequest(
        bookingId = "BK-CLAIM01",
        eventType = HandlingType.Claim,
        completionTime = now,
        location = tyo,
        voyageNumber = None,
        operatorName = None,
        recipientConfirmation = None
      )
    ) shouldBe Left(HandlingActivity.RecipientConfirmationRequired)

  test("register: Claim + recipientConfirmation + 種別 提供で成立する (US16 + IT7 0.12)"):
    val Right(ha) = HandlingActivity.register(
      HandlingActivity.RegisterRequest(
        bookingId = "BK-CLAIM02",
        eventType = HandlingType.Claim,
        completionTime = now,
        location = tyo,
        voyageNumber = None,
        operatorName = Some("田中"),
        recipientConfirmation = Some("署名: 山田太郎"),
        recipientConfirmationType = Some(RecipientConfirmationType.Signature)
      )
    ): @unchecked
    ha.eventType shouldBe HandlingType.Claim
    ha.recipientConfirmation shouldBe Some("署名: 山田太郎")
    ha.recipientConfirmationType shouldBe Some(RecipientConfirmationType.Signature)

  test("register: Claim で確認の値はあるが種別が None なら RecipientConfirmationTypeRequired (IT7 0.12 / M6)"):
    HandlingActivity.register(
      HandlingActivity.RegisterRequest(
        bookingId = "BK-CLAIM03",
        eventType = HandlingType.Claim,
        completionTime = now,
        location = tyo,
        voyageNumber = None,
        operatorName = None,
        recipientConfirmation = Some("受領印あり"),
        recipientConfirmationType = None
      )
    ) shouldBe Left(HandlingActivity.RecipientConfirmationTypeRequired)

  test("HandlingType.requiresVoyage: Load/Unload は必須、それ以外は不要"):
    HandlingType.Load.requiresVoyage shouldBe true
    HandlingType.Unload.requiresVoyage shouldBe true
    HandlingType.Receive.requiresVoyage shouldBe false
    HandlingType.Customs.requiresVoyage shouldBe false
    HandlingType.Claim.requiresVoyage shouldBe false
