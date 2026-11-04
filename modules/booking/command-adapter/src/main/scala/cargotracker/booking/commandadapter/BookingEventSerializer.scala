package cargotracker.booking.commandadapter

import cargotracker.booking.commandadapter.proto.{booking_event as pb}
import cargotracker.booking.domain.*
import cargotracker.sharedkernel.{InvalidValue, PortCode, Ulid}
import com.google.protobuf.ByteString
import java.time.{Instant, LocalDate}
import scala.util.Try

enum EventSerializationError:
  /** 知らない型名・版。握りつぶさない（旧版を読む変換を足すか、新しい版を知らない旧ノードへの配布を止める）。 */
  case UnknownEvent(typeName: String, version: Int)
  case Malformed(reason: String)

/** 予約イベントの直列化（Protobuf の Envelope。型名と版を持つ）。 */
object BookingEventSerializer:

  private val RegisteredName = "BookingRegistered"
  private val RegisteredVersion = 1

  def manifest(event: BookingEvent): String = event match
    case _: BookingEvent.BookingRegistered => s"${RegisteredName}_V$RegisteredVersion"

  def serialize(event: BookingEvent): Array[Byte] = event match
    case e: BookingEvent.BookingRegistered =>
      envelopeBytes(RegisteredName, RegisteredVersion, toProto(e).toByteArray)

  def deserialize(bytes: Array[Byte]): Either[EventSerializationError, BookingEvent] =
    Try(pb.EventEnvelope.parseFrom(bytes)).toEither.left
      .map(e => EventSerializationError.Malformed(e.getMessage))
      .flatMap { env =>
        (env.typeName, env.typeVersion) match
          case (RegisteredName, RegisteredVersion) =>
            Try(pb.BookingRegisteredV1.parseFrom(env.payload.toByteArray)).toEither.left
              .map(e => EventSerializationError.Malformed(e.getMessage))
              .flatMap(fromProto)
          case (name, version) => Left(EventSerializationError.UnknownEvent(name, version))
      }

  /** Envelope のバイト列を作る（未知の型・版の検査用にも使う）。 */
  def envelopeBytes(typeName: String, version: Int, payload: Array[Byte]): Array[Byte] =
    pb.EventEnvelope(typeName, version, ByteString.copyFrom(payload)).toByteArray

  /** 個人情報の項目が無いことの検査用。Protobuf のメッセージの項目名。 */
  def protoFieldNames(typeName: String): List[String] = typeName match
    case RegisteredName => pb.BookingRegisteredV1.scalaDescriptor.fields.map(_.name).toList
    case _              => Nil

  private def toProto(e: BookingEvent.BookingRegistered): pb.BookingRegisteredV1 =
    val common = e.cargo match
      case CargoSpec.General(c) => c
    pb.BookingRegisteredV1(
      eventId = e.meta.eventId.value,
      occurredAtSeconds = e.meta.occurredAt.getEpochSecond,
      occurredAtNanos = e.meta.occurredAt.getNano,
      bookingId = e.id.value.value,
      bookingNumber = e.number.value,
      shipperId = e.shipper.id.value.value,
      shipperKind = e.shipper.kind.toString.toUpperCase,
      cargoKind = "GENERAL",
      itemName = common.itemName,
      weightKg = common.weightKg.bigDecimal.toPlainString,
      lengthCm = common.dimensions.lengthCm.bigDecimal.toPlainString,
      widthCm = common.dimensions.widthCm.bigDecimal.toPlainString,
      heightCm = common.dimensions.heightCm.bigDecimal.toPlainString,
      quantity = common.quantity,
      originPort = e.transport.origin.value,
      destinationPort = e.transport.destination.value,
      preferredHandover = e.transport.preferredHandover.toString,
      deadline = e.transport.deadline.toString,
      actorUserId = e.meta.actor.map(_.value).getOrElse("")
    )

  private def fromProto(p: pb.BookingRegisteredV1): Either[EventSerializationError, BookingEvent] =
    def bad(e: InvalidValue) = EventSerializationError.Malformed(e.message)
    val decoded = for
      eventId <- Ulid.parse(p.eventId).left.map(bad)
      actor <-
        (if p.actorUserId.isEmpty then Right(None)
         else Ulid.parse(p.actorUserId).left.map(bad).map(Some(_)))
      bookingId <- Ulid.parse(p.bookingId).left.map(bad)
      shipperId <- Ulid.parse(p.shipperId).left.map(bad)
      number <- BookingNumber.parse(p.bookingNumber).left.map(bad)
      kind <- p.shipperKind match
        case "INDIVIDUAL" => Right(ShipperKind.Individual)
        case other        => Left(EventSerializationError.Malformed(s"荷主種別が不明: $other"))
      _ <- Either.cond(
        p.cargoKind == "GENERAL",
        (),
        EventSerializationError.Malformed(s"貨物種別が不明: ${p.cargoKind}")
      )
      dims <- Try(
        Dimensions.create(BigDecimal(p.lengthCm), BigDecimal(p.widthCm), BigDecimal(p.heightCm))
      ).toEither.left
        .map(e => EventSerializationError.Malformed(e.getMessage))
        .flatMap(_.left.map(bad))
      common <- Try(
        CargoCommon.create(p.itemName, BigDecimal(p.weightKg), dims, p.quantity)
      ).toEither.left
        .map(e => EventSerializationError.Malformed(e.getMessage))
        .flatMap(_.left.map(bad))
      origin <- PortCode.parse(p.originPort).left.map(bad)
      dest <- PortCode.parse(p.destinationPort).left.map(bad)
      handover <- Try(LocalDate.parse(p.preferredHandover)).toEither.left.map(e =>
        EventSerializationError.Malformed(e.getMessage)
      )
      deadline <- Try(LocalDate.parse(p.deadline)).toEither.left.map(e =>
        EventSerializationError.Malformed(e.getMessage)
      )
      transport <- TransportRequest.create(origin, dest, handover, deadline).left.map(bad)
    yield BookingEvent.BookingRegistered(
      EventMeta(
        eventId,
        Instant.ofEpochSecond(p.occurredAtSeconds, p.occurredAtNanos.toLong),
        actor
      ),
      BookingId(bookingId),
      number,
      ShipperRef(ShipperId(shipperId), kind),
      CargoSpec.General(common),
      transport
    )
    decoded
