///
/// テスト用のインメモリ効果ハンドラ。
///
/// モックライブラリを使わず、効果ハンドラをテストダブルとして用いる（ADR-0002）。
/// 本番の JDBC ハンドラと同じ効果を実装するため、アプリケーション層のコードは一切変わらない。
///
mod InMemoryHandlers {
    use TrackingReadDb.ReadDb
    use TrackingView.TrackingDetailView

    ///
    /// `ReadDb` 効果にインメモリ実装を注入する。
    ///
    /// 与えられた一覧から追跡番号で検索する。DB もサーバも起動しない。
    ///
    pub def withInMemoryReadDb(data: List[TrackingDetailView], f: Unit -> a \ ef + ReadDb): a \ ef - ReadDb =
        run {
            f()
        } with handler ReadDb {
            def findByTrackingNumber(trackingNumber, k) =
                k(List.find(v -> TrackingView.trackingNumber(v) == trackingNumber, data))
        }
}
