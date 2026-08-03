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
        "dimension_length, dimension_width, dimension_height, quantity, description) " +
        "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS DATE), ?, ?, ?, ?, ?, ?)"

    def selectColumns(): String =
        "SELECT booking_id, shipper_id, cargo_type, weight, origin_unlocode, " +
        "destination_unlocode, arrival_deadline, booking_status, " +
        "dimension_length, dimension_width, dimension_height, quantity, description " +
        "FROM cargo "

    def selectByBookingIdSql(): String = selectColumns() + "WHERE booking_id = ?"

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
                    nullableString(rs, "description")))
            case _ => None
        }

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
        }
}
