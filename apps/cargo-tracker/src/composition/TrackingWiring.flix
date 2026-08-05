///
/// Tracking Context の配線（IT8・TS14。IT10 で書き込み側を追加）。
///
/// 分割の意図と `src/composition/` に置く理由は [ShipperWiring](ShipperWiring.flix) を参照。
///
/// IT9 まで本 BC は**読み取り専用**（公開追跡）だった。US14（追跡番号の発行）で
/// 書き込み側が要り、`TrackingRepo` と ACL（`BookingTrackingNumberAssignment`）が増えた。
/// **合成ルートは 1 行も変わっていない**——それが分割の目的である。
///
/// **追跡番号の記録の翻訳は `acl/` にある**（規約 11・ADR-0012）。
///
mod TrackingWiring {
    use TrackingBookingTrackingNumberAssignment.BookingTrackingNumberAssignment
    use TrackingJdbcReadDb.withJdbcReadDb
    use TrackingJdbcTrackingRepo.withJdbcTrackingRepo
    use TrackingReadDb.ReadDb
    use TrackingRepo.TrackingRepo

    /// 本 BC が要求する効果（読み取り + 書き込み + ACL）
    pub type alias Eff = ReadDb + TrackingRepo + BookingTrackingNumberAssignment

    /// 本番のアダプタを適用する
    pub def withDb(f: Unit -> a \ ef + Eff):
            a \ (ef - Eff) + BookingTrackingNumberAssignmentAdapter.Requires
                          + SharedDbTx.Tx + IO =
        withJdbcReadDb(() ->
            withJdbcTrackingRepo(() ->
                BookingTrackingNumberAssignmentAdapter.withBookingTrackingNumberAssignment(f)))

    /// DB を使わない経路（`TxMode.NoTx`）で適用する
    pub def withoutDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) =
        run {
            run {
                run {
                    f()
                } with handler BookingTrackingNumberAssignment {
                    def assign(_b, _t, _k) =
                        WiringGuard.noDbViolation("BookingTrackingNumberAssignment")
                }
            } with handler TrackingRepo {
                def save(_a, _k)            = WiringGuard.noDbViolation("TrackingRepo")
                def findByBookingId(_i, _k) = WiringGuard.noDbViolation("TrackingRepo")
                def nextSequenceFor(_d, _k) = WiringGuard.noDbViolation("TrackingRepo")
            }
        } with handler ReadDb {
            def findByTrackingNumber(_n, _k) = WiringGuard.noDbViolation("ReadDb")
        }

    /// 効果を「使った」ことにする（`lift` のため。常に `false` で呼ばれる）
    pub def touch(shouldRun: Bool): Unit \ Eff =
        if (shouldRun) {
            discard ReadDb.findByTrackingNumber("");
            discard TrackingRepo.findByBookingId("");
            discard BookingTrackingNumberAssignment.assign("", "")
        } else ()
}
