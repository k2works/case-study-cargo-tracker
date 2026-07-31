///
/// 認証データのテストフィクスチャ。
///
/// パスワードハッシュは**テスト実行時に生成する**。ハードコードすると、
/// コスト設定を変えたときにテストが実態と乖離する。
///
mod AuthFixtures {
    use SharedBcryptPassword.withBcryptPassword
    use SharedDbPool.DataSource
    use SharedDbPool.{withConnection, withStatement}
    use SharedPassword.Password

    /// 有効な利用者のログイン名
    pub def activeUsername(): String = "yamada"

    /// 無効化された利用者のログイン名
    pub def disabledUsername(): String = "taro"

    /// 全利用者で共通のパスワード
    pub def samplePassword(): String = "correct horse battery staple"

    ///
    /// 利用者を投入する。
    ///
    /// `yamada`（営業担当者・有効）と `taro`（荷役作業員・無効化済み）の 2 名。
    /// 無効化されたアカウントの案内（US26 受入基準 5）を検証するために後者が要る。
    ///
    pub def seedUsers(ds: DataSource): Unit \ IO =
        let hash = withBcryptPassword(() -> Password.hash(samplePassword()));
        withConnection(ds, conn -> withStatement(conn, stmt -> {
        discard stmt.executeUpdate(
            "INSERT INTO users (id, username, email, password, enabled) VALUES " +
            "(1, '${activeUsername()}', 'yamada@example.com', '${hash}', TRUE)");
        discard stmt.executeUpdate("INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_SALES')");
        discard stmt.executeUpdate(
            "INSERT INTO users (id, username, email, password, enabled) VALUES " +
            "(2, '${disabledUsername()}', 'taro@example.com', '${hash}', FALSE)");
        discard stmt.executeUpdate("INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_HANDLER')")
        }))
}
