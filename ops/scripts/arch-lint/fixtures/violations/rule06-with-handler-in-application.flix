// 規約 6 違反: application 層でハンドラを適用している
// パス: src/tracking/application/queryservices/Bad.flix 相当
mod TrackingBadHandlerInApp {
    pub def bad(f: Unit -> a \ ef + ReadDb): a \ ef - ReadDb =
        run f() with handler ReadDb {
            def findByTrackingNumber(_n, k) = k(None)
        }
}
