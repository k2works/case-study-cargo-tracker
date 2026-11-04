package cargotracker.booking.commandadapter

import cargotracker.booking.domain.BookingEvent
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.serialization.SerializerWithStringManifest

/** Pekko の永続化に、Protobuf の Envelope（`BookingEventSerializer`）を使わせる。Java シリアライズは使わない。 識別子は設定の
  * `serializers` と対で、他のシリアライザと重ならない値にする。
  */
final class BookingEventPekkoSerializer(system: ExtendedActorSystem)
    extends SerializerWithStringManifest:

  override def identifier: Int = 6_001_001

  override def manifest(o: AnyRef): String = o match
    case e: BookingEvent => BookingEventSerializer.manifest(e)
    case other           =>
      throw new IllegalArgumentException(s"BookingEvent ではない: ${other.getClass.getName}")

  override def toBinary(o: AnyRef): Array[Byte] = o match
    case e: BookingEvent => BookingEventSerializer.serialize(e)
    case other           =>
      throw new IllegalArgumentException(s"BookingEvent ではない: ${other.getClass.getName}")

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    BookingEventSerializer.deserialize(bytes) match
      case Right(event) => event
      case Left(error)  => throw new IllegalArgumentException(s"イベントを復元できない（$manifest）: $error")
