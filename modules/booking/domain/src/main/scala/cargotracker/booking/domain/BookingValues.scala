package cargotracker.booking.domain

import cargotracker.sharedkernel.{InvalidValue, PortCode}
import java.time.LocalDate

/** 人が読む予約番号。形式（接頭辞・桁数）は未定義で、整形は発行側（ポート）が持つ。ここでは空を拒むだけ。 */
final case class BookingNumber private (value: String)
object BookingNumber:
  def parse(raw: String): Either[InvalidValue, BookingNumber] =
    NonEmptyText.parse("bookingNumber", raw, 40).map(BookingNumber(_))

/** 荷主の参照（予約が持つのは荷主の ID と種別だけ。個人情報は載せない。ADR-004）。 */
final case class ShipperRef(id: ShipperId, kind: ShipperKind)

final case class Dimensions private (
    lengthCm: BigDecimal,
    widthCm: BigDecimal,
    heightCm: BigDecimal
)
object Dimensions:
  def create(
      lengthCm: BigDecimal,
      widthCm: BigDecimal,
      heightCm: BigDecimal
  ): Either[InvalidValue, Dimensions] =
    val sides = List(lengthCm, widthCm, heightCm)
    for
      _ <- Either.cond(sides.forall(_ > 0), (), InvalidValue("dimensions", "各辺は正の値にする"))
      // 読み取りモデルの列（numeric(8, 2)）に入る範囲。受け付けたのに一覧に出ない予約を作らない
      _ <- Either.cond(
        sides.forall(fits(_, scale = 2, maxExclusive = BigDecimal(1000000))),
        (),
        InvalidValue("dimensions", "各辺は小数 2 桁まで、100 万未満にする")
      )
    yield Dimensions(lengthCm, widthCm, heightCm)

  /** 小数の桁数と上限が、保存先の列に収まるか。 */
  private[domain] def fits(v: BigDecimal, scale: Int, maxExclusive: BigDecimal): Boolean =
    v.bigDecimal.stripTrailingZeros.scale <= scale && v < maxExclusive

/** 貨物仕様の共通部分（B11：重量は正、個数は 1 以上）。 */
final case class CargoCommon private (
    itemName: String,
    weightKg: BigDecimal,
    dimensions: Dimensions,
    quantity: Int
)
object CargoCommon:
  def create(
      itemName: String,
      weightKg: BigDecimal,
      dimensions: Dimensions,
      quantity: Int
  ): Either[InvalidValue, CargoCommon] =
    for
      name <- NonEmptyText.parse("itemName", itemName, 200)
      w <- Either.cond(weightKg > 0, weightKg, InvalidValue("weightKg", "正の値にする"))
      // 読み取りモデルの列（numeric(12, 3)）に入る範囲
      _ <- Either.cond(
        Dimensions.fits(weightKg, scale = 3, maxExclusive = BigDecimal(1000000000)),
        (),
        InvalidValue("weightKg", "小数 3 桁まで、10 億未満にする")
      )
      q <- Either.cond(quantity >= 1, quantity, InvalidValue("quantity", "1 以上にする"))
    yield CargoCommon(name, w, dimensions, q)

/** 貨物仕様（B12：型で守る）。Bolt 1 は一般貨物のみ。危険物（申告情報）・冷凍冷蔵（温度管理条件）は Bolt 13 で足す。 */
enum CargoSpec:
  case General(common: CargoCommon)

/** 輸送条件（B11：出発地≠目的地、希望引渡日≦希望期限）。希望期限は用語集の「希望期限」（希望着日と同じ扱い。Bolt 計画 Q-3）。 */
final case class TransportRequest private (
    origin: PortCode,
    destination: PortCode,
    preferredHandover: LocalDate,
    deadline: LocalDate
)
object TransportRequest:
  def create(
      origin: PortCode,
      destination: PortCode,
      preferredHandover: LocalDate,
      deadline: LocalDate
  ): Either[InvalidValue, TransportRequest] =
    if origin == destination then Left(InvalidValue("destination", "出発地と異なる港にする"))
    else if preferredHandover.isAfter(deadline) then
      Left(InvalidValue("preferredHandover", "希望期限以前にする"))
    else Right(TransportRequest(origin, destination, preferredHandover, deadline))
