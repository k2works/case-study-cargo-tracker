///
/// `ReadDb` 効果の JDBC 実装（本番アダプタ）。
///
/// ハンドラの各操作は継続 `k` を最後の引数に受け取り、`k(戻り値)` で呼び出し元へ返す。
/// コネクションは自分で取得せず `Tx.connection()` から受け取る（同一トランザクション保証）。
///
/// SQL は本モジュール内の定数として持つ。文字列連結による組み立ては行わない
/// （arch-lint 規約 6。SQL インジェクション対策）。
///
mod TrackingJdbcReadDb {
    import java.sql.ResultSet

    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx
    use TrackingReadDb.ReadDb
    use TrackingView.{TrackingDetailView, TrackingEventView}

    /// 追跡レコードの取得
    def selectActivitySql(): String =
        "SELECT tracking_number, transport_status, estimated_arrival " +
        "FROM tracking_activity WHERE tracking_number = ?"

    /// 追跡イベントの取得（新しい順）。場所名を location から JOIN で引く
    def selectEventsSql(): String =
        "SELECT e.event_time, e.event_type, l.name AS location_name, " +
        "e.location_unlocode AS location_unlocode " +
        "FROM tracking_handling_event e " +
        "JOIN tracking_activity a ON a.id = e.tracking_id " +
        "LEFT JOIN location l ON l.unlocode = e.location_unlocode " +
        "WHERE a.tracking_number = ? " +
        "ORDER BY e.event_time DESC"

    /// イベント一覧を取得する
    def fetchEvents(trackingNumber: String): List[TrackingEventView] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectEventsSql(), stmt -> {
            stmt.setString(1, trackingNumber);
            let rs = stmt.executeQuery();
            let events = collectEvents(rs, Nil);
            rs.close();
            // SQL は新しい順（ORDER BY event_time DESC）。先頭に積んでいるため反転して戻す
            List.reverse(events)
        })

    /// ResultSet を再帰的に読み取る
    def collectEvents(rs: ResultSet, acc: List[TrackingEventView]): List[TrackingEventView] \ IO =
        if (rs.next())
            collectEvents(rs, TrackingRow.decodeEvent(rs) :: acc)
        else
            acc

    /// 追跡詳細を取得する
    def fetchDetail(trackingNumber: String): Option[TrackingDetailView] \ Tx + IO =
        let conn = Tx.connection();
        let found = withPreparedStatement(conn, selectActivitySql(), stmt -> {
            stmt.setString(1, trackingNumber);
            let rs = stmt.executeQuery();
            let row = if (rs.next()) {
                let num = rs.getString("tracking_number");
                let status = rs.getString("transport_status");
                let eta = rs.getString("estimated_arrival");
                Some((num, status, eta))
            } else None;
            rs.close();
            row
        });
        match found {
            case None => None
            case Some((num, status, eta)) =>
                let events = fetchEvents(trackingNumber);
                Some(TrackingRow.toDetail(num, status, eta, events))
        }

    ///
    /// `ReadDb` 効果に JDBC 実装を注入する。
    ///
    /// 合成ルート（infrastructure/runtime）とテストからのみ適用する（arch-lint 規約 5）。
    ///
    pub def withJdbcReadDb(f: Unit -> a \ ef + ReadDb): a \ (ef - ReadDb) + Tx + IO =
        run {
            f()
        } with handler ReadDb {
            def findByTrackingNumber(trackingNumber, k) = k(fetchDetail(trackingNumber))
        }
}
