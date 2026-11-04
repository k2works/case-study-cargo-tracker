package cargotracker.booking.commandadapter

import cargotracker.booking.domain.*
import cargotracker.booking.usecase.{BookingAggregates, CommandResult}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{AskPattern, Behaviors}
import org.apache.pekko.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/** `BookingAggregates` ポートの実装（ローカルモード：集約アクターを 1 つの ActorSystem の中に置く。BE-12）。 予約 ID ごとに子アクターを 1
  * つ作り、コマンドを直列に処理する。クラスターモードへの切り替えは SP-1 の結果で決める。
  */
final class PekkoBookingAggregates(system: ActorSystem[?], timeout: FiniteDuration)
    extends BookingAggregates:
  import PekkoBookingAggregates.*

  private given Timeout = Timeout(timeout)
  private given org.apache.pekko.actor.typed.Scheduler = system.scheduler

  private val registry: ActorRef[Forward] =
    system.systemActorOf(
      Registry(),
      s"booking-registry-${PekkoBookingAggregates.counter.incrementAndGet()}"
    )

  def handle(id: BookingId, command: BookingCommand): Future[Either[BookingError, CommandResult]] =
    AskPattern
      .Askable(registry)
      .ask[BookingActor.Reply](replyTo => Forward(id, BookingActor.Handle(command, replyTo)))

object PekkoBookingAggregates:
  private val counter = new java.util.concurrent.atomic.AtomicInteger(0)

  private final case class Forward(id: BookingId, message: BookingActor.Handle)

  /** 予約 ID ごとの子アクターを持つ（無ければ作る。作り直せば、イベントから復元される）。 */
  private object Registry:
    def apply(): Behavior[Forward] = Behaviors.setup { context =>
      Behaviors.receiveMessage { case Forward(id, message) =>
        val name = s"booking-${id.value.value}"
        val child = context.child(name) match
          case Some(ref) => ref.unsafeUpcast[BookingActor.Handle]
          case None      => context.spawn(BookingActor(id), name)
        child ! message
        Behaviors.same
      }
    }
