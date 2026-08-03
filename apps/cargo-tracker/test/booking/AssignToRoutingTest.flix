///
/// 経路設計への引き渡し（US06）の単体テスト。
///
/// 対応するビジネスルールは BK-4（`BookingStatus` の遷移順序）。
///
/// 開発戦略の中盤の規律に従い、**許可される遷移と拒否される遷移の両方**を
/// 固定する。片方だけだと「何でも通る実装」でも緑になる。
///
mod AssignToRoutingTest {
    use BookingModel.{Cargo, BookingStatus, CargoType, BookingError}
    use BookingModel.{bookCargo, reconstruct, reconstructSpec, assignToRouting,
                      bookingStatusOf, bookingIdOf}

    def aBookingId(): String = "BK-3F2504E0"
    def aShipperId(): String = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"

    /// 仮受付の貨物（`bookCargo` が作れるのはこの状態だけ）
    def preliminary(): Cargo =
        match bookCargo(aBookingId(), aShipperId(), "JPOSA", "USLAX", "2026-12-31",
                        CargoType.General, 1200000i64, None, None, None, None, None) {
            case Ok(c)  => c
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    /// 任意の状態の貨物（永続化からの復元と同じ経路で作る）
    def cargoIn(status: BookingStatus): Cargo =
        reconstruct(aBookingId(), aShipperId(),
                    reconstructSpec("JPOSA", "USLAX", "2026-12-31"),
                    status, CargoType.General, 1200000i64, None, None, None, None, None)

    def errOf(r: Result[BookingError, Cargo]): Option[BookingError] = match r {
        case Err(e) => Some(e)
        case Ok(_)  => None
    }

    // ---- 許可される遷移 ----

    ///
    /// 仮受付から経路設計中へ遷移できること。
    ///
    @Test
    def testAssignsPreliminaryCargoToRouting(): Unit \ Assert =
        match assignToRouting(preliminary()) {
            case Err(e) => Assert.fail("引き渡しに失敗した: ${e}")
            case Ok(c)  => Assert.assertTrue(bookingStatusOf(c) == BookingStatus.RouteProposed)
        }

    ///
    /// 遷移しても予約番号や輸送要件が変わらないこと。
    ///
    /// **状態だけが変わる**。他の値まで作り直すと、遷移のたびに情報が
    /// 失われる余地が生まれる。
    ///
    @Test
    def testKeepsIdentityAndSpecificationWhenAssigning(): Unit \ Assert =
        match assignToRouting(preliminary()) {
            case Err(e) => Assert.fail("引き渡しに失敗した: ${e}")
            case Ok(c)  =>
                Assert.assertEq(expected = aBookingId(), bookingIdOf(c));
                Assert.assertEq(expected = "JPOSA",
                    BookingModel.originOf(BookingModel.routeSpecificationOf(c)));
                Assert.assertEq(expected = 1200000i64, BookingModel.weightGramsOf(c))
        }

    // ---- 拒否される遷移 ----

    ///
    /// 二重に引き渡せないこと。
    ///
    /// 連打や再送信で 2 回届く。2 回目を通すと、経路設計側に同じ依頼が
    /// 二重に積まれる。
    ///
    @Test
    def testRejectsAssigningTwice(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.CargoIsNotPreliminary),
                        errOf(assignToRouting(cargoIn(BookingStatus.RouteProposed))))

    ///
    /// 仮受付以外のどの状態からも引き渡せないこと。
    ///
    /// **状態を列挙して全て確かめる**。「思いついた状態だけ」を書くと、
    /// 後から状態が増えたときに漏れる。
    ///
    @Test
    def testRejectsAssigningFromAnyNonPreliminaryState(): Unit \ Assert =
        let states = BookingStatus.RouteProposed :: BookingStatus.Confirmed ::
                     BookingStatus.TrackingIssued :: BookingStatus.InTransit ::
                     BookingStatus.Delivered :: BookingStatus.Settled ::
                     BookingStatus.Cancelled :: Nil;
        Assert.assertTrue(states |> List.forAll(s ->
            errOf(assignToRouting(cargoIn(s))) == Some(BookingError.CargoIsNotPreliminary)))

    ///
    /// キャンセル済みの貨物を引き渡せないこと。
    ///
    /// 上のテストに含まれるが、**業務上もっとも避けたい形**のため単独でも置く。
    /// 取り消した貨物の経路設計が始まると、実費が発生する。
    ///
    @Test
    def testRejectsAssigningCancelledCargo(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.CargoIsNotPreliminary),
                        errOf(assignToRouting(cargoIn(BookingStatus.Cancelled))))

    // ---- 引き渡せる条件の提示（US06 受入基準 4） ----

    ///
    /// 引き渡せるかどうかを、遷移を試みずに問い合わせられること。
    ///
    /// 画面はボタンの活性・非活性を決めるためにこれを使う。
    /// **判断を画面が持たない**ようにするためで、持たせると
    /// 「押せるのに失敗する」「押せないが実は可能」がすぐに生まれる。
    ///
    @Test
    def testReportsWhetherCargoCanBeAssigned(): Unit \ Assert =
        Assert.assertTrue(BookingModel.canAssignToRouting(preliminary()));
        Assert.assertTrue(not BookingModel.canAssignToRouting(cargoIn(BookingStatus.RouteProposed)))

    ///
    /// 引き渡せない理由を利用者に示せること。
    ///
    /// 「できません」だけでは、利用者は待てばよいのか誰かに聞くのか分からない。
    ///
    @Test
    def testExplainsWhyCargoCannotBeAssigned(): Unit \ Assert =
        let reason = BookingModel.assignmentBlockedReason(cargoIn(BookingStatus.RouteProposed));
        match reason {
            case None    => Assert.fail("引き渡せない理由が得られなかった")
            case Some(r) => Assert.assertTrue(String.length(r) > 0)
        }

    /// 引き渡せる貨物には理由がないこと
    @Test
    def testHasNoBlockingReasonWhenAssignable(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(BookingModel.assignmentBlockedReason(preliminary())))
}
