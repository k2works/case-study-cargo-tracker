///
/// Tracking Context の配線（IT8・TS14）。
///
/// 分割の意図と `src/composition/` に置く理由は [ShipperWiring](ShipperWiring.flix) を参照。
///
/// 本 BC は現時点で**読み取り専用**（公開追跡）である。書き込み側（追跡番号の発行・
/// 荷役イベントの反映）は US14・US15（IT10）で足す。そのとき本ファイルに
/// ポートが 1 本増えるだけで、合成ルートは 1 行も変わらない——**それが分割の目的**である。
///
mod TrackingWiring {
    use TrackingJdbcReadDb.withJdbcReadDb
    use TrackingReadDb.ReadDb

    /// 本 BC が要求する効果
    pub type alias Eff = ReadDb

    /// 本番のアダプタを適用する
    pub def withDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) + SharedDbTx.Tx + IO =
        withJdbcReadDb(f)

    /// DB を使わない経路（`TxMode.NoTx`）で適用する
    pub def withoutDb(f: Unit -> a \ ef + Eff): a \ (ef - Eff) =
        run {
            f()
        } with handler ReadDb {
            def findByTrackingNumber(_n, _k) = WiringGuard.noDbViolation("ReadDb")
        }

    /// 効果を「使った」ことにする（`lift` のため。常に `false` で呼ばれる）
    pub def touch(shouldRun: Bool): Unit \ Eff =
        if (shouldRun) discard ReadDb.findByTrackingNumber("") else ()
}
