// 適法: アダプタ配下で単一のハンドラを定義するラップ関数
// パス: src/tracking/infrastructure/repositories/Ok.flix 相当
// 規約 5 は「合成（2 段以上の入れ子）」を検出する。単一の定義は検出しない
mod TrackingOkHandlerWrapper {
    pub def withJdbcReadDb(f: Unit -> a \ ef + ReadDb): a \ (ef - ReadDb) + Tx + IO =
        run f() with handler ReadDb {
            def findByTrackingNumber(n, k) = k(fetchDetail(n))
        }
}
