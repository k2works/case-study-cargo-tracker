package cargotracker.app

import cargotracker.booking.commandadapter.*
import cargotracker.booking.queryadapter.*
import cargotracker.booking.usecase.*
import cargotracker.sharedkernel.Clock
import com.typesafe.config.{Config, ConfigFactory}
import java.time.Duration
import org.apache.pekko.actor.typed.{ActorSystem, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.persistence.testkit.PersistenceTestKitPlugin
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import slick.jdbc.PostgresProfile.api.*

final class RunningServer(val port: Int, val stop: () => Future[Unit])

/** ローカルで動かす実行物（結線の根）。command・query・イベント処理を 1 つの JVM に載せる。 Journal はインメモリ（再起動で消える）。永続化は SP-1
  * の結果で決める（ADR-002 の見直し）。 認証は開発用のスタブ（既定は無効）。マイグレーションは自動では適用しない（R-03。適用は運用タスクで承認後）。
  */
object LocalServer:

  /** インメモリ Journal（pekko-persistence-testkit）と、イベントの Protobuf シリアライザ。Java シリアライズは禁止。 */
  def actorSystemConfig: Config =
    PersistenceTestKitPlugin.config.withFallback(
      ConfigFactory.parseString("""
        pekko.actor.allow-java-serialization = off
        pekko.actor.serializers.booking-event = "cargotracker.booking.commandadapter.BookingEventPekkoSerializer"
        pekko.actor.serialization-bindings."cargotracker.booking.domain.BookingEvent" = booking-event
        pekko.persistence.testkit.events.serialize = true
        pekko.loglevel = "INFO"
      """)
    )

  def start(config: ServerConfig, db: Database)(using
      system: ActorSystem[?]
  ): Future[RunningServer] =
    given ExecutionContext = system.executionContext
    val clock = Clock.system
    val lagRecorder: LagRecorder = (lag: Duration) =>
      system.log.info("reflection_lag_ms={}", Long.box(lag.toMillis))

    // 書き込み側
    val shippers = new SlickShipperRepository(db)
    val idem = new SlickIdempotencyStore(db, clock)
    val ids = new SecureIdGenerator(clock)
    val aggregates = new PekkoBookingAggregates(system, 5.seconds)
    val numbers = new SequenceBookingNumberIssuer(db)
    val mutation = new BookingMutationGraphQL(
      new RegisterShipper(shippers, idem, ids, clock),
      new RegisterBooking(shippers, new SlickPortCatalog(db), idem, aggregates, numbers, ids, clock)
    )
    // 読み取り側
    val queries = new BookingGraphQL(
      new QueryService(new ShipperQueries(db), new BookingQueries(db))
    )
    // イベント処理
    val processor = new EventProcessor(
      system,
      new BookingProjection(db, clock, lagRecorder),
      new SlickDeadLetters(db, clock),
      maxAttempts = 5,
      backoff = 200.millis,
      pollInterval = 200.millis
    )

    val api = new HttpApi(config.devAuthEnabled, mutation.execute, queries.execute)
    for
      _ <- requireSchema(db)
      running = processor.start()
      binding <- Http().newServerAt(config.httpHost, config.httpPort).bind(api.routes)
    yield new RunningServer(
      binding.localAddress.getPort,
      () =>
        running.stop()
        binding.unbind().map(_ => ())
    )

  /** マイグレーションが適用済みか。未適用なら、何をすべきかを示して失敗する。 */
  private def requireSchema(db: Database)(using ExecutionContext): Future[Unit] =
    db.run(sql"SELECT 1 FROM booking.booking_view LIMIT 0".as[Int]).map(_ => ()).recoverWith {
      case e =>
        Future.failed(
          new IllegalStateException(s"booking スキーマがありません。マイグレーションを適用してください（${e.getMessage}）", e)
        )
    }

  def main(args: Array[String]): Unit =
    ServerConfig.fromEnv() match
      case Left(why) =>
        System.err.println(s"設定が足りません: $why")
        sys.exit(2)
      case Right(config) =>
        given system: ActorSystem[Nothing] =
          ActorSystem(Behaviors.empty, "cargo-tracker", actorSystemConfig)
        val db = Database.forURL(
          config.dbUrl,
          config.dbUser,
          config.dbPassword,
          driver = "org.postgresql.Driver"
        )
        LocalServer
          .start(config, db)
          .failed
          .foreach { e =>
            System.err.println(s"起動に失敗しました: ${e.getMessage}")
            system.terminate()
          }(using system.executionContext)
