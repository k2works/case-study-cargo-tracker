///
/// 予約の確定・差し戻し・キャンセル（US13。IT9）。
///
/// # このテストが守るもの
///
/// IT9 のレビュー H4: US13 の状態遷移は HTTP 統合テストの**正常系 3 パス**でしか
/// 覆われていなかった。状態 5 × 操作 3 の遷移表に対して逆ピラミッドであり、
/// 拒否の分岐（二重キャンセル・仮受付からの確定・外す経路が無い差し戻し）は
/// 実装にあるのにテストが無かった。
///
/// 遷移表は集約で決まる。**集約を直接叩いて表を埋める**——HTTP を通すと
/// 認可・CSRF・画面の描画が混ざり、どれが落ちたのか分からなくなる。
///
mod ConfirmBookingTest {
    use BookingModel.{Cargo, BookingStatus, CargoType, BookingError, Leg}
    use BookingModel.{reconstruct, reconstructSpec, attachItinerary,
                      itineraryOf, leg, isRouted, cargoItineraryOf,
                      bookingStatusOf, confirm, sendBackToRouting, cancel,
                      canConfirm, canSendBackToRouting, canCancel}

    def aBookingId(): String = "BK-3F2504E0"
    def aShipperId(): String = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"

    def cargoIn(status: BookingStatus): Cargo =
        reconstruct(aBookingId(), aShipperId(),
                    reconstructSpec("JPOSA", "USLAX", "2026-12-31"),
                    status, CargoType.General, 1200000i64, None, None, None, None, None,
                    None, SharedRoutingStatus.RoutingStatus.NotRouted)

    def directLegs(): List[Leg] =
        leg("V-0001S", "JPOSA", "USLAX", "2026-11-01T10:00", "2026-11-15T08:00") :: Nil

    def okOf(r: Result[BookingError, Cargo]): Cargo = match r {
        case Ok(c)  => c
        case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
    }

    def errOf(r: Result[BookingError, Cargo]): Option[BookingError] = match r {
        case Err(e) => Some(e)
        case Ok(_)  => None
    }

    /// 経路が割り当てられた経路設計中の予約（確定できる状態）
    def routed(): Cargo =
        okOf(attachItinerary(cargoIn(BookingStatus.RouteProposed), itineraryOf(directLegs())))

    /// 確定済みの予約
    def confirmed(): Cargo = okOf(confirm(routed()))

    // ================================================================
    // 確定（US13 受入基準 1-3）
    // ================================================================

    ///
    /// 経路が割り当てられた予約は確定できること。
    ///
    /// この式が偽になる入力: 確定が `RoutingStatus` まで動かす実装
    /// （経路の妥当性は営業の承認では変わらない）。
    ///
    @Test
    def testConfirmsRoutedCargo(): Unit \ Assert =
        let c = okOf(confirm(routed()));
        Assert.assertTrue(bookingStatusOf(c) == BookingStatus.Confirmed);
        Assert.assertTrue(isRouted(c))

    ///
    /// **経路が無ければ確定できないこと**。
    ///
    /// 荷主は経路を見て承認する。経路が無いまま確定すると
    /// **何を承認したのか分からない記録**が残る。
    ///
    @Test
    def testRejectsConfirmingWithoutItinerary(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(BookingError.CargoHasNoItinerary),
            errOf(confirm(cargoIn(BookingStatus.RouteProposed))))

    /// 仮受付の予約は確定できないこと（経路設計を飛ばせない）
    @Test
    def testRejectsConfirmingPreliminaryCargo(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(BookingError.CargoIsNotUnderRouting),
            errOf(confirm(cargoIn(BookingStatus.Preliminary))))

    /// キャンセル済みの予約は確定できないこと
    @Test
    def testRejectsConfirmingCancelledCargo(): Unit \ Assert =
        let cancelled = okOf(cancel(routed()));
        Assert.assertEq(
            expected = Some(BookingError.CargoIsNotUnderRouting),
            errOf(confirm(cancelled)))

    /// 二重の確定を拒むこと（既に確定した予約をもう一度確定できない）
    @Test
    def testRejectsConfirmingTwice(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(BookingError.CargoIsNotUnderRouting),
            errOf(confirm(confirmed())))

    // ================================================================
    // 差し戻し（US13 受入基準 4）
    // ================================================================

    ///
    /// 確定済みの予約を経路設計へ差し戻せること。**旅程も外れる**。
    ///
    /// 状態だけ戻して旅程を残すと「経路設計中なのに経路がある」ことになり、
    /// 経路設計者は選び直すべきか分からない。
    ///
    @Test
    def testSendsBackConfirmedCargoAndDetachesItinerary(): Unit \ Assert =
        let c = okOf(sendBackToRouting(confirmed()));
        Assert.assertTrue(bookingStatusOf(c) == BookingStatus.RouteProposed);
        Assert.assertTrue(not isRouted(c));
        Assert.assertTrue(Option.isEmpty(cargoItineraryOf(c)))

    ///
    /// **確定前でも差し戻せること**（IT9 レビュー H1）。
    ///
    /// 荷主の「別便で」という返事は、確定**前**（経路を提案した直後）に
    /// 来るほうが多い。確定済みからしか差し戻せない実装では、
    /// 変更希望を記録する手段が無く、営業は口頭で頼むしかなかった。
    ///
    /// この式が偽になる入力: `bookingStatus != Confirmed` で拒む実装。
    ///
    @Test
    def testSendsBackRoutedCargoBeforeConfirmation(): Unit \ Assert =
        let c = okOf(sendBackToRouting(routed()));
        Assert.assertTrue(bookingStatusOf(c) == BookingStatus.RouteProposed);
        Assert.assertTrue(not isRouted(c))

    ///
    /// **外す経路が無ければ差し戻せないこと**。
    ///
    /// 経路未割り当ての経路設計中は既にそこにいる。差し戻す意味がない。
    ///
    @Test
    def testRejectsSendingBackWithoutRoute(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(BookingError.CargoHasNoRouteToSendBack),
            errOf(sendBackToRouting(cargoIn(BookingStatus.RouteProposed))))

    /// 仮受付の予約は差し戻せないこと
    @Test
    def testRejectsSendingBackPreliminaryCargo(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(BookingError.CargoHasNoRouteToSendBack),
            errOf(sendBackToRouting(cargoIn(BookingStatus.Preliminary))))

    /// 差し戻した予約は経路を選び直せること（US13 受入基準 4 の続き）
    @Test
    def testCanReassignAfterSendingBack(): Unit \ Assert =
        let sentBack = okOf(sendBackToRouting(confirmed()));
        let reassigned = okOf(attachItinerary(sentBack, itineraryOf(directLegs())));
        Assert.assertTrue(isRouted(reassigned));
        Assert.assertTrue(bookingStatusOf(reassigned) == BookingStatus.RouteProposed)

    // ================================================================
    // キャンセル（US13 受入基準 5）
    // ================================================================

    ///
    /// **いずれの状態からもキャンセルできること**（ビジネスルール 4）。
    ///
    /// キャンセルが最も多いのは経路が決まる前（与信 NG・二重登録・
    /// 引き取り中止）である。仮受付を止められないと、経路設計者は
    /// 死んだ予約に経路を設計する（IT9 レビュー H1）。
    ///
    @Test
    def testCancelsFromAnyState(): Unit \ Assert =
        let states = BookingStatus.Preliminary :: BookingStatus.RouteProposed :: Nil;
        List.forEach(s ->
            Assert.assertTrue(
                bookingStatusOf(okOf(cancel(cargoIn(s)))) == BookingStatus.Cancelled),
            states);
        Assert.assertTrue(
            bookingStatusOf(okOf(cancel(confirmed()))) == BookingStatus.Cancelled)

    ///
    /// **キャンセルしても旅程は外れないこと**。
    ///
    /// 「どの経路で運ぶ予定だったか」が残っているほうが、後から経緯を追える。
    /// 実装コメントが主張している判断を、テストで固定する。
    ///
    @Test
    def testCancellingKeepsItinerary(): Unit \ Assert =
        let cancelled = okOf(cancel(confirmed()));
        Assert.assertTrue(isRouted(cancelled));
        Assert.assertTrue(not Option.isEmpty(cargoItineraryOf(cancelled)))

    ///
    /// **二重のキャンセルを拒むこと**。
    ///
    /// 操作の取り違えであり、黙って成功にすると利用者は
    /// 「取り消したはずのものが消えていない」と誤解する。
    ///
    @Test
    def testRejectsCancellingTwice(): Unit \ Assert =
        let cancelled = okOf(cancel(routed()));
        Assert.assertEq(
            expected = Some(BookingError.CargoIsAlreadyCancelled),
            errOf(cancel(cancelled)))

    /// キャンセル済みの予約には経路を割り当てられないこと
    @Test
    def testRejectsAttachingItineraryToCancelledCargo(): Unit \ Assert =
        let cancelled = okOf(cancel(cargoIn(BookingStatus.RouteProposed)));
        Assert.assertEq(
            expected = Some(BookingError.CargoIsNotUnderRouting),
            errOf(attachItinerary(cancelled, itineraryOf(directLegs()))))

    // ================================================================
    // 述語と操作の一致（IT9 レビュー H1）
    // ================================================================

    ///
    /// **述語が真のときだけ操作が成功すること**。
    ///
    /// 画面はこの述語でボタンを出し分ける。述語と操作が食い違うと
    /// **押せるのに必ず失敗するボタン**が出る（H1 の欠陥そのもの）。
    ///
    /// この式が偽になる入力: `canSendBackToRouting` を広げて
    /// `sendBackToRouting` を狭いままにする実装。
    ///
    @Test
    def testPredicatesAgreeWithOperations(): Unit \ Assert =
        let cancelled = okOf(cancel(routed()));
        let samples =
            cargoIn(BookingStatus.Preliminary) ::
            cargoIn(BookingStatus.RouteProposed) ::
            routed() :: confirmed() :: cancelled :: Nil;
        List.forEach(c -> {
            Assert.assertEq(expected = canConfirm(c),
                            Result.isOk(confirm(c)));
            Assert.assertEq(expected = canSendBackToRouting(c),
                            Result.isOk(sendBackToRouting(c)));
            Assert.assertEq(expected = canCancel(c),
                            Result.isOk(cancel(c)))
        }, samples)
}
