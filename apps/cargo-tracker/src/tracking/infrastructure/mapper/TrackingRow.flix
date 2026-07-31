///
/// `ResultSet` から Read Model へのデコード。
///
/// ORM を使わないため、行 ⇄ 型の変換を明示的な関数として書く
/// （docs/design/data-model.md の設計上の判断 8）。
///
mod TrackingRow {
    import java.sql.ResultSet

    use TrackingView.{TrackingDetailView, TrackingEventView, detail, event}

    /// イベント行をデコードする（1 行分）
    pub def decodeEvent(rs: ResultSet): TrackingEventView \ IO =
        let time = rs.getString("event_time");
        let kind = rs.getString("event_type");
        let loc = rs.getString("location_name");
        event(nullToEmpty(time), nullToEmpty(kind), nullToEmpty(loc))

    /// `NULL` を空文字へ変換する（画面表示用のため Option を潰す）
    def nullToEmpty(s: String): String =
        if (Object.isNull(s)) "" else s

    /// 追跡詳細を組み立てる
    pub def toDetail(trackingNumber: String, transportStatus: String,
                     events: List[TrackingEventView]): TrackingDetailView =
        let currentLocation = match List.head(events) {
            case Some(e) => TrackingView.locationName(e)
            case None    => "-"
        };
        detail(trackingNumber, transportStatus, currentLocation, events)
}
