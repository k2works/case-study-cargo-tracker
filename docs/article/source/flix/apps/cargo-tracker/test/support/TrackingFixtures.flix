///
/// 追跡データのテストフィクスチャ。
///
/// **値の形式は JDBC が返すものに揃える**。IT2 で、インメモリのフィクスチャが分精度
/// （`2026-08-03 09:15`）だったのに対し `ResultSet.getString` は秒付き
/// （`2026-08-03 09:15:00`）を返していたため、日付書式のバグがテストをすり抜けた。
/// テストの入力が現実と違えば、変換のテストは何を書いても保証にならない
/// （IT2 ふりかえり Try T2）。
///
/// 「推定到着日あり」「なし」は名前付きの関数として区別する。重複除去だけを目的に
/// 共通化すると、NULL のケースが消えて経路が検証されなくなる。
///
mod TrackingFixtures {
    use SharedDbPool.DataSource
    use SharedDbPool.{withConnection, withStatement}
    use TrackingView.{TrackingDetailView, TrackingEventView, detail, event}

    // ---- 値の定義（SQL とインメモリで同じものを使う） ----

    /// 標準サンプルの追跡番号
    pub def sampleTrackingNumber(): String = "TRK-20260803-0001"

    /// 標準サンプルの推定到着日（JDBC が返す TIMESTAMP の形式）
    pub def sampleEstimatedArrival(): String = "2026-08-10 00:00:00"

    /// 積込イベントの発生日時（同上）
    pub def loadEventTime(): String = "2026-08-03 09:15:00"

    /// 受取イベントの発生日時（同上）
    pub def receiveEventTime(): String = "2026-08-02 14:00:00"

    // ---- インメモリの Read Model ----

    /// 推定到着日を持つ追跡詳細（イベント 2 件・新しい順）
    pub def detailWithArrival(): TrackingDetailView =
        detail(sampleTrackingNumber(), "ONBOARD_CARRIER", sampleEstimatedArrival(), events())

    ///
    /// 推定到着日を持たない追跡詳細。
    ///
    /// 経路が未確定の貨物を表す。DB では `estimated_arrival` が NULL であり、
    /// デコード時に空文字へ変換される。
    ///
    pub def detailWithoutArrival(): TrackingDetailView =
        detail("TRK-NOETA-0001", "RECEIVED", "", events())

    /// イベントを持たない追跡詳細（受取前の貨物）
    pub def detailWithoutEvents(): TrackingDetailView =
        detail("TRK-NOEVENT-0001", "NOT_RECEIVED", "", Nil)

    /// 標準サンプルのイベント（新しい順）
    pub def events(): List[TrackingEventView] =
        event(loadEventTime(), "LOAD", "Tokyo", "JPTYO") ::
        event(receiveEventTime(), "RECEIVE", "Tokyo", "JPTYO") :: Nil

    // ---- DB シード ----

    ///
    /// 標準サンプルを DB へ投入する（推定到着日あり・イベント 2 件）。
    ///
    /// テスト用のシードのため SQL を直接記述する。本番コードは `PreparedStatement` を使う。
    ///
    pub def seedWithArrival(ds: DataSource): Unit \ IO =
        withConnection(ds, conn -> withStatement(conn, stmt -> {
        discard stmt.executeUpdate(
            "INSERT INTO tracking_activity " +
            "(tracking_number, booking_id, transport_status, estimated_arrival) VALUES " +
            "('${sampleTrackingNumber()}', 'BK-0001', 'ONBOARD_CARRIER', " +
            "TIMESTAMP '${sampleEstimatedArrival()}')");
        discard stmt.executeUpdate(
            "INSERT INTO tracking_handling_event " +
            "(tracking_id, event_type, event_time, location_unlocode) " +
            "SELECT a.id, 'LOAD', TIMESTAMP '${loadEventTime()}', 'JPTYO' " +
            "FROM tracking_activity a WHERE a.tracking_number = '${sampleTrackingNumber()}'");
        discard stmt.executeUpdate(
            "INSERT INTO tracking_handling_event " +
            "(tracking_id, event_type, event_time, location_unlocode) " +
            "SELECT a.id, 'RECEIVE', TIMESTAMP '${receiveEventTime()}', 'JPTYO' " +
            "FROM tracking_activity a WHERE a.tracking_number = '${sampleTrackingNumber()}'")
        }))

    ///
    /// 推定到着日を持たない貨物を DB へ投入する。
    ///
    /// `estimated_arrival` を指定しないことで NULL になる。NULL → 空文字 → 画面の「未定」
    /// という経路を検証するためのフィクスチャであり、**意図的に列を省いている**。
    ///
    pub def seedWithoutArrival(ds: DataSource, trackingNumber: String): Unit \ IO =
        withConnection(ds, conn -> withStatement(conn, stmt -> {
        discard stmt.executeUpdate(
            "INSERT INTO tracking_activity (tracking_number, booking_id, transport_status) " +
            "VALUES ('${trackingNumber}', 'BK-9001', 'CLAIMED')");
        discard stmt.executeUpdate(
            "INSERT INTO tracking_handling_event " +
            "(tracking_id, event_type, event_time, location_unlocode) " +
            "SELECT a.id, 'CLAIM', TIMESTAMP '2026-08-05 10:00:00', 'USLAX' " +
            "FROM tracking_activity a WHERE a.tracking_number = '${trackingNumber}'")
        }))
}
