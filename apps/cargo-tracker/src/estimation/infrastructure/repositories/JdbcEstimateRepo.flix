///
/// `EstimateRepo` 効果の JDBC 実装（本番アダプタ。US01・IT8）。
///
/// SQL は本モジュール内の定数として持つ。文字列連結による組み立ては行わない
/// （`arch-lint` 規約 9。SQL インジェクション対策）。
///
mod EstimationJdbcEstimateRepo {
    import java.math.BigDecimal
    import java.sql.PreparedStatement
    import java.sql.ResultSet

    use EstimateRepo.EstimateRepo
    use EstimationModel.{Estimate, RouteCandidate, CargoKind, EstimateStatus}
    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx

    def insertEstimateSql(): String =
        "INSERT INTO estimate " +
        "(estimate_id, origin_unlocode, destination_unlocode, arrival_deadline, " +
        "cargo_type, weight_kg, status) " +
        "VALUES (?, ?, ?, CAST(? AS DATE), ?, ?, ?)"

    ///
    /// 採番されたサロゲートキーを引く。
    ///
    /// `getGeneratedKeys` を使わないのは、**共有の
    /// `withPreparedStatement` が `RETURN_GENERATED_KEYS` を渡していない**
    /// ためである。ここだけのために共通処理へ引数を足すより、
    /// 業務キーで引き直すほうが影響が小さい（同一トランザクション内であり、
    /// `estimate_id` は UNIQUE である）。
    ///
    def selectSurrogateIdSql(): String = "SELECT id FROM estimate WHERE estimate_id = ?"

    def insertCandidateSql(): String =
        "INSERT INTO route_candidate " +
        "(estimate_id, voyage_number, transit_port, transit_days, estimated_cost, rank) " +
        "VALUES (?, ?, ?, ?, ?, ?)"

    def selectColumns(): String =
        "SELECT e.id, e.estimate_id, e.origin_unlocode, e.destination_unlocode, " +
        "e.arrival_deadline, e.cargo_type, e.weight_kg, e.status " +
        "FROM estimate e "

    def selectByEstimateIdSql(): String = selectColumns() + "WHERE e.estimate_id = ?"

    /// 一覧は**新しい順**（見積を探す手掛かりは時間である）
    def selectAllSql(): String = selectColumns() + "ORDER BY e.created_at DESC, e.id DESC"

    /// 候補は表示順（`rank` 昇順）で読む。保存した順序を再現する
    def selectCandidatesSql(): String =
        "SELECT voyage_number, transit_port, transit_days, estimated_cost " +
        "FROM route_candidate WHERE estimate_id = ? ORDER BY rank"

    ///
    /// 区間・経由港の連結に使う区切り。
    ///
    /// **画面の表示（`→`）と同じ文字を使わない**。表示を変えたときに
    /// 保存済みの値が読めなくなる。区切りは永続化の都合であり、
    /// 表示の都合とは別に決める。
    ///
    def separator(): String = "|"

    def joinParts(parts: List[String]): String = List.join(separator(), parts)

    /// 空文字は空リストへ戻す（`String.split` は空文字を 1 要素として返す）
    def splitParts(value: String): List[String] =
        if (String.trim(value) == "")
            Nil
        else
            String.splitOn(substr = separator(), value)

    def insert(estimate: Estimate): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, insertEstimateSql(), stmt -> {
            stmt.setString(1, EstimationModel.estimateIdOf(estimate));
            stmt.setString(2, EstimationModel.originOf(estimate));
            stmt.setString(3, EstimationModel.destinationOf(estimate));
            stmt.setString(4, EstimationModel.arrivalDeadlineOf(estimate));
            stmt.setString(5,
                EstimationModel.cargoKindToPersisted(EstimationModel.cargoKindOf(estimate)));
            // 内部はグラム、DB 列は NUMERIC(10,3)（キログラム）。
            // **スケールを忘れると 1000 倍の値が保存される**。読み書きの両端が
            // 同じ単位である限り往復テストは緑になり、アプリの外から読む人だけが
            // 誤った値を見る（IT5 レビューの寸法と同じ形）
            let weight = BigDecimal.valueOf(EstimationModel.weightGramsOf(estimate));
            stmt.setBigDecimal(6, weight.movePointLeft(3));
            stmt.setString(7,
                EstimationModel.statusToPersisted(EstimationModel.statusOf(estimate)));
            discard stmt.executeUpdate()
        });
        let surrogateId = selectSurrogateId(EstimationModel.estimateIdOf(estimate));
        insertCandidates(surrogateId, EstimationModel.candidatesOf(estimate), 0)

    def selectSurrogateId(estimateId: String): Int64 \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectSurrogateIdSql(), stmt -> {
            stmt.setString(1, estimateId);
            let rs = stmt.executeQuery();
            let found = if (rs.next()) rs.getLong("id") else -1i64;
            rs.close();
            if (found < 0i64)
                // **既定値で続けない**。0 を返すと候補が別の見積にぶら下がる
                bug!("保存したはずの見積が引けない: ${estimateId}")
            else
                found
        })

    def insertCandidates(estimateId: Int64, candidates: List[RouteCandidate],
                         rank: Int32): Unit \ Tx + IO =
        match candidates {
            case Nil     => ()
            case c :: cs => {
                let conn = Tx.connection();
                withPreparedStatement(conn, insertCandidateSql(), stmt -> {
                    stmt.setLong(1, estimateId);
                    stmt.setString(2,
                        joinParts(EstimationModel.candidateVoyageNumbersOf(c)));
                    stmt.setString(3,
                        joinParts(EstimationModel.candidateTransitPortsOf(c)));
                    stmt.setInt(4, EstimationModel.candidateTransitDaysOf(c));
                    // 金額は銭の整数。`BigDecimal` の小数点を 2 桁戻して
                    // `NUMERIC(12,2)` に合わせる（`Float64` を通さない。ADR-0006）
                    let cost = BigDecimal.valueOf(
                        SharedMoney.senOf(EstimationModel.candidateCostOf(c)));
                    stmt.setBigDecimal(5, cost.movePointLeft(2));
                    stmt.setInt(6, rank);
                    discard stmt.executeUpdate()
                });
                insertCandidates(estimateId, cs, rank + 1)
            }
        }

    def decode(rs: ResultSet): Option[Estimate] \ Tx + IO =
        let kindValue = rs.getString("cargo_type");
        let statusValue = rs.getString("status");
        match (EstimationModel.cargoKindFromPersisted(kindValue),
               EstimationModel.statusFromPersisted(statusValue)) {
            // 読めない行は落とす。**既定値へ倒すと危険物が一般貨物として読み出され、
            // 割増の付かない見積が「保存された見積」として画面に出る**
            case (Some(kind), Some(status)) =>
                let surrogateId = rs.getLong("id");
                let weight = rs.getBigDecimal("weight_kg");
                Some(EstimationModel.reconstruct(
                    rs.getString("estimate_id"),
                    rs.getString("origin_unlocode"),
                    rs.getString("destination_unlocode"),
                    rs.getString("arrival_deadline"),
                    kind,
                    weight.movePointRight(3).longValue(),
                    selectCandidates(surrogateId),
                    status))
            case _ => None
        }

    def selectCandidates(estimateId: Int64): List[RouteCandidate] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectCandidatesSql(), stmt -> {
            stmt.setLong(1, estimateId);
            let rs = stmt.executeQuery();
            let rows = collectCandidates(rs, Nil);
            rs.close();
            List.reverse(rows)
        })

    def collectCandidates(rs: ResultSet, acc: List[RouteCandidate]): List[RouteCandidate] \ IO =
        if (rs.next()) {
            let transitPort = rs.getString("transit_port");
            let ports = if (Object.isNull(transitPort)) Nil else splitParts(transitPort);
            let cost = rs.getBigDecimal("estimated_cost");
            let candidate = EstimationModel.routeCandidate(
                splitParts(rs.getString("voyage_number")),
                ports,
                rs.getInt("transit_days"),
                SharedMoney.ofSen(cost.movePointRight(2).longValue()));
            collectCandidates(rs, candidate :: acc)
        } else
            acc

    def selectOne(estimateId: String): Option[Estimate] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectByEstimateIdSql(), stmt -> {
            stmt.setString(1, String.trim(estimateId));
            let rs = stmt.executeQuery();
            // **`ResultSet` を閉じる前に復元する**。候補の読み取りが
            // 内側で別の文を実行するため、行の内容を先に取り出しておく
            let found = if (rs.next()) decode(rs) else None;
            rs.close();
            found
        })

    def selectAll(): List[Estimate] \ Tx + IO =
        let conn = Tx.connection();
        // **サロゲートキーを先に集めてから復元する**。1 行ごとに候補を引くと、
        // 同じ `Connection` 上で `ResultSet` を開いたまま別の文を実行することになり、
        // ドライバによっては最初の `ResultSet` が閉じられる
        let ids = withPreparedStatement(conn, selectAllSql(), stmt -> {
            let rs = stmt.executeQuery();
            let rows = collectEstimateIds(rs, Nil);
            rs.close();
            List.reverse(rows)
        });
        ids |> List.filterMap(selectOne)

    def collectEstimateIds(rs: ResultSet, acc: List[String]): List[String] \ IO =
        if (rs.next()) collectEstimateIds(rs, rs.getString("estimate_id") :: acc) else acc

    /// `EstimateRepo` 効果に JDBC 実装を注入する
    pub def withJdbcEstimateRepo(f: Unit -> a \ ef + EstimateRepo):
            a \ (ef - EstimateRepo) + Tx + IO =
        run {
            f()
        } with handler EstimateRepo {
            def save(estimate, k) = { insert(estimate); k() }
            def findByEstimateId(estimateId, k) = k(selectOne(estimateId))
            def findAll(k) = k(selectAll())
        }
}
