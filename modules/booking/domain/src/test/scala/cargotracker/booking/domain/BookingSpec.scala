package cargotracker.booking.domain

import cargotracker.sharedkernel.{Clock, InvalidValue, PortCode, Ulid}
import java.time.{Instant, LocalDate}
import java.util.Random
import org.scalatest.funsuite.AnyFunSuite

/** 貨物予約（U-02。イベント履歴式。ドメインモデル 5.4）。Bolt 1 は「登録して仮受付」まで。 業務規則の検査は B 番号で命名する（AC
  * 番号は受入条件に対応するテストだけに付ける。テスト戦略 7.2）。
  */
class BookingSpec extends AnyFunSuite:

  private val at = Instant.parse("2026-11-04T00:00:00Z")
  private val clock = Clock.fixed(at)
  private val rnd = new Random(3)
  private def ulid() = Ulid.generate(clock, rnd)

  private def meta() = EventMeta(ulid(), at)
  private val shipper = ShipperRef(ShipperId(ulid()), ShipperKind.Individual)
  private val cargo = CargoSpec.General(
    CargoCommon
      .create("精密機器", BigDecimal("120.5"), Dimensions.create(100, 80, 60).toOption.get, 3)
      .toOption
      .get
  )
  private def port(code: String) = PortCode.parse(code).toOption.get
  private val transport = TransportRequest
    .create(port("JPTYO"), port("NLRTM"), LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 15))
    .toOption
    .get

  private def registerCmd(id: BookingId = BookingId(ulid())) =
    BookingCommand.RegisterBooking(
      meta(),
      id,
      BookingNumber.parse("BK-000001").toOption.get,
      shipper,
      cargo,
      transport
    )

  /** Given（過去のイベント）/ When（コマンド）/ Then（結果）。状態は本番の復元経路（replay）で作る。 */
  private final case class Given(events: BookingEvent*):
    def when(cmd: BookingCommand): Either[BookingError, (BookingState, BookingEvent)] =
      Booking.handle(Booking.replay(events), cmd)

  test("US04-AC02: 荷主・貨物仕様・輸送条件で登録でき、予約 ID・予約番号が付いて状態は仮受付になる") {
    val cmd = registerCmd()
    val Right((state, e)) = Given().when(cmd): @unchecked
    state match
      case BookingState.Received(b) =>
        assert(b.id == cmd.id && b.number == cmd.number && b.status == BookingStatus.Received)
      case other => fail(s"仮受付のはず: $other")
    assert(
      e == BookingEvent.BookingRegistered(cmd.meta, cmd.id, cmd.number, shipper, cargo, transport)
    )
  }

  test("US04-AC01: 登録の結果には、選択した荷主の ID と種別が残る") {
    val Right((_, e: BookingEvent.BookingRegistered)) = Given().when(registerCmd()): @unchecked
    assert(e.shipper.id == shipper.id && e.shipper.kind == ShipperKind.Individual)
  }

  test("NFR-S22: イベントに荷主の個人情報（氏名・住所・連絡先・メール）を載せない") {
    val personal =
      Set(classOf[ShipperName], classOf[Address], classOf[ContactNumber], classOf[EmailAddress])
    def fieldTypes(c: Class[?]): Set[Class[?]] = c.getDeclaredFields.map(_.getType).toSet
    val carried =
      fieldTypes(classOf[BookingEvent.BookingRegistered]) ++ fieldTypes(classOf[ShipperRef])
    assert(carried.intersect(personal.map(c => c: Class[?])).isEmpty)
  }

  test("B1: 登録済みの予約に再び登録しようとしても、InvalidTransition で拒む") {
    val first = registerCmd()
    val Right((_, e)) = Given().when(first): @unchecked
    val result = Given(e).when(registerCmd(first.id))
    assert(result == Left(BookingError.InvalidTransition("Received", "RegisterBooking")))
  }

  test("復元：イベントから組み立てた状態は、コマンドの結果の状態と一致する") {
    val Right((state, e)) = Given().when(registerCmd()): @unchecked
    assert(Booking.replay(Seq(e)) == state)
  }

  test("B1: 状態 × コマンドの遷移表は全組み合わせを網羅し、表にない遷移は拒む") {
    val received = Booking.replay(Seq(Given().when(registerCmd()).toOption.get._2))
    val states = Map("NotCreated" -> BookingState.NotCreated, "Received" -> received)
    val commands = Map("RegisterBooking" -> registerCmd())
    // true = 受け付ける、false = InvalidTransition で拒む。表に載っていない組み合わせは失敗にする。
    val table =
      Map(("NotCreated", "RegisterBooking") -> true, ("Received", "RegisterBooking") -> false)
    assert(table.keySet == (for s <- states.keys; c <- commands.keys yield (s, c)).toSet)
    for ((s, c), accepted) <- table do
      val result = Booking.handle(states(s), commands(c))
      assert(result.isRight == accepted, s"$s × $c")
      if !accepted then assert(result.left.exists(_.isInstanceOf[BookingError.InvalidTransition]))
    assert(states.values.map(BookingState.name).toSet == states.keySet)
  }

  test("DM-10: 実行できる操作は状態から導く（Bolt 1 で実装済みの操作は無いので、どの状態でも空）") {
    val received = Booking.replay(Seq(Given().when(registerCmd()).toOption.get._2))
    assert(Booking.availableCommands(BookingState.NotCreated).isEmpty)
    assert(Booking.availableCommands(received).isEmpty)
  }

  // ---- B11：入力の整合（値オブジェクトの生成時に守る）----

  test("B11: 出発地と目的地は異なる") {
    assert(
      TransportRequest.create(
        port("JPTYO"),
        port("JPTYO"),
        LocalDate.of(2026, 12, 1),
        LocalDate.of(2027, 1, 1)
      ) ==
        Left(InvalidValue("destination", "出発地と異なる港にする"))
    )
  }

  test("B11: 希望引渡日は希望期限以前（同日は可、後は不可）") {
    val d = LocalDate.of(2027, 1, 1)
    assert(TransportRequest.create(port("JPTYO"), port("NLRTM"), d, d).isRight)
    assert(TransportRequest.create(port("JPTYO"), port("NLRTM"), d.plusDays(1), d).isLeft)
  }

  test("B11: 重量は正、個数は 1 以上、寸法は各辺が正（境界を含む）") {
    val dims = Dimensions.create(1, 1, 1).toOption.get
    assert(CargoCommon.create("品", BigDecimal("0.001"), dims, 1).isRight)
    assert(CargoCommon.create("品", BigDecimal(0), dims, 1).isLeft)
    assert(CargoCommon.create("品", BigDecimal(-1), dims, 1).isLeft)
    assert(CargoCommon.create("品", BigDecimal(1), dims, 0).isLeft)
    assert(
      Dimensions.create(0, 1, 1).isLeft && Dimensions.create(1, 0, 1).isLeft && Dimensions
        .create(1, 1, 0)
        .isLeft
    )
  }

  test("品名が空なら拒む") {
    val dims = Dimensions.create(1, 1, 1).toOption.get
    assert(
      CargoCommon.create(" ", BigDecimal(1), dims, 1) == Left(InvalidValue("itemName", "空にできない"))
    )
  }

  test("予約番号が空なら拒む") {
    assert(BookingNumber.parse(" ").isLeft)
  }
