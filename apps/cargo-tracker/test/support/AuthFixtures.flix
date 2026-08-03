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

    ///
    /// 2 人目の有効な利用者のログイン名。
    ///
    /// 同時セッション数 1（ADR-0003）のため、**同じ利用者では 2 つのセッションを
    /// 同時に持てない**。「他セッションの CSRF トークン」を検証するには
    /// 別の利用者が要る（IT3 レビュー H17）。
    ///
    pub def secondUsername(): String = "suzuki"

    /// 無効化された利用者のログイン名
    pub def disabledUsername(): String = "taro"

    /// 全利用者で共通のパスワード
    pub def samplePassword(): String = "correct horse battery staple"

    ///
    /// 利用者を投入する。
    ///
    /// `yamada`（営業担当者・有効）・`suzuki`（営業担当者・有効）・
    /// `taro`（荷役作業員・無効化済み）の 3 名。
    ///
    /// `taro` は無効化されたアカウントの案内（US26 受入基準 5）の検証に、
    /// `suzuki` は同時に 2 セッションを成立させる検証に要る。
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
            "(3, '${secondUsername()}', 'suzuki@example.com', '${hash}', TRUE)");
        discard stmt.executeUpdate("INSERT INTO user_roles (user_id, role) VALUES (3, 'ROLE_SALES')");
        discard stmt.executeUpdate(
            "INSERT INTO users (id, username, email, password, enabled) VALUES " +
            "(2, '${disabledUsername()}', 'taro@example.com', '${hash}', FALSE)");
        discard stmt.executeUpdate("INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_HANDLER')")
        }))
}
