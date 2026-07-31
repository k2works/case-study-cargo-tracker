// 規約 3 違反: application 層が infrastructure 層を参照する
// パス: src/tracking/application/queryservices/Bad.flix 相当
mod TrackingBadApplication {
    use SharedDbPool.borrow

    pub def bad(): Unit = ()
}
