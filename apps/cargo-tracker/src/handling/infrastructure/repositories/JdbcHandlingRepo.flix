///
/// `HandlingRepo` の JDBC 実装（IT10・US15）。
///
mod HandlingJdbcHandlingRepo {
    import java.sql.ResultSet
    use HandlingModel.HandlingActivity
    use HandlingRepo.HandlingRepo
    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx

    def insertSql(): String =
        "INSERT INTO handling_activity " +
        "(tracking_number, event_type, event_completion_time, location_unlocode, " +
        " voyage_number, validity) " +
        "VALUES (?, ?, CAST(? AS TIMESTAMP), ?, ?, ?)"

    /// **新しい順**。荷役作業員が見るのは直近の記録である
    def selectAllSql(): String =
        "SELECT tracking_number, event_type, event_completion_time, " +
        "       location_unlocode, voyage_number, validity " +
        "FROM handling_activity ORDER BY event_completion_time DESC, id DESC"

    ///
    /// `YYYY-MM-DDTHH:MM` を SQL の TIMESTAMP 文字列へ。
    ///
    /// 秒が無い形で来るため補う。**空文字は渡さない**——
    /// `CAST('' AS TIMESTAMP)` は例外になる（集約が空を拒むためここへは来ない）。
    ///
    def timestampText(value: String): String =
        let replaced = String.replace(src = "T", dst = " ", value);
        if (String.length(replaced) == 16) replaced + ":00" else replaced

    def insert(activity: HandlingActivity): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, insertSql(), stmt -> {
            stmt.setString(1, HandlingModel.trackingNumberOf(activity));
            stmt.setString(2, HandlingModel.typeToPersisted(
                HandlingModel.handlingTypeOf(activity)));
            stmt.setString(3, timestampText(
                HandlingModel.eventCompletionTimeOf(activity)));
            stmt.setString(4, HandlingModel.locationOf(activity));
            match HandlingModel.voyageNumberOf(activity) {
                case None    => stmt.setNull(5, 12)
                case Some(v) => stmt.setString(5, v)
            };
            stmt.setString(6, HandlingModel.validityToPersisted(
                HandlingModel.validityOf(activity)));
            discard stmt.executeUpdate()
        })

    def selectAll(): List[HandlingActivity] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectAllSql(), stmt -> {
            let rs = stmt.executeQuery();
            let rows = collect(rs, Nil);
            rs.close();
            List.reverse(rows)
        })

    ///
    /// 行を積む。**読めない行は落とす**（既定値へ倒さない）。
    ///
    /// 未知の種別や妥当性を既定値に倒すと、履歴が実態と違うものになる——
    /// 「積込」として記録されたものが「受領」に見えるほうが、行が消えるより害が大きい。
    ///
    def collect(rs: ResultSet, acc: List[HandlingActivity]): List[HandlingActivity] \ IO =
        if (rs.next())
            let typeText = rs.getString("event_type");
            let validityText = rs.getString("validity");
            match (HandlingModel.typeFromPersisted(typeText),
                   HandlingModel.validityFromPersisted(validityText)) {
                case (Some(t), Some(v)) =>
                    let voyage = rs.getString("voyage_number");
                    let activity = HandlingModel.reconstruct(
                        rs.getString("tracking_number"), t,
                        rs.getString("location_unlocode"),
                        rs.getString("event_completion_time"),
                        if (Object.isNull(voyage)) None else Some(voyage),
                        v);
                    collect(rs, activity :: acc)
                case _ => collect(rs, acc)
            }
        else acc

    pub def withJdbcHandlingRepo(f: Unit -> a \ ef + HandlingRepo):
            a \ (ef - HandlingRepo) + Tx + IO =
        run {
            f()
        } with handler HandlingRepo {
            def save(activity, k) = { insert(activity); k() }
            def findAll(k)        = k(selectAll())
        }
}
