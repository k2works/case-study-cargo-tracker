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
            Option.isEmpty(reconstruct("TRK-20260805-0001", aBookingId(), "SOMETHING_ELSE", None)))

    /// 既知の輸送状態は復元できること
    @Test
    def testReconstructsKnownStatus(): Unit \ Assert =
        match reconstruct("TRK-20260805-0001", aBookingId(), "LOADED", None) {
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

    // ================================================================
    // 手動更新（US17・IT11。クローズ時レビューで追加）
    // ================================================================

    ///
    /// このテスト群が守るもの。
    ///
    /// IT11 のクローズ時レビューで、`updateManually` に**ユニットテストが 1 件も
    /// 無い**ことが分かった。155 行の不変条件が HTTP 経由の 2 本でしか
    /// 確かめられておらず、実際に 2 つの欠陥が潜んでいた——
    /// 書式の不一致で同日の逆行が素通りし、場所は空文字しか見ていなかった。
    ///
    def loadedActivity(): TrackingModel.TrackingActivity =
        match TrackingModel.issue("TRK-20260805-0001", "BK-3F2504E0") {
            case Ok(a)  => TrackingModel.applyHandling(a,
                Some(SharedTransportStatus.TransportStatus.Loaded),
                TrackingModel.event("LOAD", "2026-08-02T09:00", "JPOSA", None))
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    def updated(location: String, occurredAt: String, latest: Option[String]):
            Result[TrackingModel.TrackingError, TrackingModel.TrackingActivity] =
        TrackingModel.updateManually(loadedActivity(),
            SharedTransportStatus.TransportStatus.OnboardCarrier,
            location, occurredAt, latest)

    ///
    /// **同じ日の逆行を拒むこと**（IT11 クローズ時レビュー・tester 2）。
    ///
    /// 画面から来るのは `2026-08-02T09:00`、DB から来るのは
    /// `2026-08-02 09:00:00` である。**書式を揃えずに辞書順で比べると
    /// 10 文字目が `T` と空白の比較になり、同じ日付では必ず「前ではない」**
    /// と判定されていた。日をまたぐ逆行だけが偶然拒まれていた。
    ///
    @Test
    def testRejectsSameDayRewind(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(TrackingModel.TrackingError.OccurredAtWouldRewind),
            errOf(updated("JPOSA", "2026-08-02T08:59", Some("2026-08-02 09:00:00"))))

    /// 日をまたぐ逆行も拒むこと（元から効いていた側。回帰として残す）
    @Test
    def testRejectsEarlierDayRewind(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(TrackingModel.TrackingError.OccurredAtWouldRewind),
            errOf(updated("JPOSA", "2026-08-01T23:59", Some("2026-08-02 09:00:00"))))

    /// **直近と同時刻は通すこと**（境界）。同時に起きた出来事は業務上ありうる
    @Test
    def testAllowsSameInstant(): Unit \ Assert =
        Assert.assertTrue(Result.isOk(
            updated("JPOSA", "2026-08-02T09:00", Some("2026-08-02 09:00:00"))))

    /// 直近より後は通すこと（境界）
    @Test
    def testAllowsLaterInstant(): Unit \ Assert =
        Assert.assertTrue(Result.isOk(
            updated("JPOSA", "2026-08-02T09:01", Some("2026-08-02 09:00:00"))))

    /// 出来事が 1 件も無ければ、どの時刻でも通すこと
    @Test
    def testAllowsAnyTimeWhenNoEvents(): Unit \ Assert =
        Assert.assertTrue(Result.isOk(updated("JPOSA", "2026-01-01T00:00", None)))

    ///
    /// **マスタに無い形式の場所を拒むこと**（IT11 クローズ時レビュー）。
    ///
    /// 集約は空文字しか見ておらず、6 文字でも日本語でも通していた。
    /// `tracking_handling_event.location_unlocode` は `VARCHAR(5)` の外部キーであり、
    /// **画面まで到達せず 500 になっていた**。
    ///
    /// **形式とマスタ照合は別**である。`TOKYO` は形式として正しく、
    /// 実在しないことは集約には分からない——照合はルートが行う
    /// （`TrackingRoutes.isKnownLocation`。荷役登録と同じ形）。
    ///
    @Test
    def testRejectsMalformedLocation(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(TrackingModel.TrackingError.LocationCodeIsMalformed),
            errOf(updated("TOKYOO", "2026-08-03T09:00", None)));
        Assert.assertEq(
            expected = Some(TrackingModel.TrackingError.LocationCodeIsMalformed),
            errOf(updated("大阪", "2026-08-03T09:00", None)));
        Assert.assertEq(
            expected = Some(TrackingModel.TrackingError.LocationIsRequired),
            errOf(updated("  ", "2026-08-03T09:00", None)))

    /// 小文字の港コードは大文字として扱うこと（現場は片手で入力している）
    @Test
    def testUpperCasesLocation(): Unit \ Assert =
        match updated("jposa", "2026-08-03T09:00", None) {
            case Err(e) => Assert.fail("小文字の港コードが拒まれた: ${e}")
            case Ok(a)  => match List.head(TrackingModel.pendingEventsOf(a)) {
                case None    => Assert.fail("出来事が記録されていない")
                case Some(e) =>
                    Assert.assertEq(expected = "JPOSA", TrackingModel.eventLocationOf(e))
            }
        }

    ///
    /// **段階の巻き戻しを拒むこと**。
    ///
    /// 打ち間違いで「受取済」へ戻すと、荷主には貨物が港へ帰ったように見える。
    ///
    @Test
    def testRejectsStatusRewind(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(TrackingModel.TrackingError.StatusWouldRewind),
            errOf(TrackingModel.updateManually(loadedActivity(),
                SharedTransportStatus.TransportStatus.Received,
                "JPOSA", "2026-08-03T09:00", None)))

    ///
    /// **例外を踏み台にした巻き戻しの経路を塞ぐこと**（IT11 クローズ時レビュー）。
    ///
    /// `EXCEPTION` と `UNKNOWN` は段階を持たないため順序の外にある。
    /// 手で付けられると `CLAIMED → EXCEPTION → NOT_RECEIVED` の 2 手で
    /// 巻き戻せてしまう——順序の外にある値を経由すると順序の判定が効かない。
    ///
    /// **入口で拒む**。画面（`selectableStatuses`）も出していないが、
    /// 選択肢は誰でも書き換えられるため集約が拒む。
    ///
    @Test
    def testRejectsStatusesOutsideTheStageOrder(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(TrackingModel.TrackingError.StatusIsNotSelectable),
            errOf(TrackingModel.updateManually(loadedActivity(),
                SharedTransportStatus.TransportStatus.Exception,
                "JPOSA", "2026-08-03T09:00", None)));
        Assert.assertEq(
            expected = Some(TrackingModel.TrackingError.StatusIsNotSelectable),
            errOf(TrackingModel.updateManually(loadedActivity(),
                SharedTransportStatus.TransportStatus.Unknown,
                "JPOSA", "2026-08-03T09:00", None)))

    // ================================================================
    // 例外（US19・IT12）
    // ================================================================

    ///
    /// **エスカレーションは 48 時間を「超えた」ときだけ**（US19 の追加ルール）。
    ///
    /// [テスト戦略](../../../../docs/design/test_strategy.md) 8.2 が
    /// 閾値ちょうど・未満・超過の 3 ケースを求めている。
    /// **ちょうどを含めるか否かは、境界で必ず問題になる**。
    ///
    @Test
    def testEscalatesOnlyBeyondThreshold(): Unit \ Assert =
        // 47 時間: しない
        Assert.assertTrue(not TrackingModel.shouldEscalate(
            "2026-08-01T00:00", "2026-08-02T23:00"));
        // 48 時間ちょうど: しない（**超えていない**）
        Assert.assertTrue(not TrackingModel.shouldEscalate(
            "2026-08-01T00:00", "2026-08-03T00:00"));
        // 49 時間: する
        Assert.assertTrue(TrackingModel.shouldEscalate(
            "2026-08-01T00:00", "2026-08-03T01:00"))

    ///
    /// **例外の記録で発生前の状態を退避すること**（観点 1）。
    ///
    /// 履歴から再導出すると、ユニットテストが緑でもクロスリクエストで誤復帰する。
    ///
    @Test
    def testRaiseKeepsStatusBeforeException(): Unit \ Assert =
        match TrackingModel.raiseException(loadedActivity(),
                TrackingModel.ExceptionType.Delay, "USLAX",
                "2026-08-05T09:00", "本船の遅延", false) {
            case Err(e) => Assert.fail("例外の記録に失敗した: ${e}")
            case Ok(a)  => {
                Assert.assertEq(
                    expected = SharedTransportStatus.TransportStatus.Exception,
                    TrackingModel.transportStatusOf(a));
                Assert.assertEq(
                    expected = Some(SharedTransportStatus.TransportStatus.Loaded),
                    TrackingModel.statusBeforeExceptionOf(a))
            }
        }

    ///
    /// **未解決の例外があるうちは次を記録できないこと**（IT12 クローズ時レビュー）。
    ///
    /// 当初は「2 度目でも退避を上書きしない」だけにしていたが、
    /// **未解決の行が 2 つ並ぶ**と解決の SQL が両方を同じ日時・同じ対応方針で
    /// 塗り潰し、どちらの対応だったか読めなくなる。
    ///
    /// 先の例外を解決してから次を記録する——業務としてもその順序である。
    ///
    @Test
    def testSecondExceptionIsRejectedWhileOpen(): Unit \ Assert =
        match TrackingModel.raiseException(loadedActivity(),
                TrackingModel.ExceptionType.Delay, "USLAX",
                "2026-08-05T09:00", "遅延", false) {
            case Err(e)  => Assert.fail("例外の記録に失敗した: ${e}")
            case Ok(one) =>
                Assert.assertEq(
                    expected = Some(TrackingModel.TrackingError.ExceptionAlreadyOpen),
                    errOf(TrackingModel.raiseException(one,
                        TrackingModel.ExceptionType.Delay, "USLAX",
                        "2026-08-06T09:00", "さらに遅延", false)))
        }

    ///
    /// **解決すれば次の例外を記録できること**（上の対）。
    ///
    @Test
    def testExceptionCanBeRaisedAgainAfterResolve(): Unit \ Assert =
        match TrackingModel.raiseException(loadedActivity(),
                TrackingModel.ExceptionType.Delay, "USLAX",
                "2026-08-05T09:00", "遅延", false) {
            case Err(e)  => Assert.fail("例外の記録に失敗した: ${e}")
            case Ok(one) =>
                match TrackingModel.resolveException(one, "2026-08-07T09:00",
                                                     Some("代替船"), None) {
                    case Err(e)       => Assert.fail("解決に失敗した: ${e}")
                    case Ok(resolved) =>
                        Assert.assertTrue(Result.isOk(TrackingModel.raiseException(
                            resolved, TrackingModel.ExceptionType.Delay, "USLAX",
                            "2026-08-08T09:00", "再度の遅延", false)))
                }
        }

    ///
    /// **対応内容が空なら拒むこと**（IT12 クローズ時レビュー）。
    ///
    /// 空のまま送ると既存の対応内容が消え、しかも成功として返っていた。
    ///
    @Test
    def testEmptyResolutionIsRejected(): Unit \ Assert =
        match TrackingModel.raiseException(loadedActivity(),
                TrackingModel.ExceptionType.Delay, "USLAX",
                "2026-08-05T09:00", "遅延", false) {
            case Err(e)  => Assert.fail("例外の記録に失敗した: ${e}")
            case Ok(one) => {
                Assert.assertEq(
                    expected = Some(TrackingModel.TrackingError.ResolutionIsEmpty),
                    errOf(TrackingModel.withResolutionNotes(one, None, None)));
                // **状態は変えない**（対応の記録は解決ではない）
                match TrackingModel.withResolutionNotes(one, Some("代替船"), None) {
                    case Err(e)  => Assert.fail("対応の記録に失敗した: ${e}")
                    case Ok(noted) =>
                        Assert.assertEq(
                            expected = SharedTransportStatus.TransportStatus.Exception,
                            TrackingModel.transportStatusOf(noted))
                }
            }
        }

    ///
    /// **新しい到着予定日の書式を集約が検証すること**（IT12 クローズ時レビュー）。
    ///
    /// 当初は素通しで `CAST(? AS TIMESTAMP)` に渡っており、`abc` を送ると
    /// 応答が返らなかった——IT4 で同じ形を踏んでいる。
    ///
    @Test
    def testMalformedRevisedArrivalIsRejected(): Unit \ Assert =
        match TrackingModel.raiseException(loadedActivity(),
                TrackingModel.ExceptionType.Delay, "USLAX",
                "2026-08-05T09:00", "遅延", false) {
            case Err(e)  => Assert.fail("例外の記録に失敗した: ${e}")
            case Ok(one) => {
                Assert.assertEq(
                    expected = Some(TrackingModel.TrackingError.RevisedArrivalIsMalformed),
                    errOf(TrackingModel.withResolutionNotes(one, None, Some("abc"))));
                Assert.assertEq(
                    expected = Some(TrackingModel.TrackingError.RevisedArrivalIsMalformed),
                    errOf(TrackingModel.resolveException(one, "2026-08-07T09:00",
                                                         None, Some("2026-02-30"))))
            }
        }

    ///
    /// **解決すると発生前の状態へ戻ること**（受入基準 5）。
    ///
    @Test
    def testResolveRestoresPreviousStatus(): Unit \ Assert =
        match TrackingModel.raiseException(loadedActivity(),
                TrackingModel.ExceptionType.Delay, "USLAX",
                "2026-08-05T09:00", "遅延", false) {
            case Err(e)     => Assert.fail("例外の記録に失敗した: ${e}")
            case Ok(raised) =>
                match TrackingModel.resolveException(raised, "2026-08-07T09:00",
                                                     Some("代替船に振替"), None) {
                    case Err(e)       => Assert.fail("解決に失敗した: ${e}")
                    case Ok(resolved) => {
                        Assert.assertEq(
                            expected = SharedTransportStatus.TransportStatus.Loaded,
                            TrackingModel.transportStatusOf(resolved));
                        // **退避は消す**。残すと 2 度目の解決が成立してしまう
                        Assert.assertTrue(Option.isEmpty(
                            TrackingModel.statusBeforeExceptionOf(resolved)))
                    }
                }
        }

    ///
    /// **未解決の例外が無ければ解決を拒むこと**。
    ///
    /// 解決するものが無いのに「解決しました」と出すと、
    /// 追跡管理者は対応済みだと誤解する。
    ///
    @Test
    def testResolveWithoutExceptionIsRejected(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(TrackingModel.TrackingError.NoActiveException),
            errOf(TrackingModel.resolveException(loadedActivity(),
                                                 "2026-08-07T09:00", None, None)))

    /// **理由の無い例外を拒むこと**。荷主が知りたいのは何が起きたかである
    @Test
    def testExceptionRequiresDescription(): Unit \ Assert =
        Assert.assertEq(
            expected = Some(TrackingModel.TrackingError.DescriptionIsRequired),
            errOf(TrackingModel.raiseException(loadedActivity(),
                TrackingModel.ExceptionType.Delay, "USLAX",
                "2026-08-05T09:00", "   ", false)))
}
