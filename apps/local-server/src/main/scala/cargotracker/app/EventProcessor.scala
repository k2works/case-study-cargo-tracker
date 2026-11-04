package cargotracker.app

import cargotracker.booking.commandadapter.{BookingActor, BookingEventSerializer}
import cargotracker.booking.domain.BookingEvent
import cargotracker.booking.queryadapter.{BookingProjection, ProjectedEvent, ProjectionResult}
import cargotracker.sharedkernel.{Clock, Ulid}
import java.sql.Timestamp
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.pattern.after
import org.apache.pekko.persistence.query.{EventEnvelope, Offset, PersistenceQuery}
import org.apache.pekko.persistence.testkit.query.scaladsl.PersistenceTestKitReadJournal
import org.apache.pekko.stream.{KillSwitches, Materializer, RestartSettings}
import org.apache.pekko.stream.scaladsl.{RestartSource, Sink, Source}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}
import slick.jdbc.PostgresProfile.api.*
import slick.jdbc.{PositionedParameters, SetParameter}

/** 投影できなかったイベントの待避先（`platform.dead_letter_event`）。黙って捨てない（バックエンド 6.2）。 */
trait DeadLetters:
  def record(
      persistenceId: String,
      sequenceNr: Long,
      event: Option[BookingEvent],
      error: String,
      attempts: Int
  ): Future[Unit]

final class SlickDeadLetters(db: Database, clock: Clock)(using ExecutionContext)
    extends DeadLetters:
  private val random = new java.security.SecureRandom()
  // Scala 3 では Array[Byte] の束縛を明示する（Slick の暗黙の変換が合わないため）
  private given SetParameter[Array[Byte]] = new SetParameter[Array[Byte]]:
    def apply(v: Array[Byte], pp: PositionedParameters): Unit = pp.setBytes(v)
  def record(
      persistenceId: String,
      sequenceNr: Long,
      event: Option[BookingEvent],
      error: String,
      attempts: Int
  ): Future[Unit] =
    val payload = event.map(BookingEventSerializer.serialize).getOrElse(Array.emptyByteArray)
    val eventId = event.map(_.meta.eventId).getOrElse(Ulid.generate(clock, random)).value
    db.run(
      sqlu"""INSERT INTO platform.dead_letter_event
               (dead_letter_id, projection_name, event_id, persistence_id, seq_nr, payload, error, attempts, failed_at)
             VALUES (${Ulid
          .generate(clock, random)
          .value}, 'booking-view', $eventId, $persistenceId, $sequenceNr,
                     $payload, $error, $attempts, ${Timestamp.from(clock.now())})
             ON CONFLICT (projection_name, persistence_id, seq_nr) DO NOTHING"""
    ).map(_ => ())

/** イベント処理：読み取り Journal のイベントを順に投影する。 失敗は `maxAttempts`
  * 回までリトライし、それでも駄目なら待避先に記録して**次のイベントへ進む**（停止しない）。 ストリームが落ちたら、バックオフで先頭から読み直す（投影は冪等なので安全。A6）。
  */
final class EventProcessor(
    system: ActorSystem[?],
    projection: BookingProjection,
    deadLetters: DeadLetters,
    maxAttempts: Int,
    backoff: FiniteDuration,
    pollInterval: FiniteDuration
)(using ExecutionContext):

  private given Materializer = Materializer(system)
  private val readJournal =
    PersistenceQuery(system).readJournalFor[PersistenceTestKitReadJournal](
      PersistenceTestKitReadJournal.Identifier
    )

  final class Running(killSwitch: org.apache.pekko.stream.SharedKillSwitch):
    def stop(): Unit = killSwitch.shutdown()

  /** 1 回分：現在のイベントを読み、まだ処理していないものを順に処理して、処理済みの連番を更新する。 ライブ追尾（`eventsByTag`）は、インメモリ Journal では 2
    * 件目以降が届かない。また、このインメモリ Journal の `offset`
    * は全体の位置ではなく**集約ごとの連番**なので、位置では絞れない。そのため、**集約（persistenceId）ごとの
    * 処理済みの連番**を持ち、現在のイベントを読み直して新しいものだけを処理する（ポーリング）。 実 Journal（SP-1
    * の後に決める）では、全体の位置（オフセット）で続きから読む方式に置き換える。
    */
  private def step(done: Map[String, Long]): Future[Option[(Map[String, Long], Unit)]] =
    readJournal
      .currentEventsByTag(BookingActor.Tag, Offset.noOffset)
      .runWith(Sink.seq)
      .flatMap { all =>
        val fresh = all.filter(e => e.sequenceNr > done.getOrElse(e.persistenceId, 0L))
        fresh
          .foldLeft(Future.successful(done)) { (acc, envelope) =>
            acc.flatMap(d =>
              handle(envelope).map(_ => d.updated(envelope.persistenceId, envelope.sequenceNr))
            )
          }
          .flatMap { updated =>
            if fresh.isEmpty then
              after(pollInterval)(Future.successful(Some((updated, ()))))(using system)
            else Future.successful(Some((updated, ())))
          }
      }

  private def handle(envelope: EventEnvelope): Future[Unit] =
    envelope.event match
      case e: BookingEvent =>
        attempt(envelope.persistenceId, ProjectedEvent(e, envelope.sequenceNr), 1)
      case other =>
        deadLetters.record(
          envelope.persistenceId,
          envelope.sequenceNr,
          None,
          s"未知のイベントの型: ${other.getClass.getName}",
          1
        )

  def start(): Running =
    val killSwitch =
      KillSwitches.shared(s"event-processor-${EventProcessor.counter.incrementAndGet()}")
    RestartSource
      // 落ちたら、バックオフで先頭から読み直す（投影は冪等なので安全。A6）
      .onFailuresWithBackoff(RestartSettings(1.second, 30.seconds, 0.2))(() =>
        Source.unfoldAsync(Map.empty[String, Long])(step)
      )
      .via(killSwitch.flow)
      .runWith(Sink.ignore)
    new Running(killSwitch)

  private def attempt(persistenceId: String, projected: ProjectedEvent, n: Int): Future[Unit] =
    projection(projected).transformWith {
      case Success(ProjectionResult.Applied | ProjectionResult.AlreadyApplied) => Future.unit
      case Success(ProjectionResult.Gap(expected, actual))                     =>
        retryOrGiveUp(persistenceId, projected, n, s"版が飛んでいる（期待 $expected、実際 $actual）")
      case Failure(e) => retryOrGiveUp(persistenceId, projected, n, e.toString)
    }

  private def retryOrGiveUp(
      persistenceId: String,
      projected: ProjectedEvent,
      n: Int,
      error: String
  ): Future[Unit] =
    if n < maxAttempts then
      after(backoff * n)(attempt(persistenceId, projected, n + 1))(using system)
    else deadLetters.record(persistenceId, projected.sequenceNr, Some(projected.event), error, n)

object EventProcessor:
  private val counter = new java.util.concurrent.atomic.AtomicInteger(0)
