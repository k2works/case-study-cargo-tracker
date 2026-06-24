package cargotracker.billing.infrastructure.scheduler

import cargotracker.billing.application.commandservices.BillingCommandService
import org.apache.pekko.actor.ActorSystem
import play.api.{Configuration, Logger}
import play.api.inject.ApplicationLifecycle

import java.time.{Clock, LocalDate, LocalTime, ZoneId, ZonedDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

/** IT9 0.3: 期限超過 Invoice 検出を Pekko Scheduler で日次起動する (US23 / T8 解消)。
  *
  *   - 起動時刻は `billing.overdue.cron-hour` / `cron-minute` (デフォルト 02:00 JST、低負荷時間帯)
  *   - `billing.overdue.enabled = false` で無効化可 (テスト環境 / 開発時)
  *   - 起動間隔は 24 時間固定 (1 日 1 回)、起動時刻が過去なら翌日に skip
  *   - 失敗時はログのみ、次回起動で再試行 (べき等性: markOverdue は同日複数回実行でも結果同一)
  *
  * 設定例 (application.conf):
  * ```
  * billing.overdue {
  *   enabled = true
  *   cron-hour = 2
  *   cron-minute = 0
  *   zone-id = "Asia/Tokyo"
  * }
  * ```
  */
@Singleton
class OverdueDetectionScheduler @Inject() (
    actorSystem: ActorSystem,
    config: Configuration,
    billingCommandService: BillingCommandService,
    clock: Clock,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext):

  private val logger: Logger = Logger("billing.scheduler.overdue")

  private val enabled: Boolean = config.getOptional[Boolean]("billing.overdue.enabled").getOrElse(true)
  private val cronHour: Int = config.getOptional[Int]("billing.overdue.cron-hour").getOrElse(2)
  private val cronMinute: Int = config.getOptional[Int]("billing.overdue.cron-minute").getOrElse(0)
  private val zoneId: ZoneId = ZoneId.of(config.getOptional[String]("billing.overdue.zone-id").getOrElse("Asia/Tokyo"))

  if enabled then start() else logger.info("[OverdueScheduler] disabled by billing.overdue.enabled = false")

  private def start(): Unit =
    val initialDelay = computeInitialDelay()
    logger.info(
      s"[OverdueScheduler] scheduled: first run in ${initialDelay.toMinutes} min, interval 24h (zone=$zoneId, $cronHour:$cronMinute)"
    )
    val cancellable = actorSystem.scheduler.scheduleAtFixedRate(initialDelay, 24.hours)(() => runOnce())
    lifecycle.addStopHook { () =>
      cancellable.cancel()
      logger.info("[OverdueScheduler] cancelled on shutdown")
      scala.concurrent.Future.successful(())
    }

  /** 次回起動時刻までの待機時間を計算する。今日の指定時刻が過去なら翌日に持ち越し。 */
  private[scheduler] def computeInitialDelay(): FiniteDuration =
    val nowZ = ZonedDateTime.ofInstant(clock.instant(), zoneId)
    val targetToday = nowZ.toLocalDate.atTime(LocalTime.of(cronHour, cronMinute)).atZone(zoneId)
    val target = if targetToday.isAfter(nowZ) then targetToday else targetToday.plusDays(1)
    FiniteDuration(java.time.Duration.between(nowZ, target).toMillis, MILLISECONDS)

  /** 1 回分のバッチ実行 (テストから直接呼び出せるよう package-private)。 */
  private[scheduler] def runOnce(): Unit =
    try
      val today = LocalDate.ofInstant(clock.instant(), zoneId)
      val count = billingCommandService.detectOverdue(today)
      if count > 0 then logger.info(s"[OverdueScheduler] detected $count overdue invoice(s) on $today")
      else logger.info(s"[OverdueScheduler] no overdue invoice on $today")
    catch case scala.util.control.NonFatal(e) => logger.error(s"[OverdueScheduler] failed: ${e.getMessage}", e)
