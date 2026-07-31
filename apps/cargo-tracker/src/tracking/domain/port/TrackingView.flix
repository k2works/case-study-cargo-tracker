///
/// Tracking Context の Read Model（CQRS のクエリ側の戻り値型）。
///
/// 集約ではなく、画面表示に最適化したフラットな構造を持つ。
/// 画面関数はこの型のみを受け取り、集約を直接扱わない
/// （docs/design/architecture_frontend.md の画面関数の設計原則）。
///
mod TrackingView {

    /// 追跡イベント 1 件（画面表示用）
    pub enum TrackingEventView({
        eventTime = String,
        eventType = String,
        locationName = String
    })

    /// 追跡詳細（画面表示用）
    pub enum TrackingDetailView({
        trackingNumber = String,
        transportStatus = String,
        currentLocation = String,
        events = List[TrackingEventView]
    })

    /// 追跡番号
    pub def trackingNumber(v: TrackingDetailView): String =
        let TrackingDetailView.TrackingDetailView(d) = v;
        d#trackingNumber

    /// 輸送状態（TransportStatus の永続化値）
    pub def transportStatus(v: TrackingDetailView): String =
        let TrackingDetailView.TrackingDetailView(d) = v;
        d#transportStatus

    /// 現在地（最新イベントの場所名）
    pub def currentLocation(v: TrackingDetailView): String =
        let TrackingDetailView.TrackingDetailView(d) = v;
        d#currentLocation

    /// イベント履歴（新しい順）
    pub def events(v: TrackingDetailView): List[TrackingEventView] =
        let TrackingDetailView.TrackingDetailView(d) = v;
        d#events

    /// イベントの発生日時
    pub def eventTime(e: TrackingEventView): String =
        let TrackingEventView.TrackingEventView(x) = e;
        x#eventTime

    /// イベントの種別
    pub def eventType(e: TrackingEventView): String =
        let TrackingEventView.TrackingEventView(x) = e;
        x#eventType

    /// イベントの発生場所名
    pub def locationName(e: TrackingEventView): String =
        let TrackingEventView.TrackingEventView(x) = e;
        x#locationName

    /// 詳細ビューを組み立てる
    pub def detail(num: String, st: String, loc: String, evs: List[TrackingEventView]): TrackingDetailView =
        TrackingDetailView.TrackingDetailView({
            trackingNumber = num, transportStatus = st,
            currentLocation = loc, events = evs
        })

    /// イベントビューを組み立てる
    pub def event(time: String, kind: String, loc: String): TrackingEventView =
        TrackingEventView.TrackingEventView({
            eventTime = time, eventType = kind, locationName = loc
        })
}
