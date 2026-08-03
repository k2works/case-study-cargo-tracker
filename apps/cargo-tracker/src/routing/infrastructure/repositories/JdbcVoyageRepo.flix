///
/// `VoyageRepo` 効果の JDBC 実装（本番アダプタ）。
///
/// **航海と区間は 2 テーブルに分かれる**が、集約としては 1 つである。
/// 読み出しは 1 度のクエリで両方を取り、アプリ側で組み立てる
/// （区間ごとにクエリを投げると N+1 になる）。
///
mod RoutingJdbcVoyageRepo {
    import java.sql.PreparedStatement
    import java.sql.ResultSet

    use RoutingModel.{Voyage, CarrierMovement, CargoCapability}
    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx
    use VoyageRepo.VoyageRepo

    // ---- SQL ----

    def insertVoyageSql(): String =
        "INSERT INTO voyage (voyage_number, vessel_name, carrier_name, capabilities) " +
        "VALUES (?, ?, ?, ?)"

    def insertMovementSql(): String =
        "INSERT INTO carrier_movement " +
        "(voyage_id, departure_location_unlocode, arrival_location_unlocode, " +
        "departure_date, arrival_date, seq_number) " +
        "VALUES (?, ?, ?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP), ?)"

    def updateVoyageSql(): String =
        "UPDATE voyage SET vessel_name = ?, carrier_name = ?, capabilities = ?, " +
        "updated_at = CURRENT_TIMESTAMP WHERE voyage_number = ?"

    def deleteMovementsSql(): String =
        "DELETE FROM carrier_movement WHERE voyage_id = " +
        "(SELECT id FROM voyage WHERE voyage_number = ?)"

    def selectVoyageIdSql(): String = "SELECT id FROM voyage WHERE voyage_number = ?"

    ///
    /// 更新のために航海の行を押さえる。
    ///
    /// `FOR UPDATE` は行ロックを取り、**同じ行を更新しようとする 2 本目を
    /// 1 本目の確定まで待たせる**。待ったあとに読むのは確定後の内容であり、
    /// 版の照合が正しく食い違いを検出する（IT6 レビュー H4）。
    ///
    def lockVoyageSql(): String =
        "SELECT id FROM voyage WHERE voyage_number = ? FOR UPDATE"

    ///
    /// 航海と区間を 1 度のクエリで取る。
    ///
    /// **区間ごとにクエリを投げない**（N+1）。行は航海番号・区間順で並べ、
    /// 読み手が航海の切れ目を判定する。
    ///
    def selectColumns(): String =
        "SELECT v.voyage_number, v.vessel_name, v.carrier_name, v.capabilities, " +
        "m.departure_location_unlocode, m.arrival_location_unlocode, " +
        "m.departure_date, m.arrival_date, m.seq_number " +
        "FROM voyage v JOIN carrier_movement m ON m.voyage_id = v.id "

    def selectAllSql(): String =
        selectColumns() + "ORDER BY v.voyage_number, m.seq_number"

    def selectByNumberSql(): String =
        selectColumns() + "WHERE v.voyage_number = ? ORDER BY m.seq_number"

    ///
    /// 検索。条件は**常に 5 つ書き**、「絞らない」場合はプレースホルダへ
    /// 全件一致の値を入れる。条件の有無で SQL を分岐させると、
    /// 片方の分岐だけ条件を忘れうる（予約一覧と同じ判断）。
    ///
    /// 端点は**航程全体**で見る。区間単位で一致させると、
    /// 大阪→東京→LA の航海が「東京発 LA 行き」としても出てしまう。
    ///
    /// 対応貨物種別は `,GENERAL,` の形で照合する。囲まないと
    /// `GENERAL_CARGO` のような値にも一致する（将来の誤検出）。
    ///
    def searchSql(): String =
        selectColumns() +
        "WHERE (? = '' OR v.voyage_number IN (" +
        "  SELECT voyage_number FROM voyage v2 JOIN carrier_movement m2 ON m2.voyage_id = v2.id " +
        "  WHERE m2.seq_number = 1 AND m2.departure_location_unlocode = ?)) " +
        "  AND (? = '' OR v.voyage_number IN (" +
        "  SELECT v3.voyage_number FROM voyage v3 JOIN carrier_movement m3 ON m3.voyage_id = v3.id " +
        "  WHERE m3.arrival_location_unlocode = ? " +
        "    AND m3.seq_number = (SELECT MAX(seq_number) FROM carrier_movement WHERE voyage_id = v3.id))) " +
        "  AND (? = '' OR v.voyage_number IN (" +
        "  SELECT v4.voyage_number FROM voyage v4 JOIN carrier_movement m4 ON m4.voyage_id = v4.id " +
        "  WHERE m4.seq_number = 1 AND m4.departure_date >= CAST(? AS TIMESTAMP))) " +
        "  AND (? = '' OR v.voyage_number IN (" +
        "  SELECT v5.voyage_number FROM voyage v5 JOIN carrier_movement m5 ON m5.voyage_id = v5.id " +
        "  WHERE m5.seq_number = 1 AND m5.departure_date <= CAST(? AS TIMESTAMP))) " +
        "  AND (? = '' OR v.capabilities LIKE ?) " +
        "ORDER BY v.voyage_number, m.seq_number"

    // ---- 書き込み ----

    def insert(voyage: Voyage): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, insertVoyageSql(), stmt -> {
            stmt.setString(1, RoutingModel.voyageNumberOf(voyage));
            stmt.setString(2, RoutingModel.vesselNameOf(voyage));
            stmt.setString(3, RoutingModel.carrierNameOf(voyage));
            stmt.setString(4, RoutingModel.capabilitiesToPersisted(
                RoutingModel.capabilitiesOf(voyage)));
            discard stmt.executeUpdate()
        });
        insertMovements(voyage)

    ///
    /// 区間を挿入する（航海 ID は航海番号から引き直す）。
    ///
    /// 航海が引けないときは**落とす**。黙って返すと航海の行だけが入り、
    /// 区間 0 件になる。読み出しは `JOIN` のため、その航海は一覧にも詳細にも
    /// **一切現れない**。「登録に成功したと言われたのに存在しない」は
    /// 最も追いにくい壊れ方である（IT6 レビュー H13）。
    ///
    def insertMovements(voyage: Voyage): Unit \ Tx + IO =
        match voyageIdOf(RoutingModel.voyageNumberOf(voyage)) {
            case None =>
                bug!("航海 ${RoutingModel.voyageNumberOf(voyage)} を挿入直後に引けませんでした")
            case Some(voyageId) =>
                let conn = Tx.connection();
                withPreparedStatement(conn, insertMovementSql(), stmt ->
                    List.forEach(m -> {
                        stmt.setLong(1, voyageId);
                        stmt.setString(2, RoutingModel.departureLocationOf(m));
                        stmt.setString(3, RoutingModel.arrivalLocationOf(m));
                        // 内部表現は `YYYY-MM-DDTHH:MM`。`TIMESTAMP` へ変換するため
                        // `T` を空白へ直し、秒を補う
                        stmt.setString(4, toSqlTimestamp(RoutingModel.departureTimeOf(m)));
                        stmt.setString(5, toSqlTimestamp(RoutingModel.arrivalTimeOf(m)));
                        stmt.setInt(6, RoutingModel.seqNumberOf(m));
                        discard stmt.executeUpdate()
                    }, RoutingModel.movementsOf(voyage)))
        }

    ///
    /// `YYYY-MM-DDTHH:MM` を SQL の `TIMESTAMP` リテラルへ直す。
    ///
    /// **H2 と PostgreSQL の双方で動く形**にする。`T` 区切りは
    /// PostgreSQL が受け付けるが H2 は受け付けない場合がある。
    /// 空白区切り + 秒まで書けば両方で通る。
    ///
    def toSqlTimestamp(value: String): String =
        String.replace(src = "T", dst = " ", value) + ":00"

    /// SQL の `TIMESTAMP` を `YYYY-MM-DDTHH:MM` へ戻す
    def fromSqlTimestamp(value: String): String =
        let head = if (String.length(value) >= 16)
                       String.slice(start = 0, end = 16, value)
                   else
                       value;
        String.replace(src = " ", dst = "T", head)

    def voyageIdOf(voyageNumber: String): Option[Int64] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectVoyageIdSql(), stmt -> {
            stmt.setString(1, voyageNumber);
            let rs = stmt.executeQuery();
            let found = if (rs.next()) Some(rs.getLong("id")) else None;
            rs.close();
            found
        })

    ///
    /// 更新のために引く。**先に行を押さえてから読む**。
    ///
    def selectForUpdate(voyageNumber: String): Option[Voyage] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, lockVoyageSql(), stmt -> {
            stmt.setString(1, voyageNumber);
            let rs = stmt.executeQuery();
            discard rs.next();
            rs.close()
        });
        selectByNumber(voyageNumber)

    ///
    /// 航海を丸ごと置き換える。
    ///
    /// **区間は削除してから挿入する**。差分更新にすると、区間を減らす更新で
    /// 古い行が残り、航程が伸びたままになる。
    ///
    def replaceVoyage(voyage: Voyage): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, updateVoyageSql(), stmt -> {
            stmt.setString(1, RoutingModel.vesselNameOf(voyage));
            stmt.setString(2, RoutingModel.carrierNameOf(voyage));
            stmt.setString(3, RoutingModel.capabilitiesToPersisted(
                RoutingModel.capabilitiesOf(voyage)));
            stmt.setString(4, RoutingModel.voyageNumberOf(voyage));
            // **更新行数を捨てない**。0 行なら対象が消えており、
            // 呼び出し側は保存できたと誤解する（IT6 レビュー H4）
            let updated = stmt.executeUpdate();
            if (updated == 1) ()
            else bug!("航海 ${RoutingModel.voyageNumberOf(voyage)} の更新が ${updated} 行でした")
        });
        withPreparedStatement(conn, deleteMovementsSql(), stmt -> {
            stmt.setString(1, RoutingModel.voyageNumberOf(voyage));
            discard stmt.executeUpdate()
        });
        insertMovements(voyage)

    // ---- 読み出し ----

    def selectAll(): List[Voyage] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectAllSql(), stmt -> {
            let rs = stmt.executeQuery();
            let rows = collectRows(rs, Nil);
            rs.close();
            assemble(List.reverse(rows))
        })

    def selectByNumber(voyageNumber: String): Option[Voyage] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectByNumberSql(), stmt -> {
            stmt.setString(1, voyageNumber);
            let rs = stmt.executeQuery();
            let rows = collectRows(rs, Nil);
            rs.close();
            List.head(assemble(List.reverse(rows)))
        })

    def selectMatching(origin: String, destination: String,
                       departureFrom: String, departureTo: String,
                       capability: Option[CargoCapability]): List[Voyage] \ Tx + IO =
        let originValue = String.toUpperCase(String.trim(origin));
        let destinationValue = String.toUpperCase(String.trim(destination));
        let fromValue = String.trim(departureFrom);
        let toValue = String.trim(departureTo);
        let capabilityValue = match capability {
            case None    => ""
            case Some(c) => RoutingModel.capabilityPattern(c)
        };
        let conn = Tx.connection();
        withPreparedStatement(conn, searchSql(), stmt -> {
            stmt.setString(1, originValue);
            stmt.setString(2, originValue);
            stmt.setString(3, destinationValue);
            stmt.setString(4, destinationValue);
            stmt.setString(5, fromValue);
            // 出発期間の開始は**その日の 00:00 から**含める
            stmt.setString(6, fromValue + " 00:00:00");
            stmt.setString(7, toValue);
            // 終了は**その日の 23:59:59 まで**含める。日付だけで比較すると
            // 指定日当日の便が丸ごと落ちる（IT5 の到着期限と同じ形の境界）
            stmt.setString(8, toValue + " 23:59:59");
            stmt.setString(9, capabilityValue);
            stmt.setString(10, "%" + capabilityValue + "%");
            let rs = stmt.executeQuery();
            let rows = collectRows(rs, Nil);
            rs.close();
            assemble(List.reverse(rows))
        })

    /// 1 行分の生の値（航海の属性 + 区間）
    type alias Row = (String, String, String, String, CarrierMovement)

    def collectRows(rs: ResultSet, acc: List[Row]): List[Row] \ IO =
        if (rs.next()) {
            let row = (
                rs.getString("voyage_number"),
                rs.getString("vessel_name"),
                rs.getString("carrier_name"),
                rs.getString("capabilities"),
                RoutingModel.reconstructMovement(
                    rs.getString("departure_location_unlocode"),
                    rs.getString("arrival_location_unlocode"),
                    fromSqlTimestamp(rs.getString("departure_date")),
                    fromSqlTimestamp(rs.getString("arrival_date")),
                    rs.getInt("seq_number"))
            );
            collectRows(rs, row :: acc)
        } else
            acc

    ///
    /// 行の並びを航海へ組み立てる。
    ///
    /// 行は航海番号・区間順で並んでいる前提である（SQL の `ORDER BY`）。
    /// **区間が 1 件も無い航海は現れない**（`JOIN` のため）。これは仕様どおりで、
    /// 区間の無い航海は成立しない（`ScheduleIsEmpty`）。
    ///
    def assemble(rows: List[Row]): List[Voyage] =
        groupConsecutive(rows) |> List.filterMap(toVoyage)

    ///
    /// 航海番号が同じ**連続した行**をまとめる。
    ///
    /// `List.groupBy` は `Nel`（空でないリスト）を返し、扱いが増える。
    /// 行は `ORDER BY` で並んでいるため、隣り合う行を見れば足りる。
    ///
    def groupConsecutive(rows: List[Row]): List[List[Row]] =
        match rows {
            case Nil     => Nil
            case r :: rest =>
                let number = voyageNumberOfRow(r);
                let (same, others) = List.span(x -> voyageNumberOfRow(x) == number, rest);
                (r :: same) :: groupConsecutive(others)
        }

    def toVoyage(group: List[Row]): Option[Voyage] =
        match List.head(group) {
            case None      => None
            case Some(row) =>
                let (number, vessel, carrier, capabilities, _) = row;
                Some(RoutingModel.reconstruct(
                    number, vessel, carrier,
                    RoutingModel.capabilitiesFromPersisted(capabilities),
                    group |> List.map(movementOfRow)))
        }

    def movementOfRow(row: Row): CarrierMovement =
        let (_, _, _, _, m) = row;
        m

    def voyageNumberOfRow(row: Row): String =
        let (number, _, _, _, _) = row;
        number

    ///
    /// `VoyageRepo` 効果に JDBC 実装を注入する。
    ///
    /// 合成ルートとテストからのみ適用する（`arch-lint` 規約 5）。
    ///
    pub def withJdbcVoyageRepo(f: Unit -> a \ ef + VoyageRepo): a \ (ef - VoyageRepo) + Tx + IO =
        run {
            f()
        } with handler VoyageRepo {
            def save(voyage, k) = { insert(voyage); k() }
            def findByVoyageNumber(number, k) = k(selectByNumber(number))
            def findForUpdate(number, k) = k(selectForUpdate(number))
            def findAll(k) = k(selectAll())
            def replace(voyage, k) = { replaceVoyage(voyage); k() }
            def search(origin, destination, departureFrom, departureTo, capability, k) =
                k(selectMatching(origin, destination, departureFrom, departureTo, capability))
        }
}
