// 規約 1 違反: domain 層が infrastructure 層を参照する
// パス: src/tracking/domain/model/Bad.flix 相当
mod TrackingBadDomain {
    use TrackingJdbcReadDb.withJdbcReadDb

    pub def bad(): Unit = ()
}
