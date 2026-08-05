///
/// `CargoRepo` 効果の JDBC 実装（本番アダプタ）。
///
mod BookingJdbcCargoRepo {
    import java.math.BigDecimal
    import java.sql.PreparedStatement
    import java.sql.ResultSet
    import java.sql.Types

    use BookingModel.{Cargo, BookingStatus, CargoType, RouteSpecification}
    use CargoRepo.CargoRepo
    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx

    def insertCargoSql(): String =
        "INSERT INTO cargo " +
        "(booking_id, shipper_id, cargo_type, weight, origin_unlocode, " +
        "destination_unlocode, arrival_deadline, booking_status, " +
        "dimension_length, dimension_width, dimension_height, quantity, description, " +
        "hazardous_class, un_number, proper_shipping_name, " +
        "min_temperature, max_temperature, temperature_unit) " +
        "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS DATE), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    def selectColumns(): String =
        "SELECT booking_id, shipper_id, cargo_type, weight, origin_unlocode, " +
        "destination_unlocode, arrival_deadline, booking_status, " +
        "dimension_length, dimension_width, dimension_height, quantity, description, " +
        "hazardous_class, un_number, proper_shipping_name, " +
        "min_temperature, max_temperature, temperature_unit, routing_status " +
        "FROM cargo "

    def selectByBookingIdSql(): String = selectColumns() + "WHERE booking_id = ?"

    ///
    /// 状態の更新。
    ///
    /// **状態だけを更新する**。行全体を書き戻すと、同時に別の更新があったとき
    /// 読み込んだ時点の古い値で他の列を上書きしてしまう。
    ///
    def updateStatusSql(): String =
        "UPDATE cargo SET booking_status = ?, updated_at = CURRENT_TIMESTAMP " +
        "WHERE booking_id = ?"

    /// 旅程の割り当て（IT9）。**`booking_status` は触らない**
    def updateRoutingStatusSql(): String =
        "UPDATE cargo SET routing_status = ?, updated_at = CURRENT_TIMESTAMP " +
        "WHERE booking_id = ?"

    def selectCargoIdSql(): String = "SELECT id FROM cargo WHERE booking_id = ?"

    def deleteLegsSql(): String = "DELETE FROM leg WHERE cargo_id = ?"

    def insertLegSql(): String =
        "INSERT INTO leg " +
        "(cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode, " +
        "load_time, unload_time, seq_number) " +
        "VALUES (?, ?, ?, ?, CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP), ?)"

    def selectLegsSql(): String =
        "SELECT voyage_number, load_location_unlocode, unload_location_unlocode, " +
        "load_time, unload_time " +
        "FROM leg WHERE cargo_id = ? ORDER BY seq_number"

    /// 一覧は予約番号順。利用者が探すときの手掛かりは予約番号である
    def selectAllSql(): String = selectColumns() + "ORDER BY booking_id"

    ///
    /// 検索。可視範囲（荷主）と絞り込み語を **AND で重ねる**。
    ///
    /// 片方だけを適用すると、絞り込みの結果に他人の予約が混ざる
    /// （観点表の軸 6-5）。SQL を条件ごとに組み立てず、**常に 2 条件を書き**
    /// 「絞らない」場合はプレースホルダへ全件一致の値を入れる。
    /// 条件の有無で SQL を分岐させると、片方の分岐だけ可視範囲を忘れうる。
    ///
    /// `ESCAPE '\'` を明示するのは、利用者の入力に含まれる `%` や `_` を
    /// ワイルドカードとして扱わないためである（荷主一覧と同じ）。
    ///
    /// 状態と荷主名を IT8（TS14）で足した。**可視範囲の条件は常に先頭に書く**
    def searchSql(status: BookingModel.StatusFilter,
                  shipperIds: Option[List[String]]): String =
        selectColumns() +
        "WHERE (? = '' OR shipper_id = ?) " +
        "  AND (LOWER(booking_id) LIKE ? ESCAPE '\\' " +
        "    OR LOWER(origin_unlocode) LIKE ? ESCAPE '\\' " +
        "    OR LOWER(destination_unlocode) LIKE ? ESCAPE '\\') " +
        "  AND (? = '' OR booking_status = ?) " +
        statusClause(status) +
        shipperIdsClause(shipperIds) +
        "ORDER BY booking_id"

    ///
    /// 知らない状態語が指定されたときの条件。
    ///
    /// **0 件に倒す**（IT8 計画の壊れ方の観点・軸 1）。黙って全件へ広げると、
    /// 利用者は絞ったつもりで**その状態ではない予約を対象に作業する**。
    /// `shipperIdsClause` の `Some(Nil)` と同じ判断・同じ書き方である。
    ///
    def statusClause(status: BookingModel.StatusFilter): String =
        match status {
            case BookingModel.StatusFilter.UnknownStatus(_) => "  AND 1 = 0 "
            case _                                          => ""
        }

    ///
    /// 荷主 ID の集合による条件。
    ///
    /// **`IN` は要素数だけプレースホルダが要る**ため、ここだけは SQL を
    /// 組み立てる。組み立てるのは条件の**追加**だけで、可視範囲と語の条件は
    /// この分岐に関係なく常に書かれる。分岐で条件が消える形にしない。
    ///
    /// `Some(Nil)`（該当する荷主が無い）を `1 = 0` にするのが要点である。
    /// 条件を落とすと、存在しない荷主名で全件が出る。
    ///
    def shipperIdsClause(shipperIds: Option[List[String]]): String =
        match shipperIds {
            case None      => ""
            case Some(Nil) => "  AND 1 = 0 "
            case Some(ids) =>
                let marks = ids |> List.map(_ -> "?") |> List.join(", ");
                "  AND shipper_id IN (${marks}) "
        }

    /// 貨物を登録する
    def insert(cargo: Cargo): Unit \ Tx + IO =
        let conn = Tx.connection();
        let spec = BookingModel.routeSpecificationOf(cargo);
        withPreparedStatement(conn, insertCargoSql(), stmt -> {
            stmt.setString(1, BookingModel.bookingIdOf(cargo));
            stmt.setString(2, BookingModel.shipperIdOf(cargo));
            stmt.setString(3, BookingModel.cargoTypeToPersisted(BookingModel.cargoTypeOf(cargo)));
            // 重量はグラム保持。DB は NUMERIC(10,3)（キログラム）のため 1000 で割る
            let weight = BigDecimal.valueOf(BookingModel.weightGramsOf(cargo));
            stmt.setBigDecimal(4, weight.movePointLeft(3));
            stmt.setString(5, BookingModel.originOf(spec));
            stmt.setString(6, BookingModel.destinationOf(spec));
            stmt.setString(7, BookingModel.arrivalDeadlineOf(spec));
            stmt.setString(8, BookingModel.statusToPersisted(BookingModel.bookingStatusOf(cargo)));
            setDimensions(stmt, BookingModel.dimensionsOf(cargo));
            setNullableInt(stmt, 12, BookingModel.quantityOf(cargo));
            setNullableString(stmt, 13, BookingModel.descriptionOf(cargo));
            setHazardous(stmt, BookingModel.hazardousDeclarationOf(cargo));
            setTemperature(stmt, BookingModel.temperatureRequirementOf(cargo));
            discard stmt.executeUpdate()
        })

    /// 状態を更新する
    def updateStatus(cargo: Cargo): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, updateStatusSql(), stmt -> {
            stmt.setString(1, BookingModel.statusToPersisted(BookingModel.bookingStatusOf(cargo)));
            stmt.setString(2, BookingModel.bookingIdOf(cargo));
            discard stmt.executeUpdate()
        })

    /// 寸法（3 列）をまとめて設定する
    def setDimensions(stmt: PreparedStatement, value: Option[(Int32, Int32, Int32)]): Unit \ IO =
        match value {
            case None => {
                stmt.setNull(9, Types.NUMERIC);
                stmt.setNull(10, Types.NUMERIC);
                stmt.setNull(11, Types.NUMERIC)
            }
            case Some((l, w, h)) => {
                // 内部はミリメートル、DB 列は `NUMERIC(10,3)`（センチメートル）。
                // **スケールを忘れると 10 倍の値が保存される**。読み書きの両端が
                // 同じ単位である限り往復テストは緑になり、アプリの外から読む人
                // （帳票・BI・他システム）だけが誤った値を見る（IT5 レビュー）
                stmt.setBigDecimal(9, centimeters(l));
                stmt.setBigDecimal(10, centimeters(w));
                stmt.setBigDecimal(11, centimeters(h))
            }
        }

    ///
    /// 危険物申告（3 列）をまとめて設定する。
    ///
    /// **申告が無ければ 3 列とも NULL にする**。種別を変更したときに旧種別の
    /// 列が残ると、一般貨物なのに危険物の書類が付いた行になる。
    ///
    def setHazardous(stmt: PreparedStatement,
                     value: Option[BookingModel.HazardousDeclaration]): Unit \ IO =
        match value {
            case None => {
                stmt.setNull(14, Types.VARCHAR);
                stmt.setNull(15, Types.VARCHAR);
                stmt.setNull(16, Types.VARCHAR)
            }
            case Some(d) => {
                stmt.setString(14, BookingModel.hazardousClassOf(d));
                stmt.setString(15, BookingModel.unNumberOf(d));
                stmt.setString(16, BookingModel.properShippingNameOf(d))
            }
        }

    ///
    /// 温度管理条件（3 列）をまとめて設定する。
    ///
    /// 温度は千分の一度の整数で保持している。DB は `NUMERIC(10,3)`（度）のため
    /// 小数点を 3 桁戻す。重量（グラム ⇄ キログラム）と同じ形である。
    ///
    def setTemperature(stmt: PreparedStatement,
                       value: Option[BookingModel.TemperatureRequirement]): Unit \ IO =
        match value {
            case None => {
                stmt.setNull(17, Types.NUMERIC);
                stmt.setNull(18, Types.NUMERIC);
                stmt.setNull(19, Types.VARCHAR)
            }
            case Some(t) => {
                let lo = BigDecimal.valueOf(BookingModel.minTemperatureMilliOf(t));
                let hi = BigDecimal.valueOf(BookingModel.maxTemperatureMilliOf(t));
                stmt.setBigDecimal(17, lo.movePointLeft(3));
                stmt.setBigDecimal(18, hi.movePointLeft(3));
                stmt.setString(19,
                    BookingModel.temperatureUnitToPersisted(BookingModel.temperatureUnitOf(t)))
            }
        }

    /// ミリメートルの整数をセンチメートルの十進数へ直す
    def centimeters(millimeters: Int32): BigDecimal \ IO =
        BigDecimal.valueOf(Int32.toInt64(millimeters)).movePointLeft(1)

    def setNullableInt(stmt: PreparedStatement, index: Int32, value: Option[Int32]): Unit \ IO =
        match value {
            case Some(v) => stmt.setInt(index, v)
            case None    => stmt.setNull(index, Types.INTEGER)
        }

    def setNullableString(stmt: PreparedStatement, index: Int32, value: Option[String]): Unit \ IO =
        match value {
            case Some(v) => stmt.setString(index, v)
            case None    => stmt.setNull(index, Types.VARCHAR)
        }

    /// 行を貨物へ復元する
    def decode(rs: ResultSet): Option[Cargo] \ IO =
        let statusValue = rs.getString("booking_status");
        let typeValue = rs.getString("cargo_type");
        match (BookingModel.statusFromPersisted(statusValue),
               BookingModel.cargoTypeFromPersisted(typeValue)) {
            // 未知の値は「貨物として読めない」ものとして扱う。既定値へ倒すと
            // 確定済みの予約が未確定に見え、危険物が一般貨物として扱われる
            case (Some(status), Some(cargoType)) =>
                let spec = BookingModel.reconstructSpec(
                    rs.getString("origin_unlocode"),
                    rs.getString("destination_unlocode"),
                    rs.getString("arrival_deadline"));
                let weight = rs.getBigDecimal("weight");
                Some(BookingModel.reconstruct(
                    rs.getString("booking_id"),
                    rs.getString("shipper_id"),
                    spec,
                    status,
                    cargoType,
                    weight.movePointRight(3).longValue(),
                    readDimensions(rs),
                    nullableInt(rs, "quantity"),
                    nullableString(rs, "description"),
                    readHazardous(rs),
                    readTemperature(rs),
                    // 旅程は別表。**ここでは読まない**——`ResultSet` を開いたまま
                    // 別の文を実行するとドライバによっては閉じられる。
                    // 一覧では旅程を使わないため、詳細を引くときだけ読む
                    None,
                    readRoutingStatus(rs)))
            case _ => None
        }

    ///
    /// 危険物申告を読む。
    ///
    /// **3 列が揃って初めて申告になる**。部分的に NULL の行は申告なしとして扱う。
    /// 欠けた値を空文字で埋めると、書類として成立しない申告が画面に出る。
    ///
    def readHazardous(rs: ResultSet): Option[BookingModel.HazardousDeclaration] \ IO =
        match (nullableString(rs, "hazardous_class"),
               nullableString(rs, "un_number"),
               nullableString(rs, "proper_shipping_name")) {
            case (Some(c), Some(u), Some(n)) => Some(BookingModel.reconstructHazardous(c, u, n))
            case _                           => None
        }

    ///
    /// 温度管理条件を読む。
    ///
    /// 単位が未知の値だった場合は条件なしとして扱う。**摂氏へ倒さない**
    /// （華氏 -13°F を -13°C として読むと 12 度ずれ、冷凍と冷蔵が入れ替わる）。
    ///
    def readTemperature(rs: ResultSet): Option[BookingModel.TemperatureRequirement] \ IO =
        match (nullableMilli(rs, "min_temperature"),
               nullableMilli(rs, "max_temperature"),
               nullableString(rs, "temperature_unit")) {
            case (Some(lo), Some(hi), Some(u)) =>
                BookingModel.temperatureUnitFromPersisted(u)
                    |> Option.map(unit -> BookingModel.reconstructTemperature(lo, hi, unit))
            case _ => None
        }

    /// `NUMERIC(10,3)`（度）を千分の一度の整数として読む
    def nullableMilli(rs: ResultSet, column: String): Option[Int64] \ IO =
        let value = rs.getBigDecimal(column);
        if (Object.isNull(value)) None else Some(value.movePointRight(3).longValue())

    def readDimensions(rs: ResultSet): Option[(Int32, Int32, Int32)] \ IO =
        match (nullableDecimal(rs, "dimension_length"),
               nullableDecimal(rs, "dimension_width"),
               nullableDecimal(rs, "dimension_height")) {
            case (Some(l), Some(w), Some(h)) => Some((l, w, h))
            case _                           => None
        }

    ///
    /// センチメートルの列をミリメートルの整数として読む。
    ///
    /// `intValue()` を直接使わない。**小数を黙って切り捨てる**ため、
    /// 100.5 cm が 100 mm ではなく 100 cm として読まれる。
    ///
    def nullableDecimal(rs: ResultSet, column: String): Option[Int32] \ IO =
        let value = rs.getBigDecimal(column);
        if (Object.isNull(value)) None else Some(value.movePointRight(1).intValue())

    def nullableInt(rs: ResultSet, column: String): Option[Int32] \ IO =
        let value = rs.getInt(column);
        if (rs.wasNull()) None else Some(value)

    def nullableString(rs: ResultSet, column: String): Option[String] \ IO =
        let value = rs.getString(column);
        if (Object.isNull(value)) None else Some(value)

    def selectOne(bookingId: String): Option[Cargo] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectByBookingIdSql(), stmt -> {
            stmt.setString(1, bookingId);
            let rs = stmt.executeQuery();
            let found = if (rs.next()) decode(rs) else None;
            rs.close();
            found
        })

    ///
    /// 行を押さえてから旅程込みで引く（IT9・レビュー H2）。
    ///
    /// `JdbcVoyageRepo.selectForUpdate` と同じ形——**`FOR UPDATE` は
    /// 主キー行だけに掛け**、値の取得は通常の読み取りに任せる。
    /// 旅程まで含めて結合したまま `FOR UPDATE` を掛けると、
    /// `leg` の行にもロックが掛かって割り当てと確定が不必要に競合する。
    ///
    def selectOneForUpdate(bookingId: String): Option[Cargo] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, lockCargoSql(), stmt -> {
            stmt.setString(1, bookingId);
            let rs = stmt.executeQuery();
            discard rs.next();
            rs.close()
        });
        selectOneWithItinerary(bookingId)

    def lockCargoSql(): String =
        "SELECT id FROM cargo WHERE booking_id = ? FOR UPDATE"

    def selectAll(): List[Cargo] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectAllSql(), stmt -> {
            let rs = stmt.executeQuery();
            let rows = collect(rs, Nil);
            rs.close();
            List.reverse(rows)
        })

    ///
    /// 旅程と経路状態を保存する。
    ///
    /// **区間は置き換える**（削除してから挿入）。選び直しで足す形にすると、
    /// 古い区間が混ざって連結の検証を通らない旅程ができる。
    ///
    /// **`booking_status` は触らない**。経路の割り当ては予約を確定しない
    /// （US13 との職掌の違い）。
    ///
    def saveItineraryInDb(cargo: Cargo): Unit \ Tx + IO =
        let conn = Tx.connection();
        let bookingId = BookingModel.bookingIdOf(cargo);
        withPreparedStatement(conn, updateRoutingStatusSql(), stmt -> {
            stmt.setString(1, SharedRoutingStatus.toPersisted(
                BookingModel.routingStatusOf(BookingModel.deliveryOfCargo(cargo))));
            stmt.setString(2, bookingId);
            discard stmt.executeUpdate()
        });
        let cargoId = selectCargoId(bookingId);
        withPreparedStatement(conn, deleteLegsSql(), stmt -> {
            stmt.setLong(1, cargoId);
            discard stmt.executeUpdate()
        });
        match BookingModel.cargoItineraryOf(cargo) {
            case None    => ()
            case Some(i) => insertLegs(cargoId, BookingModel.legsOf(i), 1)
        }

    def selectCargoId(bookingId: String): Int64 \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectCargoIdSql(), stmt -> {
            stmt.setString(1, bookingId);
            let rs = stmt.executeQuery();
            let found = if (rs.next()) rs.getLong("id") else -1i64;
            rs.close();
            if (found < 0i64)
                // **既定値で続けない**。0 を返すと区間が別の貨物にぶら下がる
                bug!("保存対象の予約が引けない: ${bookingId}")
            else
                found
        })

    def insertLegs(cargoId: Int64, legs: List[BookingModel.Leg],
                   seq: Int32): Unit \ Tx + IO =
        match legs {
            case Nil     => ()
            case l :: ls => {
                let conn = Tx.connection();
                withPreparedStatement(conn, insertLegSql(), stmt -> {
                    stmt.setLong(1, cargoId);
                    stmt.setString(2, BookingModel.legVoyageNumberOf(l));
                    stmt.setString(3, BookingModel.legLoadLocationOf(l));
                    stmt.setString(4, BookingModel.legUnloadLocationOf(l));
                    // `T` 区切りの時刻を DB の形式へ。`CAST(? AS TIMESTAMP)` が解釈する
                    stmt.setString(5, timestampText(BookingModel.legLoadTimeOf(l)));
                    stmt.setString(6, timestampText(BookingModel.legUnloadTimeOf(l)));
                    stmt.setInt(7, seq);
                    discard stmt.executeUpdate()
                });
                insertLegs(cargoId, ls, seq + 1)
            }
        }

    /// `2026-11-01T10:00` を `2026-11-01 10:00:00` へ
    def timestampText(value: String): String =
        let replaced = String.replace(src = "T", dst = " ", String.trim(value));
        if (String.length(replaced) == 16) replaced + ":00" else replaced

    /// 旅程を読む（詳細でのみ使う。一覧では引かない）
    def selectLegs(cargoId: Int64): List[BookingModel.Leg] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectLegsSql(), stmt -> {
            stmt.setLong(1, cargoId);
            let rs = stmt.executeQuery();
            let rows = collectLegs(rs, Nil);
            rs.close();
            List.reverse(rows)
        })

    def collectLegs(rs: ResultSet, acc: List[BookingModel.Leg]): List[BookingModel.Leg] \ IO =
        if (rs.next()) {
            let l = BookingModel.leg(
                rs.getString("voyage_number"),
                rs.getString("load_location_unlocode"),
                rs.getString("unload_location_unlocode"),
                nullableString(rs, "load_time") |> Option.getWithDefault(""),
                nullableString(rs, "unload_time") |> Option.getWithDefault(""));
            collectLegs(rs, l :: acc)
        } else
            acc

    ///
    /// 旅程を含めて 1 件引く。
    ///
    /// **`ResultSet` を閉じてから区間を引く**。開いたまま別の文を実行すると
    /// ドライバによっては最初の `ResultSet` が閉じられる（IT8 の見積で踏んだ形）。
    ///
    def selectOneWithItinerary(bookingId: String): Option[Cargo] \ Tx + IO =
        match selectOne(bookingId) {
            case None    => None
            case Some(c) =>
                let cargoId = selectCargoId(BookingModel.bookingIdOf(c));
                match selectLegs(cargoId) {
                    case Nil  => Some(c)
                    case legs => Some(BookingModel.withItinerary(c, BookingModel.itineraryOf(legs)))
                }
        }

    /// 可視範囲と絞り込み条件で引く
    def selectMatching(shipperId: Option[String],
                       criteria: BookingModel.CargoQuery): List[Cargo] \ Tx + IO =
        let owner = Option.getWithDefault("", shipperId);
        let keyword = BookingModel.queryKeywordOf(criteria);
        let pattern = SharedDbSql.likePattern(keyword);
        let status = BookingModel.queryStatusOf(criteria);
        // 絞らない場合は空文字。`searchSql` の `? = ''` がその行を素通しにする。
        // 未知の語は `statusClause` が `1 = 0` を足すため、ここは空文字でよい
        let statusValue = match status {
            case BookingModel.StatusFilter.OnlyStatus(s) => BookingModel.statusToPersisted(s)
            case _                                       => ""
        };
        let shipperIds = BookingModel.queryShipperIdsOf(criteria);
        let conn = Tx.connection();
        withPreparedStatement(conn, searchSql(status, shipperIds), stmt -> {
            stmt.setString(1, owner);
            stmt.setString(2, owner);
            stmt.setString(3, pattern);
            stmt.setString(4, pattern);
            stmt.setString(5, pattern);
            stmt.setString(6, statusValue);
            stmt.setString(7, statusValue);
            // `IN` の要素は 8 番目から順に並ぶ（`shipperIdsClause` の並びと同じ）
            setShipperIds(stmt, 8, Option.getWithDefault(Nil, shipperIds));
            let rs = stmt.executeQuery();
            let rows = collect(rs, Nil);
            rs.close();
            List.reverse(rows)
        })

    /// `IN` のプレースホルダへ荷主 ID を順に設定する
    def setShipperIds(stmt: PreparedStatement, at: Int32, ids: List[String]): Unit \ IO =
        match ids {
            case Nil     => ()
            case x :: xs => { stmt.setString(at, x); setShipperIds(stmt, at + 1, xs) }
        }

    ///
    /// 経路状態を読む。
    ///
    /// **読めない値は `NotRouted` へ倒す**。ここは業務上の意味を持つ値だが、
    /// 倒さないと**行そのものが読めなくなり予約が一覧から消える**。
    /// 経路が決まっているのに未決定と読むほうが、予約が消えるより害が小さい
    /// （画面には「経路設計中」と出るので、経路設計者が気付ける）。
    ///
    def readRoutingStatus(rs: ResultSet): SharedRoutingStatus.RoutingStatus \ IO =
        SharedRoutingStatus.fromPersisted(rs.getString("routing_status"))
            |> Option.getWithDefault(SharedRoutingStatus.RoutingStatus.NotRouted)

    def collect(rs: ResultSet, acc: List[Cargo]): List[Cargo] \ IO =
        if (rs.next())
            match decode(rs) {
                case Some(c) => collect(rs, c :: acc)
                // 読めない行は一覧から落とす。1 行の破損で画面全体を落とさない
                case None    => collect(rs, acc)
            }
        else
            acc

    /// `CargoRepo` 効果に JDBC 実装を注入する
    pub def withJdbcCargoRepo(f: Unit -> a \ ef + CargoRepo): a \ (ef - CargoRepo) + Tx + IO =
        run {
            f()
        } with handler CargoRepo {
            def save(cargo, k) = { insert(cargo); k() }
            def findByBookingId(bookingId, k) = k(selectOne(bookingId))
            def findAll(k) = k(selectAll())
            def updateStatus(cargo, k) = { updateStatus(cargo); k() }
            def search(shipperId, criteria, k) = k(selectMatching(shipperId, criteria))
            def saveItinerary(cargo, k) = { saveItineraryInDb(cargo); k() }
            def findWithItinerary(bookingId, k) = k(selectOneWithItinerary(bookingId))
            def findForUpdate(bookingId, k)      = k(selectOneForUpdate(bookingId))
        }
}
