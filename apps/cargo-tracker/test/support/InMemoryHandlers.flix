///
/// テスト用のインメモリ効果ハンドラ。
///
/// モックライブラリを使わず、効果ハンドラをテストダブルとして用いる（ADR-0002）。
/// 本番の JDBC ハンドラと同じ効果を実装するため、アプリケーション層のコードは一切変わらない。
///
mod InMemoryHandlers {
    use SharedClock.Clock
    use TrackingReadDb.ReadDb
    use TrackingView.TrackingDetailView

    ///
    /// 時刻を固定する。
    ///
    /// これがあって初めて「30 分後にセッションが切れる」を**待たずに**検証できる。
    ///
    pub def withFixedClock(fixedMillis: Int64, f: Unit -> a \ ef + Clock): a \ ef - Clock =
        run {
            f()
        } with handler Clock {
            def nowMillis(k) = k(fixedMillis)
        }

    ///
    /// 呼ばれるたびに進む時計。
    ///
    /// 「アクセスするたびに失効時刻が延びる」（最終アクセスからのタイムアウト）を
    /// 検証するには、**同一テストの中で時刻が変わる**必要がある。
    ///
    pub def withAdvancingClock(rc: Region[r], startMillis: Int64, stepMillis: Int64,
                               f: Unit -> a \ ef + Clock): a \ (ef - Clock) + r =
        let current = Ref.fresh(rc, startMillis);
        run {
            f()
        } with handler Clock {
            def nowMillis(k) = {
                let value = Ref.get(current);
                Ref.put(value + stepMillis, current);
                k(value)
            }
        }

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
