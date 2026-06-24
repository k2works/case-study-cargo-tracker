package cargotracker.billing.infrastructure.scheduler

import cargotracker.billing.application.commandservices.BillingCommandService
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.inject.{ApplicationLifecycle, DefaultApplicationLifecycle}

import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

/** IT9 0.3: OverdueDetectionScheduler の computeInitialDelay ロジックを検証する。
  *
  * 実 Pekko スケジューラの起動 / 停止は IntegrationSpec で確認 (IT10 申し送り)。 本 Spec はピュアな時刻計算ロジックの境界値検証に絞る (Unit テスト)。
  */
class OverdueDetectionSchedulerSpec extends AnyFunSuite with Matchers:

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private val zoneTokyo: ZoneId = ZoneId.of("Asia/Tokyo")
  private val actorSystem: ActorSystem = ActorSystem("OverdueSchedulerSpec")
  private val lifecycle: ApplicationLifecycle = new DefaultApplicationLifecycle

  private def buildScheduler(now: Instant, enabled: Boolean = false): OverdueDetectionScheduler =
    val cfg = Configuration(
      ConfigFactory.parseString(
        s"""
          |billing.overdue.enabled = $enabled
          |billing.overdue.cron-hour = 2
          |billing.overdue.cron-minute = 0
          |billing.overdue.zone-id = "Asia/Tokyo"
          |""".stripMargin
      )
    )
    val clock = Clock.fixed(now, zoneTokyo)
    new OverdueDetectionScheduler(actorSystem, cfg, (_: java.time.LocalDate) => 0, clock, lifecycle)

  test("computeInitialDelay: 今日 01:00 JST → 02:00 JST まで 1 時間"):
    // 2026-10-15 01:00:00 JST = 2026-10-14T16:00:00Z
    val scheduler = buildScheduler(Instant.parse("2026-10-14T16:00:00Z"))
    val delay = scheduler.computeInitialDelay()
    delay shouldBe 1.hour

  test("computeInitialDelay: 今日 02:00 JST 丁度 → 翌日 02:00 JST まで 24 時間"):
    // 2026-10-15 02:00:00 JST = 2026-10-14T17:00:00Z
    val scheduler = buildScheduler(Instant.parse("2026-10-14T17:00:00Z"))
    val delay = scheduler.computeInitialDelay()
    delay shouldBe 24.hours

  test("computeInitialDelay: 今日 10:00 JST (起動時刻を過ぎた) → 翌日 02:00 JST まで 16 時間"):
    // 2026-10-15 10:00:00 JST = 2026-10-15T01:00:00Z
    val scheduler = buildScheduler(Instant.parse("2026-10-15T01:00:00Z"))
    val delay = scheduler.computeInitialDelay()
    delay shouldBe 16.hours

  test("computeInitialDelay: 今日 23:00 JST → 翌日 02:00 JST まで 3 時間"):
    // 2026-10-15 23:00:00 JST = 2026-10-15T14:00:00Z
    val scheduler = buildScheduler(Instant.parse("2026-10-15T14:00:00Z"))
    val delay = scheduler.computeInitialDelay()
    delay shouldBe 3.hours
