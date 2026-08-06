package cargotracker.routing.domain.model.aggregates

import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, Schedule, VoyageNumber}
import cargotracker.shared.domain.Location
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class VoyageSpec extends AnyFunSuite with Matchers:

  private val voyageNumber = VoyageNumber.unsafeFrom("VY-001")
  private val tyo = Location.unsafeFrom("JPTYO")
  private val yok = Location.unsafeFrom("JPYOK")
  private val lax = Location.unsafeFrom("USLAX")
  private val schedule = Schedule(
    List(
      CarrierMovement(
        tyo,
        yok,
        Instant.parse("2026-07-01T10:00:00Z"),
        Instant.parse("2026-07-01T18:00:00Z")
      ).toOption.get
    )
  ).toOption.get

  test("register で version 0 の Voyage を生成する"):
    val v = Voyage.register(voyageNumber, schedule, "MV Sample", "MAEU", Set.empty)
    v.voyageNumber shouldBe voyageNumber
    v.version shouldBe 0

  test("updateSchedule で schedule を差し替えた新インスタンスを返す"):
    val v0 = Voyage.register(voyageNumber, schedule, "MV", "CC", Set.empty)
    val newSchedule = Schedule(
      List(
        CarrierMovement(
          tyo,
          yok,
          Instant.parse("2026-07-02T10:00:00Z"),
          Instant.parse("2026-07-02T18:00:00Z")
        ).toOption.get,
        CarrierMovement(
          yok,
          lax,
          Instant.parse("2026-07-03T08:00:00Z"),
          Instant.parse("2026-07-10T20:00:00Z")
        ).toOption.get
      )
    ).toOption.get
    val v1 = v0.updateSchedule(newSchedule)
    v1.voyageNumber shouldBe voyageNumber
    v1.schedule.destination shouldBe lax
    v1.version shouldBe 0 // IT2: 集約に version 活性化は IT3

  test("reconstruct で永続化から復元できる"):
    val v = Voyage.reconstruct(voyageNumber, schedule, "MV Sample", "MAEU", Set.empty, version = 3)
    v.version shouldBe 3
