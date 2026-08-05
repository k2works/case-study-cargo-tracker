///
/// `TrackingRepo` の JDBC 実装（IT10・US14）。
///
mod TrackingJdbcTrackingRepo {
    import java.sql.Types

    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx
    use TrackingModel.TrackingActivity
    use TrackingRepo.TrackingRepo

    def insertSql(): String =
        "INSERT INTO tracking_activity (tracking_number, booking_id, transport_status) " +
        "VALUES (?, ?, ?)"

    def selectByBookingIdSql(): String =
        "SELECT tracking_number, booking_id, transport_status " +
        "FROM tracking_activity WHERE booking_id = ?"

    def selectByTrackingNumberSql(): String =
        "SELECT tracking_number, booking_id, transport_status " +
        "FROM tracking_activity WHERE tracking_number = ?"

    /// 行を押さえる。`JdbcVoyageRepo.lockVoyageSql` と同じ形（主キー行だけに掛ける）
    def lockTrackingSql(): String =
        "SELECT id FROM tracking_activity WHERE tracking_number = ? FOR UPDATE"

    ///
    /// 出来事を 1 件足す（IT11・H8）。
    ///
    /// `tracking_id` を**副問い合わせで解決する**。追跡番号は業務キーであり
    /// 呼び出し側が持っているが、サロゲートキーは DB の都合である。
    ///
    def insertEventSql(): String =
        "INSERT INTO tracking_handling_event " +
        "(tracking_id, event_type, event_time, location_unlocode, voyage_number) " +
        "SELECT id, ?, CAST(? AS TIMESTAMP), ?, ? FROM tracking_activity " +
        "WHERE tracking_number = ?"

    /// **輸送状態だけを書く**。行全体を書き戻すと他の列を古い値で上書きする
    def updateStatusSql(): String =
        "UPDATE tracking_activity SET transport_status = ?, " +
        "updated_at = CURRENT_TIMESTAMP WHERE tracking_number = ?"

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

    def selectByTrackingNumber(trackingNumber: String): Option[TrackingActivity] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectByTrackingNumberSql(), stmt -> {
            stmt.setString(1, trackingNumber);
            let rs = stmt.executeQuery();
            let found =
                if (rs.next())
                    TrackingModel.reconstruct(
                        rs.getString("tracking_number"),
                        rs.getString("booking_id"),
                        rs.getString("transport_status"))
                else None;
            rs.close();
            found
        })

    def selectForUpdate(trackingNumber: String): Option[TrackingActivity] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, lockTrackingSql(), stmt -> {
            stmt.setString(1, trackingNumber);
            let rs = stmt.executeQuery();
            discard rs.next();
            rs.close()
        });
        selectByTrackingNumber(trackingNumber)

    /// **集約が持つ輸送状態と出来事を書く**。列の値を外から受け取らない
    def saveHandling(activity: TrackingActivity): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, updateStatusSql(), stmt -> {
            stmt.setString(1, SharedTransportStatus.toPersisted(
                TrackingModel.transportStatusOf(activity)));
            stmt.setString(2, TrackingModel.trackingNumberOf(activity));
            discard stmt.executeUpdate()
        });
        TrackingModel.pendingEventsOf(activity)
            |> List.forEach(e -> insertEvent(TrackingModel.trackingNumberOf(activity), e))

    ///
    /// 出来事を 1 件書く（IT11・H8）。
    ///
    /// `tracking_id` は追跡番号から**副問い合わせで引く**。呼び出し側に
    /// サロゲートキーを持たせると、集約が DB の都合を知ることになる。
    ///
    def insertEvent(trackingNumber: String, e: TrackingModel.HandlingEvent): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, insertEventSql(), stmt -> {
            stmt.setString(1, TrackingModel.eventTypeOf(e));
            stmt.setString(2, TrackingModel.eventTimeOf(e));
            stmt.setString(3, TrackingModel.eventLocationOf(e));
            match TrackingModel.eventVoyageNumberOf(e) {
                case None    => stmt.setNull(4, Types.VARCHAR)
                case Some(v) => stmt.setString(4, v)
            };
            stmt.setString(5, trackingNumber);
            discard stmt.executeUpdate()
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
            def findByTrackingNumber(n, k) = k(selectByTrackingNumber(n))
            def saveHandling(a, k)         = { saveHandling(a); k() }
            def findForUpdate(n, k)        = k(selectForUpdate(n))
            def nextSequenceFor(isoDate, k) = k(nextSequenceFor(isoDate))
        }
}
