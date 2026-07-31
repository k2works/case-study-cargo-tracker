///
/// 開発用サンプルデータの投入。
///
/// **業務データのシードをマイグレーションに置かない**のは、テストが自前で用意する
/// データと衝突するためである（IT1 で実際に一意制約違反が発生した）。
/// マイグレーションに置くのはマスタデータ（`location`）までとし、
/// 業務データは開発起動時にのみ投入する。
///
mod SharedDbDevSeed {
    use SharedBcryptPassword.withBcryptPassword
    use SharedDbPool.DataSource
    use SharedDbPool.{withConnection, withStatement}
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
        withConnection(ds, conn -> withStatement(conn, stmt -> {
        discard stmt.executeUpdate(
            "MERGE INTO users (id, username, email, password, enabled) KEY (username) VALUES " +
            "(1, 'yamada', 'yamada@example.com', '${hash}', TRUE)");
        discard stmt.executeUpdate(
            "MERGE INTO user_roles (user_id, role) KEY (user_id, role) VALUES (1, 'ROLE_SALES')");
        discard stmt.executeUpdate(
            "MERGE INTO users (id, username, email, password, enabled) KEY (username) VALUES " +
            "(2, 'admin', 'admin@example.com', '${hash}', TRUE)");
        discard stmt.executeUpdate(
            "MERGE INTO user_roles (user_id, role) KEY (user_id, role) VALUES (2, 'ROLE_ADMIN')")
        }))

    /// サンプルの追跡データを投入する（冪等）
    pub def seed(ds: DataSource): Unit \ IO =
        withConnection(ds, conn -> withStatement(conn, stmt -> {
        discard stmt.executeUpdate(
            "MERGE INTO tracking_activity " +
            "(id, tracking_number, booking_id, transport_status, estimated_arrival) " +
            "KEY (tracking_number) VALUES " +
            "(1, 'TRK-20260803-0001', 'BK-0001', 'ONBOARD_CARRIER', " +
            "TIMESTAMP '2026-08-10 00:00:00')");
        discard stmt.executeUpdate("DELETE FROM tracking_handling_event WHERE tracking_id = 1");
        discard stmt.executeUpdate(
            "INSERT INTO tracking_handling_event " +
            "(tracking_id, event_type, event_time, location_unlocode, voyage_number) VALUES " +
            "(1, 'RECEIVE', TIMESTAMP '2026-08-02 14:00:00', 'JPTYO', NULL), " +
            "(1, 'LOAD', TIMESTAMP '2026-08-03 09:15:00', 'JPTYO', 'V0042')")
        }))
}
