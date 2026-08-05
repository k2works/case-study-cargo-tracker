///
/// `TrackingRepo` の JDBC 実装（IT10・US14）。
///
mod TrackingJdbcTrackingRepo {
    import java.sql.ResultSet
    import java.sql.Types

    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx
    use TrackingModel.TrackingActivity
    use TrackingRepo.TrackingRepo

    def insertSql(): String =
        "INSERT INTO tracking_activity (tracking_number, booking_id, transport_status) " +
        "VALUES (?, ?, ?)"

    def selectByBookingIdSql(): String =
        "SELECT tracking_number, booking_id, transport_status, " +
        "       status_before_exception " +
        "FROM tracking_activity WHERE booking_id = ?"

    def selectByTrackingNumberSql(): String =
        "SELECT tracking_number, booking_id, transport_status, " +
        "       status_before_exception " +
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

    ///
    /// 直近の出来事の時刻（IT11 レビュー）。
    ///
    /// **`MAX` で取る**。並び順の前提（一覧が新しい順か古い順か）に依存しない——
    /// 依存すると、表示側の並べ替えを変えた瞬間に判定が静かに壊れる。
    ///
    def latestEventTimeSql(): String =
        "SELECT MAX(e.event_time) AS latest FROM tracking_handling_event e " +
        "JOIN tracking_activity a ON a.id = e.tracking_id " +
        "WHERE a.tracking_number = ?"

    ///
    /// 例外を記録する（US19・IT12）。
    ///
    /// `tracking_id` は追跡番号から副問い合わせで引く（出来事と同じ形）。
    ///
    def insertExceptionSql(): String =
        "INSERT INTO tracking_exception_event " +
        "(tracking_id, exception_type, occurred_at, location_unlocode, " +
        " description, escalated) " +
        "SELECT id, ?, CAST(? AS TIMESTAMP), ?, ?, ? FROM tracking_activity " +
        "WHERE tracking_number = ?"

    ///
    /// 未解決の例外を解決する。
    ///
    /// **未解決の行だけを更新する**（`resolved_at IS NULL`）。
    /// 解決済みの行を上書きすると、過去の対応記録が消える。
    ///
    def resolveExceptionSql(): String =
        "UPDATE tracking_exception_event SET resolved_at = CAST(? AS TIMESTAMP), " +
        "resolution_notes = ?, revised_arrival = CAST(? AS TIMESTAMP), " +
        "updated_at = CURRENT_TIMESTAMP " +
        "WHERE resolved_at IS NULL AND tracking_id IN " +
        "(SELECT id FROM tracking_activity WHERE tracking_number = ?)"

    ///
    /// 対応内容だけを更新する（受入基準 4）。
    ///
    /// **`resolved_at` を書かない**。到着予定の改定は解決ではない——
    /// 書いてしまうと未解決の一覧から消え、追跡管理者が対応を追えなくなる。
    ///
    def updateResolutionNotesSql(): String =
        "UPDATE tracking_exception_event SET resolution_notes = ?, " +
        "revised_arrival = CAST(? AS TIMESTAMP), updated_at = CURRENT_TIMESTAMP " +
        "WHERE resolved_at IS NULL AND tracking_id IN " +
        "(SELECT id FROM tracking_activity WHERE tracking_number = ?)"

    /// 改定後の到着予定を追跡活動へ反映する（荷主が見るのはこちら）
    def updateArrivalSql(): String =
        "UPDATE tracking_activity SET estimated_arrival = CAST(? AS TIMESTAMP), " +
        "updated_at = CURRENT_TIMESTAMP WHERE tracking_number = ?"

    /// **輸送状態だけを書く**。行全体を書き戻すと他の列を古い値で上書きする
    def updateStatusSql(): String =
        "UPDATE tracking_activity SET transport_status = ?, " +
        "status_before_exception = ?, " +
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
                        rs.getString("transport_status"),
                        nullableString(rs, "status_before_exception"))
                else None;
            rs.close();
            found
        })

    /// `NULL` を `None` として読む（既定値へ倒さない）
    def nullableString(rs: ResultSet, column: String): Option[String] \ IO =
        let value = rs.getString(column);
        if (Object.isNull(value)) None else Some(value)

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
                        rs.getString("transport_status"),
                        nullableString(rs, "status_before_exception"))
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
            // **復帰先も同じ更新で書く**（US19・IT12）。状態と退避が別々に
            // 書かれると、片方だけ書かれた行は「例外中なのに戻れない」になる
            match TrackingModel.statusBeforeExceptionOf(activity) {
                case None    => stmt.setNull(2, Types.VARCHAR)
                case Some(s) => stmt.setString(2, SharedTransportStatus.toPersisted(s))
            };
            stmt.setString(3, TrackingModel.trackingNumberOf(activity));
            discard stmt.executeUpdate()
        });
        TrackingModel.pendingEventsOf(activity)
            |> List.forEach(e -> insertEvent(TrackingModel.trackingNumberOf(activity), e));
        match TrackingModel.pendingExceptionOf(activity) {
            case None    => ()
            case Some(r) => saveException(TrackingModel.trackingNumberOf(activity), r)
        }

    ///
    /// 例外を書く（発生／解決）。
    ///
    /// **解決の記録は既存の行を更新する**。新しい行にすると、
    /// 「発生 1 行・解決 1 行」で未解決の判定が結合になる。
    ///
    def saveException(trackingNumber: String,
                      r: TrackingModel.ExceptionRecord): Unit \ Tx + IO =
        let conn = Tx.connection();
        match TrackingModel.exceptionResolvedAtOf(r) {
            case None =>
                withPreparedStatement(conn, insertExceptionSql(), stmt -> {
                    stmt.setString(1, TrackingModel.exceptionTypeToPersisted(
                        TrackingModel.exceptionTypeOfRecord(r)));
                    stmt.setString(2, SharedCalendarText.toSqlTimestamp(
                        TrackingModel.exceptionOccurredAtOf(r)));
                    stmt.setString(3, TrackingModel.exceptionLocationOf(r));
                    stmt.setString(4, TrackingModel.exceptionDescriptionOf(r));
                    stmt.setBoolean(5, TrackingModel.exceptionEscalatedOf(r));
                    stmt.setString(6, trackingNumber);
                    discard stmt.executeUpdate()
                })
            // **空の解決日時は「対応内容だけの更新」である**（受入基準 4）。
            // 解決日時を書くと未解決の一覧から消え、対応を追えなくなる
            case Some(resolvedAt) if resolvedAt == "" => {
                withPreparedStatement(conn, updateResolutionNotesSql(), stmt -> {
                    match TrackingModel.exceptionResolutionNotesOf(r) {
                        case None    => stmt.setNull(1, Types.VARCHAR)
                        case Some(n) => stmt.setString(1, n)
                    };
                    match TrackingModel.exceptionRevisedArrivalOf(r) {
                        case None    => stmt.setNull(2, Types.TIMESTAMP)
                        case Some(d) => stmt.setString(2, d + " 00:00:00")
                    };
                    stmt.setString(3, trackingNumber);
                    discard stmt.executeUpdate()
                });
                updateArrivalIfRevised(trackingNumber, r)
            }
            case Some(resolvedAt) => {
                withPreparedStatement(conn, resolveExceptionSql(), stmt -> {
                    stmt.setString(1, SharedCalendarText.toSqlTimestamp(resolvedAt));
                    match TrackingModel.exceptionResolutionNotesOf(r) {
                        case None    => stmt.setNull(2, Types.VARCHAR)
                        case Some(n) => stmt.setString(2, n)
                    };
                    match TrackingModel.exceptionRevisedArrivalOf(r) {
                        case None    => stmt.setNull(3, Types.TIMESTAMP)
                        case Some(d) => stmt.setString(3, d + " 00:00:00")
                    };
                    stmt.setString(4, trackingNumber);
                    discard stmt.executeUpdate()
                });
                updateArrivalIfRevised(trackingNumber, r)
            }
        }

    ///
    /// 改定後の到着予定を追跡活動へ反映する。
    ///
    /// **荷主が見るのは追跡活動の到着予定**である。例外の行に書くだけでは、
    /// 荷主の画面は古い予定を出し続ける。
    ///
    def updateArrivalIfRevised(trackingNumber: String,
                               r: TrackingModel.ExceptionRecord): Unit \ Tx + IO =
        match TrackingModel.exceptionRevisedArrivalOf(r) {
            case None    => ()
            case Some(d) =>
                let conn = Tx.connection();
                withPreparedStatement(conn, updateArrivalSql(), stmt -> {
                    stmt.setString(1, d + " 00:00:00");
                    stmt.setString(2, trackingNumber);
                    discard stmt.executeUpdate()
                })
        }

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
            // **共有の整形を通す**（IT11 レビュー）。L1 で共有カーネルへ寄せた直後に、
            // 新しい経路が素の値を渡しており、整形の責務が再び 2 系統になっていた
            stmt.setString(2, SharedCalendarText.toSqlTimestamp(
                TrackingModel.eventTimeOf(e)));
            stmt.setString(3, TrackingModel.eventLocationOf(e));
            match TrackingModel.eventVoyageNumberOf(e) {
                case None    => stmt.setNull(4, Types.VARCHAR)
                case Some(v) => stmt.setString(4, v)
            };
            stmt.setString(5, trackingNumber);
            discard stmt.executeUpdate()
        })

    def selectLatestEventTime(trackingNumber: String): Option[String] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, latestEventTimeSql(), stmt -> {
            stmt.setString(1, trackingNumber);
            let rs = stmt.executeQuery();
            let found =
                if (rs.next())
                    let value = rs.getString("latest");
                    if (Object.isNull(value)) None else Some(value)
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
            def findByTrackingNumber(n, k) = k(selectByTrackingNumber(n))
            def saveHandling(a, k)         = { saveHandling(a); k() }
            def findForUpdate(n, k)        = k(selectForUpdate(n))
            def latestEventTimeOf(n, k)    = k(selectLatestEventTime(n))
            def nextSequenceFor(isoDate, k) = k(nextSequenceFor(isoDate))
        }
}
