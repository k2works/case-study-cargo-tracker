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

    ///
    /// 荷主ロールの利用者。**特定の荷主に紐づく**。
    ///
    /// 可視範囲（観点表の軸 6）の検証に要る。荷主 ID はテストが
    /// `linkShipper` で後から結び付ける（登録した荷主の ID は実行時に決まるため）。
    ///
    pub def shipperUsername(): String = "kimura"

    /// もう 1 人の荷主。**他人の予約が見えないこと**の検証に要る
    pub def otherShipperUsername(): String = "tanaka"

    /// 経路設計者。航海スケジュールの登録・更新（US24・US25）の検証に要る
    pub def routerUsername(): String = "router"

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
        discard stmt.executeUpdate("INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_HANDLER')");
        discard stmt.executeUpdate(
            "INSERT INTO users (id, username, email, password, enabled) VALUES " +
            "(4, '${shipperUsername()}', 'kimura@example.com', '${hash}', TRUE)");
        discard stmt.executeUpdate("INSERT INTO user_roles (user_id, role) VALUES (4, 'ROLE_SHIPPER')");
        discard stmt.executeUpdate(
            "INSERT INTO users (id, username, email, password, enabled) VALUES " +
            "(5, '${otherShipperUsername()}', 'tanaka@example.com', '${hash}', TRUE)");
        discard stmt.executeUpdate("INSERT INTO user_roles (user_id, role) VALUES (5, 'ROLE_SHIPPER')");
        discard stmt.executeUpdate(
            "INSERT INTO users (id, username, email, password, enabled) VALUES " +
            "(6, '${routerUsername()}', 'router@example.com', '${hash}', TRUE)");
        discard stmt.executeUpdate("INSERT INTO user_roles (user_id, role) VALUES (6, 'ROLE_ROUTER')")
        }))

    ///
    /// 荷主ロールの利用者を、実在する荷主へ結び付ける。
    ///
    /// **荷主コードで指定する**。荷主 ID は登録時に採番され画面に出ないため、
    /// テストが利用者と同じ経路で得られるのは荷主コードだけである
    /// （IT4 で「一覧に出ている値だけで予約が完了する」形にした経緯と同じ）。
    /// ID への変換は SQL に任せる。
    ///
    /// 運用では利用者登録時に紐付けが決まる（画面から設定する機能は無い）。
    ///
    pub def linkShipper(ds: DataSource, username: String, shipperCode: String): Unit \ IO =
        withConnection(ds, conn -> withStatement(conn, stmt ->
            discard stmt.executeUpdate(
                "UPDATE users SET shipper_id = " +
                "(SELECT shipper_id FROM shipper WHERE shipper_code = '${shipperCode}') " +
                "WHERE username = '${username}'")))
}
