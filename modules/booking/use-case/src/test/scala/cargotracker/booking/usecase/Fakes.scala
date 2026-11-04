package cargotracker.booking.usecase

import cargotracker.booking.domain.*
import cargotracker.sharedkernel.{Clock, PortCode, Ulid}
import java.time.Instant
import java.util.Random
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/** 出力ポートのインメモリのフェイク。本物（PostgreSQL・アクター）より甘くしない： 重複キーや存在しない ID は、本物と同じように扱う。
  */
object Fakes:
  given ExecutionContext = ExecutionContext.parasitic

  final class InMemoryShippers extends ShipperRepository:
    val saved: mutable.LinkedHashMap[ShipperId, Shipper] = mutable.LinkedHashMap.empty
    def findByEmail(email: EmailAddress): Future[Option[ShipperId]] =
      Future.successful(saved.values.find(_.email == email).map(_.id))
    def findById(id: ShipperId): Future[Option[Shipper]] = Future.successful(saved.get(id))
    def save(shipper: Shipper, audit: AuditStamp): Future[Unit] = Future.successful {
      saved.update(shipper.id, shipper)
    }

  /** 参照データ（reference.port）の代わり。登録されていないコードは存在しない。 */
  final class KnownPorts(codes: Set[String]) extends PortCatalog:
    def exists(code: PortCode): Future[Boolean] = Future.successful(codes.contains(code.value))

  final class InMemoryIdempotency extends IdempotencyStore:
    private val store = mutable.Map.empty[(Ulid, String), StoredResult]
    def find(owner: Ulid, key: String): Future[Option[StoredResult]] =
      Future.successful(store.get((owner, key)))
    def save(owner: Ulid, key: String, result: StoredResult): Future[Unit] =
      Future.successful { store.update((owner, key), result) }
    def size: Int = store.size

  /** 本物のアクターと同じ規則（Booking.handle）で、イベントを順に積む。 */
  final class InMemoryBookings extends BookingAggregates:
    val events: mutable.Map[BookingId, Vector[BookingEvent]] = mutable.Map.empty
    def handle(
        id: BookingId,
        command: BookingCommand
    ): Future[Either[BookingError, CommandResult]] =
      Future.successful {
        val history = events.getOrElse(id, Vector.empty)
        Booking.handle(Booking.replay(history), command).map { (_, event) =>
          events.update(id, history :+ event)
          CommandResult(lastVersion = history.size + 1L)
        }
      }

  final class SequentialNumbers extends BookingNumberIssuer:
    var issued: Int = 0
    def next(): Future[BookingNumber] = Future.successful {
      issued += 1
      BookingNumber.parse(f"BK-$issued%06d").toOption.get
    }

  final class SeededIds(clock: Clock, seed: Long = 11L) extends IdGenerator:
    private val rnd = new Random(seed)
    def next(): Ulid = Ulid.generate(clock, rnd)
