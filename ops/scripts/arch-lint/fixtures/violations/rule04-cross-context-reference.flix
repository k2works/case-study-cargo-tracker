// 規約 4 違反: tracking コンテキストが booking コンテキストを直接参照する
// パス: src/tracking/application/queryservices/Bad.flix 相当
mod TrackingBadCrossContext {
    use BookingCargoRepo.findByTrackingId

    pub def bad(): Unit = ()
}
