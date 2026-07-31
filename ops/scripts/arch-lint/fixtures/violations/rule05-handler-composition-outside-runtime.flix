// 規約 5 違反: 合成ルート以外でハンドラを合成している
// パス: src/tracking/interfaces/web/Bad.flix 相当
mod TrackingBadComposition {
    pub def bad(ds: DataSource): Unit \ IO =
        readOnly(ds, () -> withJdbcReadDb(() -> ()))
}
