///
/// 旅程の割り当て（US09・US11。IT9）。
///
/// # このテストが守るもの
///
/// 経路の割り当ては **Routing の画面から Booking の集約を呼ぶ**
/// （[ADR-0011](../../../../docs/adr/ADR-0011-routing-writes-booking-through-its-aggregate.md)）。
/// ADR が受け入れた「書き込みの越境」が安全なのは、**集約が不変条件を守るから**である。
///
/// したがって本テストが確かめるのは「呼び出し元が壊れた値を渡しても集約が拒むこと」
/// である。**保護が呼び出し元の行儀の良さに依存していないこと**を、
/// 呼び出し元を介さずに直接確かめる。
///
mod ItineraryTest {
    use BookingModel.{Cargo, BookingStatus, CargoType, BookingError, Leg}
    use BookingModel.{reconstruct, reconstructSpec, attachItinerary, detachItinerary,
                      itineraryOf, leg, isRouted, cargoItineraryOf, legsOf,
                      bookingStatusOf, expectedArrivalTimeOf}

    def aBookingId(): String = "BK-3F2504E0"
    def aShipperId(): String = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"

    /// 大阪発ロサンゼルス行きの予約（経路設計中）
    def cargoIn(status: BookingStatus): Cargo =
        reconstruct(aBookingId(), aShipperId(),
                    reconstructSpec("JPOSA", "USLAX", "2026-12-31"),
                    status, CargoType.General, 1200000i64, None, None, None, None, None,
                    None, SharedRoutingStatus.RoutingStatus.NotRouted)

    def underRouting(): Cargo = cargoIn(BookingStatus.RouteProposed)

    /// 直行（1 区間）
    def directLegs(): List[Leg] =
        leg("V-0001S", "JPOSA", "USLAX", "2026-11-01T10:00", "2026-11-15T08:00") :: Nil

    /// 東京で積み替える（2 区間）
    def transshipLegs(): List[Leg] =
        leg("V-0001S", "JPOSA", "JPTYO", "2026-11-01T10:00", "2026-11-02T09:00") ::
        leg("V-0002S", "JPTYO", "USLAX", "2026-11-03T10:00", "2026-11-15T08:00") :: Nil

    def errOf(r: Result[BookingError, Cargo]): Option[BookingError] = match r {
        case Err(e) => Some(e)
        case Ok(_)  => None
    }

    def okOf(r: Result[BookingError, Cargo]): Cargo = match r {
        case Ok(c)  => c
        case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
    }

    // ---- 正常系 ----

    ///
    /// **旅程を割り当てても `BookingStatus` は変わらないこと**（着手前の決定 2）。
    ///
    /// 変えるのは `RoutingStatus` だけである。予約を確定するのは営業が
    /// 荷主の承認を得たとき（US13）であり、経路が決まっただけでは確定しない。
    ///
    /// この式が偽になる入力: `attachItinerary` が `CONFIRMED` まで進める実装
    /// （設計ドキュメントの `RouteCargoCommand` がそう書いていた）。
    ///
    @Test
    def testAttachingItineraryDoesNotConfirmBooking(): Unit \ Assert =
        let attached = okOf(attachItinerary(underRouting(), itineraryOf(directLegs())));
        Assert.assertTrue(bookingStatusOf(attached) == BookingStatus.RouteProposed);
        Assert.assertTrue(isRouted(attached))

    /// 割り当てた区間を保持すること
    @Test
    def testKeepsAssignedLegs(): Unit \ Assert =
        let attached = okOf(attachItinerary(underRouting(), itineraryOf(transshipLegs())));
        match cargoItineraryOf(attached) {
            case None    => Assert.fail("旅程が保持されていない")
            case Some(i) =>
                Assert.assertEq(expected = "V-0001S" :: "V-0002S" :: Nil,
                                legsOf(i) |> List.map(BookingModel.legVoyageNumberOf))
        }

    /// 到着予定は**最終区間の荷降時刻**であること
    @Test
    def testExpectedArrivalIsLastUnloadTime(): Unit \ Assert =
        Assert.assertEq(expected = "2026-11-15T08:00",
                        expectedArrivalTimeOf(itineraryOf(transshipLegs())))

    // ---- 不変条件（集約が守る） ----

    ///
    /// **経路設計中でなければ割り当てないこと**。
    ///
    /// 仮受付（まだ引き渡されていない）や確定済み（荷主が承認済み）に
    /// 後から別の経路を入れると、**荷主が承認した経路と違うものが動く**。
    ///
    @Test
    def testRejectsWhenNotUnderRouting(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.CargoIsNotUnderRouting),
                        errOf(attachItinerary(cargoIn(BookingStatus.Preliminary),
                                              itineraryOf(directLegs()))));
        Assert.assertEq(expected = Some(BookingError.CargoIsNotUnderRouting),
                        errOf(attachItinerary(cargoIn(BookingStatus.Confirmed),
                                              itineraryOf(directLegs()))))

    /// 区間が空の旅程は「経路が決まった」と言えないこと
    @Test
    def testRejectsEmptyItinerary(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.ItineraryIsEmpty),
                        errOf(attachItinerary(underRouting(), itineraryOf(Nil))))

    ///
    /// **端点が要件と違えば拒むこと**。
    ///
    /// 大阪発ロサンゼルス行きの予約に、東京発の旅程を割り当てない。
    /// この式が偽になる入力: 端点を検証しない実装（Routing が渡した値を信じる）。
    ///
    @Test
    def testRejectsWhenEndpointsDoNotMatch(): Unit \ Assert =
        let wrongOrigin =
            leg("V-0001S", "JPTYO", "USLAX", "2026-11-01T10:00", "2026-11-15T08:00") :: Nil;
        Assert.assertEq(expected = Some(BookingError.ItineraryDoesNotMatchRouteSpecification),
                        errOf(attachItinerary(underRouting(), itineraryOf(wrongOrigin))));
        let wrongDestination =
            leg("V-0001S", "JPOSA", "DEHAM", "2026-11-01T10:00", "2026-11-15T08:00") :: Nil;
        Assert.assertEq(expected = Some(BookingError.ItineraryDoesNotMatchRouteSpecification),
                        errOf(attachItinerary(underRouting(), itineraryOf(wrongDestination))))

    ///
    /// **区間がつながっていなければ拒むこと**。
    ///
    /// 前の荷降港と次の積込港が違えば、**貨物は途中で降ろされたまま次の船に乗らない**。
    /// 端点だけを見る実装ではこれを通してしまう（大阪発 LA 着は満たされる）。
    ///
    /// この式が偽になる入力: 端点だけ検証して中間を見ない実装。
    ///
    @Test
    def testRejectsDisconnectedLegs(): Unit \ Assert =
        let broken =
            leg("V-0001S", "JPOSA", "JPTYO", "2026-11-01T10:00", "2026-11-02T09:00") ::
            leg("V-0002S", "CNSHA", "USLAX", "2026-11-03T10:00", "2026-11-15T08:00") :: Nil;
        Assert.assertEq(expected = Some(BookingError.ItineraryIsDisconnected),
                        errOf(attachItinerary(underRouting(), itineraryOf(broken))))

    // ---- 差し戻し・選び直し ----

    ///
    /// **旅程を外すと経路状態も戻ること**（壊れ方の観点・軸 4）。
    ///
    /// 旅程だけ消して `ROUTED` のままにすると、
    /// 「経路提案済だが経路が無い」という読めない状態になる。
    ///
    @Test
    def testDetachingAlsoResetsRoutingStatus(): Unit \ Assert =
        let attached = okOf(attachItinerary(underRouting(), itineraryOf(directLegs())));
        let detached = detachItinerary(attached);
        Assert.assertTrue(not isRouted(detached));
        Assert.assertTrue(Option.isEmpty(cargoItineraryOf(detached)))

    ///
    /// **外した後に選び直せること**。
    ///
    /// 荷主が「もっと安く」と言えば戻ってくる（US09 の利用場面 3）。
    ///
    @Test
    def testCanReattachAfterDetaching(): Unit \ Assert =
        let attached = okOf(attachItinerary(underRouting(), itineraryOf(directLegs())));
        let detached = detachItinerary(attached);
        let reattached = okOf(attachItinerary(detached, itineraryOf(transshipLegs())));
        Assert.assertTrue(isRouted(reattached));
        match cargoItineraryOf(reattached) {
            case None    => Assert.fail("選び直した旅程が保持されていない")
            case Some(i) => Assert.assertEq(expected = 2, List.length(legsOf(i)))
        }
}
