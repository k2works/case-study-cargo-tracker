///
/// Routing Context の配線（IT8・TS14）。
///
/// 分割の意図と `src/composition/` に置く理由は [ShipperWiring](ShipperWiring.flix) を参照。
///
/// 本 BC は**リポジトリと ACL の 2 本**を持つ。ACL（`BookingRouteRequest`）の向きは
/// [ADR-0009](../../../../docs/adr/ADR-0009-routing-pulls-booking-via-acl.md) で確定した
/// ——経路割り当ての画面が Routing 側にあるため、**Routing が Booking から引く**。
///
mod RoutingWiring {
    use RoutingBookingRouteRequest.BookingRouteRequest
    use RoutingJdbcBookingRouteRequest.withJdbcBookingRouteRequest
    use RoutingJdbcVoyageRepo.withJdbcVoyageRepo
    use VoyageRepo.VoyageRepo

    /// 本 BC が要求する効果（リポジトリ + ACL）
    pub type alias Eff = VoyageRepo + BookingRouteRequest

    /// 本番のアダプタを適用する
    pub def withDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) + SharedDbTx.Tx + IO =
        withJdbcVoyageRepo(() ->
            withJdbcBookingRouteRequest(f))

    /// DB を使わない経路（`TxMode.NoTx`）で適用する
    pub def withoutDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) =
        run {
            run {
                f()
            } with handler BookingRouteRequest {
                def findRouteRequest(_i, _k) = WiringGuard.noDbViolation("BookingRouteRequest")
            }
        } with handler VoyageRepo {
            def save(_v, _k)                   = WiringGuard.noDbViolation("VoyageRepo")
            def findByVoyageNumber(_n, _k)     = WiringGuard.noDbViolation("VoyageRepo")
            def findForUpdate(_n, _k)          = WiringGuard.noDbViolation("VoyageRepo")
            def findAll(_k)                    = WiringGuard.noDbViolation("VoyageRepo")
            def replace(_v, _k)                = WiringGuard.noDbViolation("VoyageRepo")
            def search(_o, _d, _f, _t, _c, _k) = WiringGuard.noDbViolation("VoyageRepo")
        }

    /// 効果を「使った」ことにする（`lift` のため。常に `false` で呼ばれる）
    pub def touch(shouldRun: Bool): Unit \ Eff =
        if (shouldRun) {
            discard VoyageRepo.findAll();
            discard BookingRouteRequest.findRouteRequest("")
        } else ()
}
