///
/// 追跡番号の採番と追跡活動（US14。IT10）。
///
/// # このテストが守るもの
///
/// 追跡番号は**荷主が問い合わせに使う唯一の手掛かり**である。形式が崩れると
/// 荷主が入力した番号で引けなくなり、営業に問い合わせが来る。
///
/// 集約を直接叩く。HTTP を通すと認可・CSRF・画面の描画が混ざり、
/// どれが落ちたのか分からなくなる。
///
mod TrackingActivityTest {
    use TrackingModel.{TrackingActivity, TrackingError}
    use TrackingModel.{issue, formatNumber, isWellFormed, reconstruct,
                       trackingNumberOf, bookingIdOf, transportStatusOf}

    def aBookingId(): String = "BK-3F2504E0"

    def okOf(r: Result[TrackingError, TrackingActivity]): TrackingActivity = match r {
        case Ok(a)  => a
        case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
    }

    def errOf(r: Result[TrackingError, TrackingActivity]): Option[TrackingError] = match r {
        case Err(e) => Some(e)
        case Ok(_)  => None
    }

    // ---- 採番の形式 ----

    ///
    /// **`TRK-YYYYMMDD-NNNN` に組み立てること**。
    ///
    /// 形式は UI 設計が定めている。この式が偽になる入力: 日付のハイフンを
    /// 落とし忘れる実装（`TRK-2026-08-05-0001` は 17 文字にならない）。
    ///
    @Test
    def testFormatsNumberFromDateAndSequence(): Unit \ Assert =
        Assert.assertEq(expected = "TRK-20260805-0001", formatNumber("2026-08-05", 1))

    ///
    /// **連番を 4 桁でゼロ詰めすること**。
    ///
    /// 詰めないと文字列として並べたときに順序が狂う——
    /// `TRK-...-10` が `TRK-...-2` より前に来る。
    ///
    @Test
    def testPadsSequenceToFourDigits(): Unit \ Assert =
        Assert.assertEq(expected = "TRK-20260805-0042", formatNumber("2026-08-05", 42));
        Assert.assertEq(expected = "TRK-20260805-0999", formatNumber("2026-08-05", 999));
        Assert.assertEq(expected = "TRK-20260805-1000", formatNumber("2026-08-05", 1000))

    /// 4 桁を超えたら切り詰めないこと（桁あふれを黙って隠さない）
    @Test
    def testDoesNotTruncateOverflowingSequence(): Unit \ Assert =
        Assert.assertEq(expected = "TRK-20260805-12345", formatNumber("2026-08-05", 12345))

    /// 組み立てた番号は必ず形式検査を通ること
    @Test
    def testFormattedNumberIsWellFormed(): Unit \ Assert =
        Assert.assertTrue(isWellFormed(formatNumber("2026-08-05", 1)));
        Assert.assertTrue(isWellFormed(formatNumber("2099-12-31", 9999)))

    ///
    /// 形式に合わない値を弾くこと。
    ///
    /// **境界を並べる**——長さ・接頭辞・区切りの位置・数字以外。
    ///
    @Test
    def testRejectsMalformedNumbers(): Unit \ Assert =
        let malformed =
            "" ::
            "TRK-20260805-001" ::      // 連番が 3 桁（16 文字）
            "TRK-20260805-00011" ::    // 連番が 5 桁（18 文字）
            "TRX-20260805-0001" ::     // 接頭辞が違う
            "TRK-2026080-50001" ::     // 区切りの位置が違う
            "TRK-2026080X-0001" ::     // 日付に数字以外
            "TRK-20260805-00A1" :: Nil; // 連番に数字以外
        List.forEach(v ->
            if (isWellFormed(v))
                Assert.fail("形式に合わない番号を通しました: ${v}")
            else (), malformed)

    // ---- 発行 ----

    ///
    /// **発行した追跡活動は「受領待ち」で始まること**（受入基準 3）。
    ///
    /// この式が偽になる入力: 既定値を `Unknown` にする実装。
    /// 「まだ受け取っていない」と「状態が読めない」は業務上まったく違う——
    /// 前者は正常、後者は調査が要る。
    ///
    @Test
    def testIssuedActivityStartsNotReceived(): Unit \ Assert =
        let a = okOf(issue("TRK-20260805-0001", aBookingId()));
        Assert.assertTrue(
            transportStatusOf(a) == SharedTransportStatus.TransportStatus.NotReceived);
        Assert.assertEq(expected = "TRK-20260805-0001", trackingNumberOf(a));
        Assert.assertEq(expected = aBookingId(), bookingIdOf(a))

    /// 空の追跡番号を拒むこと
    @Test
    def testRejectsEmptyTrackingNumber(): Unit \ Assert =
        Assert.assertEq(expected = Some(TrackingError.TrackingNumberIsRequired),
                        errOf(issue("   ", aBookingId())))

    /// 形式に合わない追跡番号を拒むこと
    @Test
    def testRejectsMalformedTrackingNumberOnIssue(): Unit \ Assert =
        Assert.assertEq(expected = Some(TrackingError.TrackingNumberIsMalformed),
                        errOf(issue("TRK-1", aBookingId())))

    /// 予約番号が無ければ拒むこと（どの貨物の追跡か分からない）
    @Test
    def testRejectsEmptyBookingId(): Unit \ Assert =
        Assert.assertEq(expected = Some(TrackingError.BookingIdIsRequired),
                        errOf(issue("TRK-20260805-0001", "")))

    // ---- 復元 ----

    ///
    /// **未知の輸送状態は `None` を返すこと**。
    ///
    /// 既定値へ倒すと、輸送中の貨物が未受領に見える——荷主が
    /// 「まだ集荷されていない」と誤解し、営業に問い合わせが来る。
    /// 行を落とすほうが害が小さい（読み出し側が「読めなかった」と言える）。
    ///
    @Test
    def testUnknownPersistedStatusYieldsNone(): Unit \ Assert =
        Assert.assertTrue(
            Option.isEmpty(reconstruct("TRK-20260805-0001", aBookingId(), "SOMETHING_ELSE")))

    /// 既知の輸送状態は復元できること
    @Test
    def testReconstructsKnownStatus(): Unit \ Assert =
        match reconstruct("TRK-20260805-0001", aBookingId(), "LOADED") {
            case None    => Assert.fail("既知の輸送状態を復元できませんでした")
            case Some(a) =>
                Assert.assertTrue(
                    transportStatusOf(a) == SharedTransportStatus.TransportStatus.Loaded)
        }

    ///
    /// **輸送状態の往復が壊れないこと**。
    ///
    /// 永続化値 → 列挙 → 永続化値 で同じ値に戻る。この式が偽になる入力:
    /// `toPersisted` と `fromPersisted` の綴りが片方だけ変わった実装。
    ///
    @Test
    def testTransportStatusRoundTrips(): Unit \ Assert =
        let all =
            SharedTransportStatus.TransportStatus.NotReceived ::
            SharedTransportStatus.TransportStatus.Received ::
            SharedTransportStatus.TransportStatus.Loaded ::
            SharedTransportStatus.TransportStatus.OnboardCarrier ::
            SharedTransportStatus.TransportStatus.Unloaded ::
            SharedTransportStatus.TransportStatus.AwaitingClaim ::
            SharedTransportStatus.TransportStatus.Claimed ::
            SharedTransportStatus.TransportStatus.Exception ::
            SharedTransportStatus.TransportStatus.Unknown :: Nil;
        List.forEach(s ->
            Assert.assertEq(
                expected = Some(s),
                SharedTransportStatus.fromPersisted(SharedTransportStatus.toPersisted(s))),
            all)
}
