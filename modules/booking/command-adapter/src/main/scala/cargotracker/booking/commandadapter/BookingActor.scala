package cargotracker.booking.commandadapter

import cargotracker.booking.domain.*
import cargotracker.booking.usecase.CommandResult
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

/** 貨物予約の集約アクター。ドメイン（純粋）が決めた結果を、永続化してから返信する。 拒否（業務エラー）は永続化しない。ドメインは Pekko を知らない（A3）。
  */
object BookingActor:

  /** 読み取り側（イベント処理）が追いかけるためのタグ。 */
  val Tag = "booking"

  type Reply = Either[BookingError, CommandResult]

  final case class Handle(command: BookingCommand, replyTo: ActorRef[Reply])

  /** 永続化 ID は `<集約の型>|<ID>`（ADR-001。参照実装の `UserAccount-<ULID>` に相当）。 */
  def persistenceId(id: BookingId): PersistenceId = PersistenceId("Booking", id.value.value, "|")

  def apply(id: BookingId): Behavior[Handle] =
    Behaviors.setup { context =>
      EventSourcedBehavior[Handle, BookingEvent, BookingState](
        persistenceId = persistenceId(id),
        emptyState = BookingState.NotCreated,
        commandHandler = (state, msg) =>
          Booking.handle(state, msg.command) match
            case Left(error)       => Effect.reply(msg.replyTo)(Left(error))
            case Right((_, event)) =>
              Effect
                .persist(event)
                .thenReply(msg.replyTo)(_ =>
                  Right(CommandResult(EventSourcedBehavior.lastSequenceNumber(context)))
                ),
        eventHandler = Booking.evolve
      ).withTagger(_ => Set(Tag))
    }
