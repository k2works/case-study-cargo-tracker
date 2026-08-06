///
/// 荷役登録のオーケストレーション（US15。IT11・M6）。
///
/// # このテストが守るもの
///
/// 荷役の登録は 1 回で **3 つの集約**に触れる——荷役の記録、追跡の輸送状態、
/// （経路逸脱なら）予約の経路状態。IT10 の実装は後ろ 2 つの結果を
/// `discard` しており、**反映に失敗しても現場には「登録しました」と出た**。
///
/// 荷主の画面が更新されていないことに気付くのは、荷主が問い合わせてきたときになる。
/// ADR-0012 のリスク 2 は「反映が遅れる」と書いていたが、実際には
/// **永久に反映されない**——記述と実装が逆だった。
///
/// ここで確かめるのは「失敗が呼び出し元へ返ること」である。
/// 文言は `Flash` の責務であり、そちらで確かめる。
///
mod RegisterHandlingTest {
    use HandlingBookingMisroutingReport.{BookingMisroutingReport, ReportResult}
    use HandlingCargoSnapshotSource.CargoSnapshotSource
    use HandlingModel.{HandlingType, HandlingValidity}
    use HandlingRegisterHandling.{RegisterResult, ReflectionOutcome}
    use HandlingRepo.HandlingRepo
    use HandlingTrackingTransportStatusUpdate.{TrackingTransportStatusUpdate, UpdateResult}
    use InMemoryHandlers.withFixedClock

    def aTrackingNumber(): String = "TRK-20260805-0001"
    def today(): String = "2026-08-05"

    /// 大阪 → ロサンゼルス（V-0001S）の直行
    def snapshot(): HandlingModel.CargoSnapshot =
        HandlingModel.cargoSnapshot("JPOSA", "USLAX",
            HandlingModel.legSnapshot("JPOSA", "USLAX", "V-0001S") :: Nil)

    /// 記録は必ず成功する。反映側だけをテストごとに差し替える
    def withRepo(f: Unit -> a \ ef + HandlingRepo): a \ ef - HandlingRepo =
        withRepoCleared(true, f)

    ///
    /// 通関の有無を差し替えられる版（IT11 クローズ時レビュー・tester 10）。
    ///
    /// 既定を `true` 固定にしていたため、**ゲートの分岐がユニットレベルで
    /// 一度も false を通っていなかった**。HTTP を立てずに固定できる。
    ///
    def withRepoCleared(cleared: Bool, f: Unit -> a \ ef + HandlingRepo):
            a \ ef - HandlingRepo =
        withRepoState(cleared, false, f)

    /// 通関の有無と引取の有無を差し替えられる版（IT12・M10）
    def withRepoState(cleared: Bool, claimed: Bool,
                      f: Unit -> a \ ef + HandlingRepo): a \ ef - HandlingRepo =
        run {
            f()
        } with handler HandlingRepo {
            def save(_a, k)           = k()
            def findRecent(_n, _l, k) = k(Nil)
            def countMatching(_n, k)  = k(0)
            def hasCustomsClearance(_n, k) = k(cleared)
            def hasClaim(_n, k)       = k(claimed)
        }

    def withSnapshot(found: Option[HandlingModel.CargoSnapshot],
                     f: Unit -> a \ ef + CargoSnapshotSource):
            a \ ef - CargoSnapshotSource =
        run {
            f()
        } with handler CargoSnapshotSource {
            def findByTrackingNumber(_n, k) = k(found)
        }

    def withTrackingUpdate(result: UpdateResult,
                           f: Unit -> a \ ef + TrackingTransportStatusUpdate):
            a \ ef - TrackingTransportStatusUpdate =
        run {
            f()
        } with handler TrackingTransportStatusUpdate {
            def apply(_a, _s, k) = k(result)
        }

    def withMisroutingReport(result: ReportResult,
                             f: Unit -> a \ ef + BookingMisroutingReport):
            a \ ef - BookingMisroutingReport =
        run {
            f()
        } with handler BookingMisroutingReport {
            def report(_b, k) = k(result)
        }

    ///
    /// すべて反映できたら `Reflected` を返す。
    ///
    @Test
    def testReportsCompleteReflection(): Unit \ Assert =
        let result = withRepo(() ->
            withSnapshot(Some(snapshot()), () ->
                withTrackingUpdate(UpdateResult.Updated, () ->
                    withMisroutingReport(ReportResult.Reported, () ->
                        withFixedClock(0i64, today(), () ->
                            HandlingRegisterHandling.register(
                                aTrackingNumber(), HandlingType.Receive,
                                "JPOSA", "2026-08-01T10:00", None, "sato", None))))));
        Assert.assertEq(
            expected = RegisterResult.Registered(HandlingValidity.Valid,
                                                 ReflectionOutcome.Reflected),
            result)

    ///
    /// **追跡へ反映できなければ、そのことが返ること**（M6）。
    ///
    /// 記録自体は残す——荷役は起きた事実であり、反映の失敗で消してはいけない。
    /// しかし「登録しました」だけを出すと、**荷主の画面が古いままであることを
    /// 誰も知らない**。
    ///
    @Test
    def testReportsTrackingNotReflected(): Unit \ Assert =
        let result = withRepo(() ->
            withSnapshot(Some(snapshot()), () ->
                withTrackingUpdate(UpdateResult.TrackingNotFound, () ->
                    withMisroutingReport(ReportResult.Reported, () ->
                        withFixedClock(0i64, today(), () ->
                            HandlingRegisterHandling.register(
                                aTrackingNumber(), HandlingType.Receive,
                                "JPOSA", "2026-08-01T10:00", None, "sato", None))))));
        Assert.assertEq(
            expected = RegisterResult.Registered(HandlingValidity.Valid,
                                                 ReflectionOutcome.TrackingNotReflected),
            result)

    ///
    /// **経路逸脱を予約へ報告できなければ、そのことが返ること**（M6）。
    ///
    /// 報告が落ちると、経路設計者は貨物が予定と違う経路に乗ったことを知らない。
    /// 荷役作業員の画面には「経路設計者へご連絡ください」と出るのに、
    /// **受け手には何も届いていない**。
    ///
    @Test
    def testReportsMisroutingNotReported(): Unit \ Assert =
        let result = withRepo(() ->
            withSnapshot(Some(snapshot()), () ->
                withTrackingUpdate(UpdateResult.Updated, () ->
                    withMisroutingReport(ReportResult.NotRouted, () ->
                        withFixedClock(0i64, today(), () ->
                            HandlingRegisterHandling.register(
                                aTrackingNumber(), HandlingType.Load,
                                "JPTYO", "2026-08-01T10:00", Some("V-0001S"), "sato", None))))));
        Assert.assertEq(
            expected = RegisterResult.Registered(HandlingValidity.Misrouted,
                                                 ReflectionOutcome.MisroutingNotReported),
            result)

    ///
    /// **追跡の失敗を優先して返すこと**。
    ///
    /// 両方落ちたときに 1 つしか出せないなら、**荷主が見る側**を選ぶ。
    /// 経路逸脱の報告は経路設計者への連絡で代替できるが、
    /// 荷主の画面は誰も手で直せない。
    ///
    @Test
    def testTrackingFailureTakesPrecedence(): Unit \ Assert =
        let result = withRepo(() ->
            withSnapshot(Some(snapshot()), () ->
                withTrackingUpdate(UpdateResult.TrackingNotFound, () ->
                    withMisroutingReport(ReportResult.NotRouted, () ->
                        withFixedClock(0i64, today(), () ->
                            HandlingRegisterHandling.register(
                                aTrackingNumber(), HandlingType.Load,
                                "JPTYO", "2026-08-01T10:00", Some("V-0001S"), "sato", None))))));
        Assert.assertEq(
            expected = RegisterResult.Registered(HandlingValidity.Misrouted,
                                                 ReflectionOutcome.TrackingNotReflected),
            result)

    ///
    /// 貨物が無ければ**何も書かない**（IT10 から続く判断。回帰として残す）。
    ///
    @Test
    def testUnknownCargoWritesNothing(): Unit \ Assert =
        let result = withRepo(() ->
            withSnapshot(None, () ->
                withTrackingUpdate(UpdateResult.Updated, () ->
                    withMisroutingReport(ReportResult.Reported, () ->
                        withFixedClock(0i64, today(), () ->
                            HandlingRegisterHandling.register(
                                aTrackingNumber(), HandlingType.Receive,
                                "JPOSA", "2026-08-01T10:00", None, "sato", None))))));
        Assert.assertEq(expected = RegisterResult.CargoNotFound, result)

    ///
    /// **通関が記録されていなければ引取を拒むこと**（ADR-0014 決定 1）。
    ///
    /// 通関前の引き渡しは業務上の重大な逸脱である。
    /// **記録の前に止める**——「記録は残ったが引取ではない」行は後から読めない。
    ///
    @Test
    def testClaimIsRejectedWhenCustomsIsNotCleared(): Unit \ Assert =
        let result = withRepoCleared(false, () ->
            withSnapshot(Some(snapshot()), () ->
                withTrackingUpdate(UpdateResult.Updated, () ->
                    withMisroutingReport(ReportResult.Reported, () ->
                        withFixedClock(0i64, today(), () ->
                            HandlingRegisterHandling.register(
                                aTrackingNumber(), HandlingType.Claim,
                                "USLAX", "2026-08-01T10:00", None, "sato",
                                Some("RC-12345")))))));
        Assert.assertEq(expected = RegisterResult.CustomsNotCleared, result)

    /// 通関が記録されていれば引取は通ること（ゲートの対）
    @Test
    def testClaimPassesWhenCustomsIsCleared(): Unit \ Assert =
        let result = withRepoCleared(true, () ->
            withSnapshot(Some(snapshot()), () ->
                withTrackingUpdate(UpdateResult.Updated, () ->
                    withMisroutingReport(ReportResult.Reported, () ->
                        withFixedClock(0i64, today(), () ->
                            HandlingRegisterHandling.register(
                                aTrackingNumber(), HandlingType.Claim,
                                "USLAX", "2026-08-01T10:00", None, "sato",
                                Some("RC-12345")))))));
        Assert.assertEq(
            expected = RegisterResult.Registered(HandlingValidity.Valid,
                                                 ReflectionOutcome.Reflected),
            result)

    ///
    /// **引取済の貨物には荷役を記録できないこと**（IT12・IT11 レビュー M10/M17）。
    ///
    /// IT11 では引取済の貨物に積込を記録でき、`CLAIMED → LOADED` へ戻った。
    /// しかも手動更新は巻き戻しを拒むため**誰も直せなかった**——
    /// 荷主の画面に「引取済だったものが積込に戻る」ことが起き、放置しかできない。
    ///
    /// **記録の前に止める**。引取は所有権の移転であり、その後に荷役は起きない。
    ///
    @Test
    def testHandlingIsRejectedAfterClaim(): Unit \ Assert =
        let result = withRepoState(true, true, () ->
            withSnapshot(Some(snapshot()), () ->
                withTrackingUpdate(UpdateResult.Updated, () ->
                    withMisroutingReport(ReportResult.Reported, () ->
                        withFixedClock(0i64, today(), () ->
                            HandlingRegisterHandling.register(
                                aTrackingNumber(), HandlingType.Load,
                                "JPOSA", "2026-08-01T10:00", Some("V-0001S"),
                                "sato", None))))));
        Assert.assertEq(expected = RegisterResult.AlreadyClaimed, result)

    ///
    /// **引取の重複も同じ経路で止まること**（IT11 レビュー M17）。
    ///
    /// `CLAIM` が 2 行並ぶと、どちらが正の証跡か読めない。
    ///
    @Test
    def testSecondClaimIsRejected(): Unit \ Assert =
        let result = withRepoState(true, true, () ->
            withSnapshot(Some(snapshot()), () ->
                withTrackingUpdate(UpdateResult.Updated, () ->
                    withMisroutingReport(ReportResult.Reported, () ->
                        withFixedClock(0i64, today(), () ->
                            HandlingRegisterHandling.register(
                                aTrackingNumber(), HandlingType.Claim,
                                "USLAX", "2026-08-01T10:00", None,
                                "sato", Some("RC-12345")))))));
        Assert.assertEq(expected = RegisterResult.AlreadyClaimed, result)
}
