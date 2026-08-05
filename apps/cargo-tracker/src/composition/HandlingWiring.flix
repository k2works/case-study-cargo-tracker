///
/// Handling Context の配線（IT10・US15）。
///
/// 分割の意図と `src/composition/` に置く理由は [ShipperWiring](ShipperWiring.flix) を参照。
///
/// 本 BC は**リポジトリ 1 本と ACL 3 本**を持つ。3 本とも
/// [ADR-0012](../../../../docs/adr/ADR-0012-cross-context-writes-go-through-the-target-aggregate.md)
/// の形（同期の ACL ポート + 相手の集約）であり、翻訳は `acl/` にある（規約 11）。
///
/// | ポート | 向き | 何をするか |
/// | :--- | :--- | :--- |
/// | `CargoSnapshotSource` | Handling → Booking（読） | 妥当性判定の材料を引く |
/// | `TrackingTransportStatusUpdate` | Handling → Tracking（書） | 輸送状態を反映する |
/// | `BookingMisroutingReport` | Handling → Booking（書） | 経路逸脱を報告する |
///
mod HandlingWiring {
    use HandlingBookingMisroutingReport.BookingMisroutingReport
    use HandlingCargoSnapshotSource.CargoSnapshotSource
    use HandlingJdbcHandlingRepo.withJdbcHandlingRepo
    use HandlingRepo.HandlingRepo
    use HandlingTrackingTransportStatusUpdate.TrackingTransportStatusUpdate

    /// 本 BC が要求する効果
    pub type alias Eff = HandlingRepo + CargoSnapshotSource
                       + TrackingTransportStatusUpdate + BookingMisroutingReport

    /// 本番のアダプタを適用する
    pub def withDb(f: Unit -> a \ ef + Eff):
            a \ (ef - Eff) + CargoSnapshotSourceAdapter.Requires
                          + TrackingTransportStatusAdapter.Requires
                          + BookingMisroutingReportAdapter.Requires
                          + SharedDbTx.Tx + IO =
        withJdbcHandlingRepo(() ->
            CargoSnapshotSourceAdapter.withCargoSnapshotSource(() ->
                TrackingTransportStatusAdapter.withTrackingTransportStatusUpdate(() ->
                    BookingMisroutingReportAdapter.withBookingMisroutingReport(f))))

    /// DB を使わない経路（`TxMode.NoTx`）で適用する
    pub def withoutDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) =
        run {
            run {
                run {
                    run {
                        f()
                    } with handler BookingMisroutingReport {
                        def report(_b, _k) = WiringGuard.noDbViolation("BookingMisroutingReport")
                    }
                } with handler TrackingTransportStatusUpdate {
                    def apply(_n, _s, _k) =
                        WiringGuard.noDbViolation("TrackingTransportStatusUpdate")
                }
            } with handler CargoSnapshotSource {
                def findByTrackingNumber(_n, _k) =
                    WiringGuard.noDbViolation("CargoSnapshotSource")
            }
        } with handler HandlingRepo {
            def save(_a, _k) = WiringGuard.noDbViolation("HandlingRepo")
            def findAll(_k)  = WiringGuard.noDbViolation("HandlingRepo")
        }

    /// 効果を「使った」ことにする（`lift` のため。常に `false` で呼ばれる）
    pub def touch(shouldRun: Bool): Unit \ Eff =
        if (shouldRun) {
            discard HandlingRepo.findAll();
            discard CargoSnapshotSource.findByTrackingNumber("");
            discard TrackingTransportStatusUpdate.apply("",
                SharedTransportStatus.TransportStatus.Received);
            discard BookingMisroutingReport.report("")
        } else ()
}
