///
/// 予約一覧の絞り込み（IT8・TS14。IT7 M14 + IT6 M4）。
///
/// # このテストが存在する理由
///
/// IT7 のレビュー（利用者代表）で、予約一覧に**状態での絞り込みが無い**と
/// 指摘された。`operation.md` の日次手順は「未着手の予約を朝に確認する」だが、
/// 未着手が 20 件を超えると一覧を目で追うことになり手順が成立しない。
/// 通知（US12）は将来リリースであり、状態での絞り込みが**唯一の安全網**である。
///
/// IT6 のレビュー（M4）では、予約一覧が**荷主名で絞れない**と指摘された。
/// 荷主から電話で「うちの分はどうなっているか」と聞かれたとき、
/// 営業は荷主コードを知らない。**同じ SQL・同じ画面**であるため統合して直す。
///
/// # 絞り込みの重ね方
///
/// 可視範囲（荷主ロールは自分の予約だけ）と 3 つの絞り込みは **AND で重ねる**。
/// **可視範囲は常に効く**——絞り込みを足したときに片方の経路で可視範囲を
/// 忘れると、荷主が他社の予約を読める（IT5 で実際に起きた）。
///
/// # 荷主名を Shipper Context に直接引きに行かない
///
/// 荷主名は Shipper Context のものである。`JdbcCargoRepo` が `shipper` 表を
/// JOIN すると、SQL の中で BC の境界が壊れる（`arch-lint` は SQL を読めない）。
/// **ACL（`findShipperIdsByName`）で ID の集合に翻訳してから**予約を絞る。
/// これは「必要とする側が必要な形に翻訳して引く」という本プロジェクトの
/// ACL の一般形（[ADR-0009](../../../../docs/adr/ADR-0009-routing-pulls-booking-via-acl.md)）に沿う。
///
mod CargoSearchTest {
    use BookingBookCargo.listVisible
    use BookingModel.{BookingStatus, CargoType, Cargo, bookingIdOf}
    use InMemoryHandlers.{withInMemoryCargoRepo, withStubShipperExistence}
    use SharedSecurity.{Principal, Role}

    def shipperA(): String = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
    def shipperB(): String = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

    /// 荷主コードと荷主 ID の対応。表示名はスタブが「荷主 <コード>」で作る
    def knownShippers(): List[(String, String)] =
        ("SHP-3F2504E0", shipperA()) :: ("SHP-7C9E6679", shipperB()) :: Nil

    def aCargo(bookingId: String, shipperId: String, status: BookingStatus): Cargo =
        match BookingModel.requireRouteSpecification("JPOSA", "USLAX", "2026-12-31") {
            case Err(e)   => bug!("フィクスチャの生成に失敗した: ${e}")
            case Ok(spec) =>
                BookingModel.reconstruct(bookingId, shipperId, spec, status,
                                         CargoType.General, 1200000i64,
                                         None, None, None, None, None,
                                         None, SharedRoutingStatus.RoutingStatus.NotRouted, None)
        }

    ///
    /// 一覧の題材。**同じ荷主に別状態・別荷主に同状態**を混ぜる。
    ///
    /// 片方の軸しか動かない題材だと、可視範囲と絞り込みのどちらが効いたのか
    /// 区別が付かない（IT7 の教訓: 緑になる題材と、壊れ方が見える題材は別物）。
    ///
    def fixtures(): List[Cargo] =
        aCargo("BK-00000001", shipperA(), BookingStatus.Preliminary) ::
        aCargo("BK-00000002", shipperA(), BookingStatus.RouteProposed) ::
        aCargo("BK-00000003", shipperB(), BookingStatus.Preliminary) :: Nil

    def sales(): Option[Principal] =
        Some(SharedSecurity.principalOf(1i64, "sales@example.com", Role.Sales, None))

    def shipperUser(shipperId: String): Option[Principal] =
        Some(SharedSecurity.principalOf(2i64, "shipper@example.com", Role.Shipper,
                                        Some(shipperId)))

    /// 絞り込みを実行し、当たった予約番号を返す
    def found(viewer: Option[Principal], keyword: String, status: String,
              shipperName: String): List[String] =
        let (rows, _) = withInMemoryCargoRepo(fixtures(), () ->
            withStubShipperExistence(knownShippers(), () ->
                listVisible(viewer, keyword, status, shipperName)));
        List.map(bookingIdOf, rows)

    // ---- 状態での絞り込み（IT7 M14） ----

    ///
    /// **状態で絞れること**。
    ///
    /// この式が偽になる入力: 状態のプレースホルダを SQL に足したが
    /// 値を空文字のまま渡す実装（全件が返り、絞り込みが効かない）。
    ///
    @Test
    def testFiltersByStatus(): Unit \ Assert =
        Assert.assertEq(expected = "BK-00000001" :: "BK-00000003" :: Nil,
                        found(sales(), "", "PRELIMINARY", ""))

    /// 状態を指定しなければ全件（絞り込みの既定は「絞らない」）
    @Test
    def testKeepsAllWhenStatusIsEmpty(): Unit \ Assert =
        Assert.assertEq(expected = 3, List.length(found(sales(), "", "", "")))

    ///
    /// **知らない状態語は 0 件に倒すこと**（[IT8 計画](../../../../docs/development/iteration_plan-8.md)
    /// の壊れ方の観点・軸 1「黙って広げない」）。
    ///
    /// 全件へ倒すと、利用者は絞ったつもりで**その状態ではない予約を対象に
    /// 作業する**。予約一覧から着手する予約を選ぶ運用（`operation.md` の
    /// 日次手順）では、まだ引き渡されていない予約に経路を割り当てることになる。
    ///
    /// 0 件にする以上、**なぜ 0 件なのかを画面が言える**必要がある
    /// （`BookingPages` が「そんな予約状態はありません」と出す）。
    ///
    /// この式が偽になる入力: 未知の語を `AnyStatus` へ倒す実装（全 3 件が返る）。
    ///
    @Test
    def testRejectsUnknownStatus(): Unit \ Assert =
        Assert.assertEq(expected = Nil, found(sales(), "", "NO_SUCH_STATUS", ""))

    // ---- 荷主名での絞り込み（IT6 M4） ----

    /// **荷主名で絞れること**（ACL が名前を荷主 ID の集合へ翻訳する）
    @Test
    def testFiltersByShipperName(): Unit \ Assert =
        Assert.assertEq(expected = "BK-00000001" :: "BK-00000002" :: Nil,
                        found(sales(), "", "", "SHP-3F2504E0"))

    ///
    /// **一致する荷主が無ければ 0 件**。
    ///
    /// この式が偽になる入力: 空の ID 集合を「絞らない」と同一視する実装。
    /// 存在しない荷主名で全件が出ると、営業は他社の予約を読み上げてしまう。
    ///
    @Test
    def testReturnsNothingWhenShipperNameMatchesNobody(): Unit \ Assert =
        Assert.assertEq(expected = Nil, found(sales(), "", "", "存在しない荷主"))

    // ---- 重ね方 ----

    /// 状態と荷主名は AND で重なること
    @Test
    def testCombinesStatusAndShipperName(): Unit \ Assert =
        Assert.assertEq(expected = "BK-00000002" :: Nil,
                        found(sales(), "", "ROUTE_PROPOSED", "SHP-3F2504E0"))

    ///
    /// **可視範囲は絞り込みより先に効くこと**。
    ///
    /// 荷主 B の利用者が荷主 A の名前で絞っても、0 件になる。
    /// この式が偽になる入力: 荷主名の絞り込みで可視範囲の条件を上書きする実装。
    ///
    @Test
    def testVisibleScopeStillAppliesWithShipperNameFilter(): Unit \ Assert =
        Assert.assertEq(expected = Nil,
                        found(shipperUser(shipperB()), "", "", "SHP-3F2504E0"))

    /// 荷主は自分の予約を状態で絞れること
    @Test
    def testShipperCanFilterOwnBookingsByStatus(): Unit \ Assert =
        Assert.assertEq(expected = "BK-00000002" :: Nil,
                        found(shipperUser(shipperA()), "", "ROUTE_PROPOSED", ""))

    /// 語での絞り込みと状態の絞り込みが同時に効くこと
    @Test
    def testCombinesKeywordAndStatus(): Unit \ Assert =
        Assert.assertEq(expected = "BK-00000003" :: Nil,
                        found(sales(), "BK-00000003", "PRELIMINARY", ""))
}
