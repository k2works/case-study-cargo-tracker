///
/// 荷役作業の記録と妥当性判定（US15。IT10）。
///
/// # このテストが守るもの
///
/// `isValidFor` は設計のデシジョンテーブルそのものであり、**貨物が違う船に
/// 乗ったことを検出する唯一の場所**である。見逃すと、荷主は予定どおり届くと
/// 思ったまま貨物が別の港へ運ばれる。
///
/// 表は**種別 × 場所の一致**で 6 セルある。全部埋める——
/// 「受領は警告、積込は MISROUTED」という書き分けは表を見ないと守れない。
///
mod HandlingActivityTest {
    use HandlingModel.{HandlingActivity, HandlingError, HandlingType, HandlingValidity}
    use HandlingModel.{register, isValidFor, cargoSnapshot, legSnapshot,
                       resultingStatusFor, requiresVoyageNumber, validityOf, withValidity,
                       typeFromPersisted, typeToPersisted}

    def aTrackingNumber(): String = "TRK-20260805-0001"
    def anOperator(): String = "sato"
    def aConfirmation(): String = "RC-12345"
    def today(): String = "2026-08-05"

    /// 大阪 → 東京（V-0001S）→ ロサンゼルス（V-0002S）の旅程
    def transshipSnapshot(): HandlingModel.CargoSnapshot =
        cargoSnapshot("JPOSA", "USLAX",
            legSnapshot("JPOSA", "JPTYO", "V-0001S") ::
            legSnapshot("JPTYO", "USLAX", "V-0002S") :: Nil)

    /// 経路未割り当て
    def unroutedSnapshot(): HandlingModel.CargoSnapshot =
        cargoSnapshot("JPOSA", "USLAX", Nil)

    def okOf(r: Result[HandlingError, HandlingActivity]): HandlingActivity = match r {
        case Ok(a)  => a
        case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
    }

    def errOf(r: Result[HandlingError, HandlingActivity]): Option[HandlingError] = match r {
        case Err(e) => Some(e)
        case Ok(_)  => None
    }

    def activity(t: HandlingType, location: String,
                 voyage: Option[String]): HandlingActivity =
        okOf(register(aTrackingNumber(), t, location, "2026-08-01T10:00", voyage,
                 anOperator(), None, today()))

    // ================================================================
    // デシジョンテーブル（US15 受入基準 7）
    // ================================================================

    ///
    /// **表を丸ごと固定する**。
    ///
    /// | 種別 | 場所 | 期待 |
    /// | :--- | :--- | :--- |
    /// | RECEIVE | 出発港と一致 | Valid |
    /// | RECEIVE | 不一致 | **Warned**（MISROUTED ではない） |
    /// | LOAD | 該当区間の積込港と一致 | Valid |
    /// | LOAD | 不一致 | **Misrouted** |
    /// | UNLOAD | 該当区間の荷降港と一致 | Valid |
    /// | UNLOAD | 不一致 | **Misrouted** |
    ///
    /// この式が偽になる入力: 受領の不一致も MISROUTED にする実装。
    /// 集荷場所が予定と違うのは「別の倉庫で受け取った」程度のことが多く、
    /// 経路そのものは生きている——MISROUTED にすると経路設計者が
    /// 無用な作り直しをする。
    ///
    @Test
    def testDecisionTable(): Unit \ Assert =
        let snapshot = transshipSnapshot();
        let cases =
            // (種別, 場所, 航海番号, 期待)
            (HandlingType.Receive, "JPOSA", None, HandlingValidity.Valid) ::
            (HandlingType.Receive, "JPNGO", None, HandlingValidity.Warned) ::
            (HandlingType.Load, "JPOSA", Some("V-0001S"), HandlingValidity.Valid) ::
            (HandlingType.Load, "JPNGO", Some("V-0001S"), HandlingValidity.Misrouted) ::
            (HandlingType.Unload, "JPTYO", Some("V-0001S"), HandlingValidity.Valid) ::
            (HandlingType.Unload, "JPNGO", Some("V-0001S"), HandlingValidity.Misrouted) ::
            Nil;
        List.forEach(c -> {
            let (t, place, voyage, expected) = c;
            let actual = isValidFor(activity(t, place, voyage), snapshot);
            if (actual == expected) ()
            else Assert.fail("${t} を ${place} で記録したときの判定が " +
                             "${actual} でした（期待 ${expected}）")
        }, cases)

    ///
    /// **予定に無い便に載せたら MISROUTED であること**。
    ///
    /// 港は旅程のどこかと一致していても、**便が違えば違う船**である。
    /// この式が偽になる入力: 航海番号を見ずに港だけで照合する実装。
    ///
    @Test
    def testLoadOnUnplannedVoyageIsMisrouted(): Unit \ Assert =
        let a = activity(HandlingType.Load, "JPOSA", Some("V-9999X"));
        Assert.assertTrue(isValidFor(a, transshipSnapshot()) == HandlingValidity.Misrouted)

    ///
    /// **同じ港を 2 度通る旅程でも便で区別すること**。
    ///
    /// 積替で戻る旅程では、港だけで比べると誤って一致する。
    /// ここでは V-0002S の積込港は JPTYO であり、JPOSA ではない。
    ///
    @Test
    def testMatchesLegByVoyageNotOnlyByPort(): Unit \ Assert =
        let a = activity(HandlingType.Load, "JPOSA", Some("V-0002S"));
        Assert.assertTrue(isValidFor(a, transshipSnapshot()) == HandlingValidity.Misrouted)

    ///
    /// **経路未割り当てなら警告に倒すこと**。
    ///
    /// 照合先が無い。`Misrouted` にすると「経路が違う」と読めるが、
    /// 経路そのものが無い——経路設計者は何と比べて違うのか分からない。
    ///
    @Test
    def testUnroutedCargoYieldsWarnedNotMisrouted(): Unit \ Assert =
        let a = activity(HandlingType.Load, "JPOSA", Some("V-0001S"));
        Assert.assertTrue(isValidFor(a, unroutedSnapshot()) == HandlingValidity.Warned)

    // ================================================================
    // 入力の検証
    // ================================================================

    /// 積込・荷降しは航海番号が必須であること（設計のデシジョンテーブル）
    @Test
    def testLoadAndUnloadRequireVoyageNumber(): Unit \ Assert =
        Assert.assertTrue(requiresVoyageNumber(HandlingType.Load));
        Assert.assertTrue(requiresVoyageNumber(HandlingType.Unload));
        Assert.assertTrue(not requiresVoyageNumber(HandlingType.Receive));
        Assert.assertEq(expected = Some(HandlingError.VoyageNumberIsRequired),
            errOf(register(aTrackingNumber(), HandlingType.Load, "JPOSA",
                           "2026-08-01T10:00", None, anOperator(), None, today())))

    ///
    /// **受領に航海番号を付けさせないこと**。
    ///
    /// 付けられると「この便で受け取った」という意味に読めるが、受領は便を伴わない。
    ///
    @Test
    def testReceiveRejectsVoyageNumber(): Unit \ Assert =
        Assert.assertEq(expected = Some(HandlingError.VoyageNumberIsNotApplicable),
            errOf(register(aTrackingNumber(), HandlingType.Receive, "JPOSA",
                           "2026-08-01T10:00", Some("V-0001S"), anOperator(), None, today())))

    ///
    /// **未来の日時を拒むこと**。
    ///
    /// 荷役は起きた事実の記録である。これから起きることを記録すると、
    /// 追跡画面が実態より先に進み、荷主は届いていない貨物を届いたと思う。
    ///
    @Test
    def testRejectsFutureCompletionTime(): Unit \ Assert =
        Assert.assertEq(expected = Some(HandlingError.CompletionTimeIsInFuture),
            errOf(register(aTrackingNumber(), HandlingType.Receive, "JPOSA",
                           "2099-01-01T10:00", None, anOperator(), None, today())))

    /// **当日の記録は通ること**（境界）。現場は作業直後に登録する
    @Test
    def testAllowsTodayCompletionTime(): Unit \ Assert =
        Assert.assertTrue(Result.isOk(
            register(aTrackingNumber(), HandlingType.Receive, "JPOSA",
                     today() + "T23:59", None, anOperator(), None, today())))

    /// UN/LOCODE でない場所を拒むこと
    @Test
    def testRejectsMalformedLocation(): Unit \ Assert =
        Assert.assertEq(expected = Some(HandlingError.LocationCodeIsMalformed),
            errOf(register(aTrackingNumber(), HandlingType.Receive, "大阪",
                           "2026-08-01T10:00", None, anOperator(), None, today())))

    /// 追跡番号が無ければ拒むこと（どの貨物か分からない）
    @Test
    def testRejectsEmptyTrackingNumber(): Unit \ Assert =
        Assert.assertEq(expected = Some(HandlingError.TrackingNumberIsRequired),
            errOf(register("  ", HandlingType.Receive, "JPOSA",
                           "2026-08-01T10:00", None, anOperator(), None, today())))

    /// 日時の形式が違えば拒むこと
    @Test
    def testRejectsMalformedCompletionTime(): Unit \ Assert =
        Assert.assertEq(expected = Some(HandlingError.CompletionTimeIsMalformed),
            errOf(register(aTrackingNumber(), HandlingType.Receive, "JPOSA",
                           "2026-08-01 10:00", None, anOperator(), None, today())))

    ///
    /// **空文字の航海番号は「無い」として扱うこと**。
    ///
    /// 受領のフォームでも航海番号の欄は描画されるため、空文字が送られてくる。
    /// これを値として扱うと、受領が必ず `VoyageNumberIsNotApplicable` で落ちる。
    ///
    @Test
    def testTreatsBlankVoyageNumberAsAbsent(): Unit \ Assert =
        Assert.assertTrue(Result.isOk(
            register(aTrackingNumber(), HandlingType.Receive, "JPOSA",
                     "2026-08-01T10:00", Some("   "), anOperator(), None, today())))

    // ================================================================
    // 輸送状態への写像
    // ================================================================

    ///
    /// **荷役の種別が輸送状態を決めること**（受入基準 4）。
    ///
    /// 対応は荷役の業務知識であり、Tracking の関心ではない。
    ///
    /// **中継港の荷降しと最終目的港の荷降しは別の状態になる**（IT11・US16）。
    /// 引取待ちが無いと「引取できる貨物」を画面で選べない。
    ///
    /// **通関は状態を変えない**（ADR-0014 決定 2）。`None` を返す——
    /// 現在の状態を書き戻す形にすると、書き戻しの競合を隠す。
    ///
    @Test
    def testMapsHandlingTypeToTransportStatus(): Unit \ Assert =
        let s = transshipSnapshot();
        Assert.assertEq(
            expected = Some(SharedTransportStatus.TransportStatus.Received),
            resultingStatusFor(activity(HandlingType.Receive, "JPOSA", None), s));
        Assert.assertEq(
            expected = Some(SharedTransportStatus.TransportStatus.Loaded),
            resultingStatusFor(activity(HandlingType.Load, "JPOSA",
                                          Some("V-0001S")), s));
        // 中継港（JPTYO）の荷降しは「荷降し済」
        Assert.assertEq(
            expected = Some(SharedTransportStatus.TransportStatus.Unloaded),
            resultingStatusFor(activity(HandlingType.Unload, "JPTYO",
                                          Some("V-0001S")), s));
        // **最終目的港（USLAX）の荷降しは「引取待ち」**
        Assert.assertEq(
            expected = Some(SharedTransportStatus.TransportStatus.AwaitingClaim),
            resultingStatusFor(activity(HandlingType.Unload, "USLAX",
                                          Some("V-0002S")), s));
        // 通関は状態を変えない
        Assert.assertEq(
            expected = None,
            resultingStatusFor(activity(HandlingType.Customs, "USLAX", None), s));
        Assert.assertEq(
            expected = Some(SharedTransportStatus.TransportStatus.Claimed),
            resultingStatusFor(claimActivity("USLAX"), s))

    /// 引取は荷受人確認を伴わないと構築できない（US16）
    def claimActivity(location: String): HandlingActivity =
        okOf(register(aTrackingNumber(), HandlingType.Claim, location,
                      "2026-08-01T10:00", None, anOperator(), Some("RC-12345"), today()))

    // ================================================================
    // 変換と文言
    // ================================================================

    /// 種別の往復が壊れないこと
    @Test
    def testHandlingTypeRoundTrips(): Unit \ Assert =
        let all = HandlingType.Receive :: HandlingType.Load :: HandlingType.Unload ::
                  HandlingType.Customs :: HandlingType.Claim :: Nil;
        List.forEach(t ->
            Assert.assertEq(expected = Some(t), typeFromPersisted(typeToPersisted(t))), all)

    ///
    /// **未知の種別は `None`**（既定値へ倒さない）。
    ///
    /// IT10 では `CLAIM` / `CUSTOMS` が未実装であり、ここで拒むことを
    /// 確かめていた。IT11（US16・ADR-0014）で両方を実装したため、
    /// **確かめる対象を「正典に無い値」へ移す**——
    /// 実装した値を拒み続けるテストを残すと、次に足す種別で同じ書き換えが要る。
    ///
    @Test
    def testUnknownHandlingTypeYieldsNone(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(typeFromPersisted("INSPECT")));
        Assert.assertTrue(Option.isEmpty(typeFromPersisted("")));
        Assert.assertTrue(Option.isEmpty(typeFromPersisted("claim")))

    /// 判定を載せ替えても他の値が変わらないこと
    @Test
    def testWithValidityKeepsEverythingElse(): Unit \ Assert =
        let a = activity(HandlingType.Receive, "JPOSA", None);
        let marked = withValidity(a, HandlingValidity.Warned);
        Assert.assertTrue(validityOf(marked) == HandlingValidity.Warned);
        Assert.assertEq(expected = HandlingModel.locationOf(a),
                        HandlingModel.locationOf(marked));
        Assert.assertEq(expected = HandlingModel.trackingNumberOf(a),
                        HandlingModel.trackingNumberOf(marked))

    // ================================================================
    // デシジョンテーブル: CUSTOMS / CLAIM（US16・IT11。クローズ時レビューで追加）
    // ================================================================

    ///
    /// **通関と引取は仕向港で行う。違えば警告に留める**（設計の表の CLAIM 行）。
    ///
    /// IT11 のクローズ時レビューで、`isValidFor` に足した CUSTOMS / CLAIM の分岐が
    /// **どのテストにも掛かっていない**ことが分かった。`Valid` 固定に書き換えても
    /// 緑になる状態であり、受入基準 c（引取場所が目的港と異なる場合は警告）は
    /// 実証されていなかった。
    ///
    /// **MISROUTED にはしない**。荷受人が別の場所で受け取ることは業務上ありうる——
    /// 経路そのものは生きている（RECEIVE と同じ判断）。
    ///
    @Test
    def testCustomsAndClaimDecisionTable(): Unit \ Assert =
        let s = transshipSnapshot();  // JPOSA → JPTYO → USLAX
        // 仕向港で行えば妥当
        Assert.assertEq(expected = HandlingValidity.Valid,
            isValidFor(activity(HandlingType.Customs, "USLAX", None), s));
        Assert.assertEq(expected = HandlingValidity.Valid,
            isValidFor(claimActivity("USLAX"), s));
        // 仕向港でなければ警告（**経路逸脱にはしない**）
        Assert.assertEq(expected = HandlingValidity.Warned,
            isValidFor(activity(HandlingType.Customs, "JPTYO", None), s));
        Assert.assertEq(expected = HandlingValidity.Warned,
            isValidFor(claimActivity("JPTYO"), s))

    ///
    /// **引取以外に荷受人確認を付けさせないこと**（IT11 クローズ時レビュー）。
    ///
    /// 付けられると「誰かが受け取った」と読めるが、荷受人へは渡していない。
    /// `VoyageNumberIsNotApplicable` と対称の規律であり、そちらだけ
    /// テストがあった。
    ///
    @Test
    def testConfirmationIsOnlyForClaim(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(HandlingError.ConsigneeConfirmationIsNotApplicable),
            errOf(register(aTrackingNumber(), HandlingType.Receive, "JPOSA",
                           "2026-08-01T10:00", None, anOperator(),
                           Some(aConfirmation()), today())))

    /// **小文字の追跡番号を大文字として扱うこと**（IT11 クローズ時レビュー）
    @Test
    def testUpperCasesTrackingNumber(): Unit \ Assert =
        let a = okOf(register("trk-20260805-0001", HandlingType.Receive, "JPOSA",
                              "2026-08-01T10:00", None, anOperator(), None, today()));
        Assert.assertEq(expected = "TRK-20260805-0001",
                        HandlingModel.trackingNumberOf(a))
}
