package cargotracker.booking.commandadapter

import cargotracker.booking.domain.*
import cargotracker.sharedkernel.{PortCode, Ulid}
import java.time.{Instant, LocalDate}
import org.scalatest.funsuite.AnyFunSuite
import scala.deriving.Mirror
import scala.compiletime.constValueTuple

/** イベントの直列化（Protobuf の Envelope。BE 9.2・検査 A5）。 長期に保存するイベントなので、往復だけでなく「固定した旧版のバイト列を読める」ことを検査する。
  */
class BookingEventSerializerSpec extends AnyFunSuite:

  private val shipper = ShipperRef(
    ShipperId(Ulid.parse("01ARZ3NDEKTSV4RRFFQ69G5FAV").toOption.get),
    ShipperKind.Individual
  )

  private val registered: BookingEvent = BookingEvent.BookingRegistered(
    EventMeta(
      Ulid.parse("01ARZ3NDEKTSV4RRFFQ69G5FAW").toOption.get,
      Instant.parse("2026-11-04T01:02:03.456789Z"),
      Some(Ulid.parse("01ARZ3NDEKTSV4RRFFQ69G5FAY").toOption.get) // 実行者（NFR-S10）
    ),
    BookingId(Ulid.parse("01ARZ3NDEKTSV4RRFFQ69G5FAX").toOption.get),
    BookingNumber.parse("BK-000001").toOption.get,
    shipper,
    CargoSpec.General(
      CargoCommon
        .create(
          "精密機器",
          BigDecimal("120.5"),
          Dimensions.create(100, BigDecimal("80.25"), 60).toOption.get,
          3
        )
        .toOption
        .get
    ),
    TransportRequest
      .create(
        PortCode.parse("JPTYO").toOption.get,
        PortCode.parse("NLRTM").toOption.get,
        LocalDate.of(2026, 12, 1),
        LocalDate.of(2027, 1, 15)
      )
      .toOption
      .get
  )

  /** すべてのイベントの見本。新しいイベントを足したら、ここに足さないと下の「一覧」の検査が失敗する。 */
  private val samples: Map[String, BookingEvent] = Map("BookingRegistered" -> registered)

  private inline def eventNames[T](using m: Mirror.SumOf[T]): List[String] =
    constValueTuple[m.MirroredElemLabels].toList.map(_.toString)

  test("A5: BookingEvent のすべてのイベントに見本がある（載せ忘れが素通りしない）") {
    assert(samples.keySet == eventNames[BookingEvent].toSet)
  }

  test("A5: すべてのイベントが、直列化 → 復元で元に戻る") {
    samples.foreach { (name, event) =>
      val restored = BookingEventSerializer.deserialize(BookingEventSerializer.serialize(event))
      assert(restored == Right(event), name)
    }
  }

  test("実行者（actor）の有無も、往復で保たれる") {
    val withActor = registered
    val withoutActor = registered match
      case r: BookingEvent.BookingRegistered => r.copy(meta = r.meta.copy(actor = None))
    assert(
      BookingEventSerializer.deserialize(BookingEventSerializer.serialize(withActor)) == Right(
        withActor
      )
    )
    assert(
      BookingEventSerializer.deserialize(BookingEventSerializer.serialize(withoutActor)) == Right(
        withoutActor
      )
    )
  }

  test("Envelope は型名と版を持つ") {
    assert(BookingEventSerializer.manifest(registered) == "BookingRegistered_V1")
  }

  test("A5: 固定した V1 のバイト列を、今のコードで読める（旧版の互換性）") {
    val Right(event) =
      BookingEventSerializer.deserialize(GoldenBytes.bookingRegisteredV1): @unchecked
    // V1 を固定した時点では実行者の項目が無かった：旧版のバイト列は、実行者なし（None）として読める
    val withoutActor = registered match
      case r: BookingEvent.BookingRegistered => r.copy(meta = r.meta.copy(actor = None))
    assert(event == withoutActor)
  }

  test("未知の型名・未知の版は、握りつぶさず UnknownEvent で拒む") {
    val unknownType =
      BookingEventSerializer.envelopeBytes("CargoMagicallyAppeared", 1, Array.emptyByteArray)
    val unknownVersion =
      BookingEventSerializer.envelopeBytes("BookingRegistered", 99, Array.emptyByteArray)
    assert(
      BookingEventSerializer.deserialize(unknownType) == Left(
        EventSerializationError.UnknownEvent("CargoMagicallyAppeared", 1)
      )
    )
    assert(
      BookingEventSerializer.deserialize(unknownVersion) == Left(
        EventSerializationError.UnknownEvent("BookingRegistered", 99)
      )
    )
  }

  test("壊れたバイト列は Malformed で拒む（例外を投げない）") {
    val r = BookingEventSerializer.deserialize(Array[Byte](1, 2, 3, 4, 5))
    assert(r.left.exists(_.isInstanceOf[EventSerializationError.Malformed]))
  }

  test("NFR-S22: Protobuf のイベントに、氏名・住所・連絡先・メールアドレスの項目を持たない") {
    val fields = BookingEventSerializer.protoFieldNames("BookingRegistered")
    // 項目名をトークンに分けて見る（品名 item_name の "name" に誤反応しない）
    val personalTokens = Set("email", "address", "contact", "phone", "tel")
    val personalNames =
      Set("name", "shipper_name", "person_name", "customer_name", "consignee_name")
    assert(fields.nonEmpty)
    val offenders =
      fields.filter(f => personalNames.contains(f) || f.split('_').exists(personalTokens.contains))
    assert(offenders.isEmpty, s"個人情報らしい項目: $offenders")
    assert(fields.contains("item_name")) // 品名は個人情報ではない（検査が品名を誤って禁じていない）
  }
