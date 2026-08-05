///
/// Booking Context の配線（IT8・TS14）。
///
/// 分割の意図と `src/composition/` に置く理由は [ShipperWiring](ShipperWiring.flix) を参照。
///
/// 本 BC は**リポジトリと ACL の 2 本**を持つ。ACL（`ShipperExistenceChecker`）が
/// Booking 側にあるのは、**必要とする側が必要な形に翻訳して引く**という
/// 本プロジェクトの ACL の一般形による（[ADR-0009](../../../../docs/adr/ADR-0009-routing-pulls-booking-via-acl.md)）。
///
mod BookingWiring {
    use BookingJdbcCargoRepo.withJdbcCargoRepo
    use BookingJdbcShipperExistence.withJdbcShipperExistence
    use CargoRepo.CargoRepo
    use ShipperExistenceChecker.ShipperExistenceChecker

    /// 本 BC が要求する効果（リポジトリ + ACL）
    pub type alias Eff = CargoRepo + ShipperExistenceChecker

    /// 本番のアダプタを適用する
    pub def withDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) + SharedDbTx.Tx + IO =
        withJdbcCargoRepo(() ->
            withJdbcShipperExistence(f))

    /// DB を使わない経路（`TxMode.NoTx`）で適用する
    pub def withoutDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) =
        run {
            run {
                f()
            } with handler ShipperExistenceChecker {
                def resolveShipperId(_c, _k)     = WiringGuard.noDbViolation("ShipperExistenceChecker")
                def describeShipper(_i, _k)      = WiringGuard.noDbViolation("ShipperExistenceChecker")
                def findShipperIdsByName(_n, _k) = WiringGuard.noDbViolation("ShipperExistenceChecker")
            }
        } with handler CargoRepo {
            def save(_c, _k)            = WiringGuard.noDbViolation("CargoRepo")
            def findByBookingId(_i, _k) = WiringGuard.noDbViolation("CargoRepo")
            def findAll(_k)             = WiringGuard.noDbViolation("CargoRepo")
            def updateStatus(_c, _k)    = WiringGuard.noDbViolation("CargoRepo")
            def search(_s, _q, _k)          = WiringGuard.noDbViolation("CargoRepo")
            def saveItinerary(_c, _k)       = WiringGuard.noDbViolation("CargoRepo")
            def findWithItinerary(_i, _k)   = WiringGuard.noDbViolation("CargoRepo")
            def findForUpdate(_i, _k)       = WiringGuard.noDbViolation("CargoRepo")
            def saveTrackingNumber(_c, _k)  = WiringGuard.noDbViolation("CargoRepo")
            def findByTrackingNumber(_n, _k) = WiringGuard.noDbViolation("CargoRepo")
        }

    /// 効果を「使った」ことにする（`lift` のため。常に `false` で呼ばれる）
    pub def touch(shouldRun: Bool): Unit \ Eff =
        if (shouldRun) {
            discard CargoRepo.findAll();
            discard ShipperExistenceChecker.resolveShipperId("")
        } else ()
}
