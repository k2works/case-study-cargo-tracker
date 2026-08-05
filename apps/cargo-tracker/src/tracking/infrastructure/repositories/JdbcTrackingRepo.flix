///
/// `TrackingRepo` の JDBC 実装（IT10・US14）。
///
mod TrackingJdbcTrackingRepo {
    import java.sql.{Connection, PreparedStatement, ResultSet}
    use SharedDbTx.Tx
    use TrackingModel.TrackingActivity
    use TrackingRepo.TrackingRepo

    def insertSql(): String =
        "INSERT INTO tracking_activity (tracking_number, booking_id, transport_status) " +
        "VALUES (?, ?, ?)"

    def selectByBookingIdSql(): String =
        "SELECT tracking_number, booking_id, transport_status " +
        "FROM tracking_activity WHERE booking_id = ?"

    ///
    /// その日の最大連番。
    ///
    /// `TRK-YYYYMMDD-NNNN` の後ろ 4 桁を数値として取る。
    /// **`LIKE` の対象は自分で組み立てた番号だけ**なので、
    /// 利用者入力のエスケープ（`SharedDbSql.escapeLike`）は要らない。
    ///
    def maxSequenceSql(): String =
        "SELECT MAX(CAST(SUBSTRING(tracking_number, 14, 4) AS INT)) AS max_seq " +
        "FROM tracking_activity WHERE tracking_number LIKE ?"

    def withPreparedStatement(conn: Connection, sql: String,
                              f: PreparedStatement -> a \ ef + IO): a \ ef + IO =
        let stmt = conn.prepareStatement(sql);
        let result = f(stmt);
        stmt.close();
        result

    def insert(activity: TrackingActivity): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, insertSql(), stmt -> {
            stmt.setString(1, TrackingModel.trackingNumberOf(activity));
            stmt.setString(2, TrackingModel.bookingIdOf(activity));
            stmt.setString(3, SharedTransportStatus.toPersisted(
                TrackingModel.transportStatusOf(activity)));
            discard stmt.executeUpdate()
        })

    def selectByBookingId(bookingId: String): Option[TrackingActivity] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectByBookingIdSql(), stmt -> {
            stmt.setString(1, bookingId);
            let rs = stmt.executeQuery();
            let found =
                if (rs.next())
                    // 未知の輸送状態は `None`。**既定値へ倒さない**——
                    // 輸送中の貨物が未受領に見える
                    TrackingModel.reconstruct(
                        rs.getString("tracking_number"),
                        rs.getString("booking_id"),
                        rs.getString("transport_status"))
                else None;
            rs.close();
            found
        })

    ///
    /// その日の次の連番。
    ///
    /// **1 始まり**。`MAX` が NULL（その日まだ 1 件も無い）なら `getInt` が
    /// 0 を返すため、+1 して 1 になる。
    ///
    /// **一意性はここでは保証しない**（`TrackingRepo` のドキュメントを参照）。
    /// 同時に読めば同じ値が返る。止めるのは DB の UNIQUE 制約である。
    ///
    def nextSequenceFor(isoDate: String): Int32 \ Tx + IO =
        let compact = String.replace(src = "-", dst = "", isoDate);
        let conn = Tx.connection();
        withPreparedStatement(conn, maxSequenceSql(), stmt -> {
            stmt.setString(1, "TRK-" + compact + "-%");
            let rs = stmt.executeQuery();
            let maxSeq = if (rs.next()) rs.getInt("max_seq") else 0;
            rs.close();
            maxSeq + 1
        })

    pub def withJdbcTrackingRepo(f: Unit -> a \ ef + TrackingRepo):
            a \ (ef - TrackingRepo) + Tx + IO =
        run {
            f()
        } with handler TrackingRepo {
            def save(activity, k)          = { insert(activity); k() }
            def findByBookingId(id, k)     = k(selectByBookingId(id))
            def nextSequenceFor(isoDate, k) = k(nextSequenceFor(isoDate))
        }
}
