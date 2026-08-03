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
        "min_temperature, max_temperature, temperature_unit " +
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

    /// 一覧は予約番号順。利用者が探すときの手掛かりは予約番号である
    def selectAllSql(): String = selectColumns() + "ORDER BY booking_id"

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
                stmt.setBigDecimal(9, BigDecimal.valueOf(Int32.toInt64(l)));
                stmt.setBigDecimal(10, BigDecimal.valueOf(Int32.toInt64(w)));
                stmt.setBigDecimal(11, BigDecimal.valueOf(Int32.toInt64(h)))
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
                    readTemperature(rs)))
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

    def nullableDecimal(rs: ResultSet, column: String): Option[Int32] \ IO =
        let value = rs.getBigDecimal(column);
        if (Object.isNull(value)) None else Some(value.intValue())

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

    def selectAll(): List[Cargo] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectAllSql(), stmt -> {
            let rs = stmt.executeQuery();
            let rows = collect(rs, Nil);
            rs.close();
            List.reverse(rows)
        })

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
            def update(cargo, k) = { updateStatus(cargo); k() }
        }
}
