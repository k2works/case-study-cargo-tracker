///
/// `ShipperRepo` 効果の JDBC 実装（本番アダプタ）。
///
/// **本プロジェクト初の書き込みアダプタ**である。コネクションは自分で取得せず
/// `Tx.connection()` から受け取る。これにより同一トランザクション内の全 SQL が
/// 同一コネクション上で実行されることが型で保証される。
///
/// SQL は本モジュール内の定数として持つ。文字列連結による組み立ては行わない
/// （arch-lint 規約 9。SQL インジェクション対策）。
///
mod ShipperJdbcRepo {
    import java.math.BigDecimal
    import java.sql.PreparedStatement
    import java.sql.ResultSet
    import java.sql.Types

    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx
    use ShipperModel.{Shipper, ShipperType}
    use ShipperRepo.ShipperRepo

    /// 荷主の登録
    def insertShipperSql(): String =
        "INSERT INTO shipper " +
        "(shipper_id, shipper_code, shipper_type, name, email, email_normalized, " +
        "phone, address, contract_number, discount_rate) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"

    /// 選択するカラム（3 つの検索で共通）
    def selectColumns(): String =
        "SELECT shipper_id, shipper_code, shipper_type, name, email, phone, address, " +
        "contract_number, discount_rate FROM shipper "

    ///
    /// メールアドレスによる検索。
    ///
    /// **正規化列で比較する**。`Yamada@Example.com` と `yamada@example.com` は
    /// 同じ宛先であり、区別すると同じ人が二重登録される。
    /// `LOWER(email) = ?` ではなく列にするのは、**同じ正規化で一意制約も張る**ためである
    /// （式インデックスは H2 が解釈しない）。索引が効く点も同じ理由で望ましい。
    /// インメモリ実装も同じ比較をする（両者がずれると単体と統合で結論が変わる）。
    ///
    def selectByEmailSql(): String = selectColumns() + "WHERE email_normalized = ?"

    /// 荷主 ID による検索
    def selectByShipperIdSql(): String = selectColumns() + "WHERE shipper_id = ?"

    ///
    /// 一覧。荷主コード順に並べる。
    ///
    /// 登録順（`id` 順）にしないのは、**利用者が探すときの手掛かりはコード**だからである。
    ///
    def selectAllSql(): String = selectColumns() + "ORDER BY shipper_code"

    ///
    /// 絞り込み。氏名 / 社名・荷主コード・メールアドレスの部分一致。
    ///
    /// **DB で絞る**。全件を取り出してアプリ側で捨てると、件数が増えるほど
    /// 使われない行を運ぶことになる。
    ///
    /// 突合は小文字へ揃えた値どうしで行う。`LOWER(列)` を使わないのは、
    /// **式インデックスが H2 で使えない**ためである（IT4 で `LOWER(email)` の
    /// インデックスを諦め、正規化カラムを持つ形に切り替えた経緯がある）。
    /// `name` と `shipper_code` は正規化カラムを持たないため関数を使うが、
    /// メールは既存の `email_normalized` を使う。
    ///
    /// `ESCAPE '\'` を明示する。**利用者の入力に含まれる `%` や `_` を
    /// ワイルドカードとして扱わない**ためである。`%` の 1 文字で全件が出ると、
    /// 利用者は「絞り込んだのに全部出る」ことになり絞り込みを信用しなくなる。
    ///
    def searchSql(): String =
        selectColumns() +
        "WHERE LOWER(name) LIKE ? ESCAPE '\\' " +
        "   OR LOWER(shipper_code) LIKE ? ESCAPE '\\' " +
        "   OR email_normalized LIKE ? ESCAPE '\\' " +
        "ORDER BY shipper_code"

    /// 荷主を登録する
    def insert(shipper: Shipper): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, insertShipperSql(), stmt -> {
            stmt.setString(1, ShipperModel.shipperIdOf(shipper));
            stmt.setString(2, ShipperModel.shipperCodeOf(shipper));
            stmt.setString(3, ShipperModel.shipperTypeToPersisted(ShipperModel.shipperTypeOf(shipper)));
            stmt.setString(4, ShipperModel.nameOf(shipper));
            stmt.setString(5, ShipperModel.emailOf(shipper));
            // 表示は利用者の入力どおり、突合は正規化した値で行う
            stmt.setString(6, String.toLowerCase(ShipperModel.emailOf(shipper)));
            setNullableString(stmt, 7, ShipperModel.phoneOf(shipper));
            setNullableString(stmt, 8, ShipperModel.addressOf(shipper));
            setNullableString(stmt, 9, ShipperModel.contractNumberOf(shipper));
            setNullableRate(stmt, 10, ShipperModel.discountRateOf(shipper));
            discard stmt.executeUpdate()
        })

    ///
    /// 任意項目を設定する。
    ///
    /// `None` を空文字ではなく `NULL` として入れる。空文字を入れると
    /// 「未入力」と「空文字が入力された」を DB 上で区別できなくなる。
    ///
    def setNullableString(stmt: PreparedStatement, index: Int32,
                          value: Option[String]): Unit \ IO =
        match value {
            case Some(v) => stmt.setString(index, v)
            case None    => stmt.setNull(index, Types.VARCHAR)
        }

    ///
    /// 割引率を設定する。
    ///
    /// 内部表現は万分率の整数（`3000` = 30.00%）。DB は `NUMERIC(5,4)` のため
    /// 10000 で割った小数として入れる。**`BigDecimal` を経由し `Float64` を通さない**。
    /// 浮動小数点を挟むと 0.1500 が 0.14999... になりうる。
    ///
    def setNullableRate(stmt: PreparedStatement, index: Int32,
                        value: Option[Int32]): Unit \ IO =
        match value {
            case Some(basisPoints) =>
                let scaled = BigDecimal.valueOf(Int32.toInt64(basisPoints));
                stmt.setBigDecimal(index, scaled.movePointLeft(4))
            case None => stmt.setNull(index, Types.NUMERIC)
        }

    /// 行を荷主へ復元する
    def decode(rs: ResultSet): Option[Shipper] \ IO =
        let typeValue = rs.getString("shipper_type");
        match ShipperModel.shipperTypeFromPersisted(typeValue) {
            // 未知の種別は「荷主として読めない」ものとして扱う。既定値へ倒すと
            // 法人が個人として読み出され、割引が静かに消える
            case None => None
            case Some(shipperType) =>
                Some(ShipperModel.reconstruct(
                    rs.getString("shipper_id"),
                    rs.getString("shipper_code"),
                    rs.getString("name"),
                    rs.getString("email"),
                    nullableString(rs, "phone"),
                    nullableString(rs, "address"),
                    shipperType,
                    nullableString(rs, "contract_number"),
                    nullableRate(rs, "discount_rate")))
        }

    /// `NULL` を `None` として読む
    def nullableString(rs: ResultSet, column: String): Option[String] \ IO =
        let value = rs.getString(column);
        if (Object.isNull(value)) None else Some(value)

    /// 割引率を万分率の整数として読む
    def nullableRate(rs: ResultSet, column: String): Option[Int32] \ IO =
        let value = rs.getBigDecimal(column);
        if (Object.isNull(value))
            None
        else
            Some(value.movePointRight(4).intValue())

    /// 1 件を引く
    def selectOne(sql: String, key: String): Option[Shipper] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, sql, stmt -> {
            stmt.setString(1, key);
            let rs = stmt.executeQuery();
            let found = if (rs.next()) decode(rs) else None;
            rs.close();
            found
        })

    /// 全件を引く
    def selectAll(): List[Shipper] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectAllSql(), stmt -> {
            let rs = stmt.executeQuery();
            let rows = collect(rs, Nil);
            rs.close();
            List.reverse(rows)
        })

    ///
    /// 絞り込んで引く。
    ///
    /// 検索語は小文字へ揃え、`LIKE` のメタ文字を打ち消してから前後に `%` を付ける。
    ///
    def selectMatching(keyword: String): List[Shipper] \ Tx + IO =
        let pattern = SharedDbSql.likePattern(keyword);
        let conn = Tx.connection();
        withPreparedStatement(conn, searchSql(), stmt -> {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            let rs = stmt.executeQuery();
            let rows = collect(rs, Nil);
            rs.close();
            List.reverse(rows)
        })

    ///
    /// `ResultSet` を再帰的に読み取る
    def collect(rs: ResultSet, acc: List[Shipper]): List[Shipper] \ IO =
        if (rs.next())
            match decode(rs) {
                case Some(s) => collect(rs, s :: acc)
                // 読めない行は一覧から落とす。1 行の破損で画面全体を落とさない
                case None    => collect(rs, acc)
            }
        else
            acc

    ///
    /// `ShipperRepo` 効果に JDBC 実装を注入する。
    ///
    /// 合成ルートとテストからのみ適用する（arch-lint 規約 5）。
    ///
    pub def withJdbcShipperRepo(f: Unit -> a \ ef + ShipperRepo): a \ (ef - ShipperRepo) + Tx + IO =
        run {
            f()
        } with handler ShipperRepo {
            def save(shipper, k) = { insert(shipper); k() }
            def findByEmail(email, k) = k(selectOne(selectByEmailSql(), String.toLowerCase(email)))
            def findByShipperId(shipperId, k) = k(selectOne(selectByShipperIdSql(), shipperId))
            def findAll(k) = k(selectAll())
            def search(keyword, k) = k(selectMatching(keyword))
        }
}
