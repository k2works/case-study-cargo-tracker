///
/// 開発用サンプルデータの投入。
///
/// **業務データのシードをマイグレーションに置かない**のは、テストが自前で用意する
/// データと衝突するためである（IT1 で実際に一意制約違反が発生した）。
/// マイグレーションに置くのはマスタデータ（`location`）までとし、
/// 業務データは開発起動時にのみ投入する。
///
mod SharedDbDevSeed {
    import java.sql.Connection
    use SharedBcryptPassword.withBcryptPassword
    use SharedDbPool.DataSource
    use SharedDbPool.{withConnection, withStatement, withPreparedStatement}
    use SharedPassword.Password

    ///
    /// 開発用の利用者を投入する（冪等）。
    ///
    /// パスワードは起動のたびにハッシュ化する。ハッシュをソースへ書くと、
    /// コスト設定を変えたときに開発環境だけ古い設定のまま残る。
    ///
    /// **本パスワードは開発用である**。本番の利用者は運用手順で登録する。
    ///
    pub def seedUsers(ds: DataSource): Unit \ IO =
        let hash = withBcryptPassword(() -> Password.hash("password"));
        withConnection(ds, conn -> {
            insertUser(conn, hash, 1i64, "yamada", "yamada@example.com", "ROLE_SALES");
            insertUser(conn, hash, 2i64, "admin", "admin@example.com", "ROLE_ADMIN");
            // 荷役作業員（IT11・E2E シナリオ③）。**開発環境に居ないロールは
            // 画面を確かめられない**——荷役の導線は IT10 で入ったが、
            // 開発用シードに該当ロールが 1 人も居なかった
            insertUser(conn, hash, 3i64, "sato", "sato@example.com", "ROLE_HANDLER")
        })

    /// 利用者 1 名を投入する（ハッシュは変数のためプレースホルダで渡す。arch-lint 規約 9）
    def insertUser(conn: Connection, hash: String, id: Int64,
                   name: String, email: String, roleValue: String): Unit \ IO =
        withPreparedStatement(conn, insertUserSql(), stmt -> {
            stmt.setLong(1, id);
            stmt.setString(2, name);
            stmt.setString(3, email);
            stmt.setString(4, hash);
            // `WHERE NOT EXISTS` の照合に使う 5 番目のプレースホルダ
            stmt.setString(5, name);
            discard stmt.executeUpdate()
        });
        withPreparedStatement(conn, insertRoleSql(), stmt -> {
            stmt.setLong(1, id);
            stmt.setString(2, roleValue);
            stmt.setLong(3, id);
            discard stmt.executeUpdate()
        })

    /// 利用者の投入（冪等）
    def insertUserSql(): String =
        "INSERT INTO users (id, username, email, password, enabled) " +
        "SELECT ?, ?, ?, ?, TRUE WHERE NOT EXISTS " +
        "(SELECT 1 FROM users existing WHERE existing.username = ?)"

    /// ロールの投入（冪等）
    def insertRoleSql(): String =
        "INSERT INTO user_roles (user_id, role) SELECT ?, ? WHERE NOT EXISTS " +
        "(SELECT 1 FROM user_roles existing WHERE existing.user_id = ?)"

    /// サンプルの追跡データを投入する（冪等）
    pub def seed(ds: DataSource): Unit \ IO =
        withConnection(ds, conn -> withStatement(conn, stmt -> {
        discard stmt.executeUpdate(
            "INSERT INTO tracking_activity " +
            "(id, tracking_number, booking_id, transport_status, estimated_arrival) " +
            "SELECT 1, 'TRK-20260803-0001', 'BK-0001', 'ONBOARD_CARRIER', " +
            "TIMESTAMP '2026-08-10 00:00:00' WHERE NOT EXISTS " +
            "(SELECT 1 FROM tracking_activity existing " +
            "WHERE existing.tracking_number = 'TRK-20260803-0001')");
        discard stmt.executeUpdate("DELETE FROM tracking_handling_event WHERE tracking_id = 1");
        discard stmt.executeUpdate(
            "INSERT INTO tracking_handling_event " +
            "(tracking_id, event_type, event_time, location_unlocode, voyage_number) VALUES " +
            "(1, 'RECEIVE', TIMESTAMP '2026-08-02 14:00:00', 'JPTYO', NULL), " +
            "(1, 'LOAD', TIMESTAMP '2026-08-03 09:15:00', 'JPTYO', 'V0042')")
        }))
}
