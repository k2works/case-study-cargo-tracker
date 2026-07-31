///
/// `UserRepo` 効果の JDBC 実装（本番アダプタ）。
///
/// SQL は本モジュール内の定数として持つ。変数の埋め込みは行わない（arch-lint 規約 9）。
///
mod SharedJdbcUserRepo {
    import java.sql.ResultSet
    import java.sql.Timestamp
    import java.time.Instant

    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx
    use SharedSecurity.roleFromPersisted
    use SharedUserRepo.UserRepo
    use SharedUserRepo.{UserRecord, userRecord}

    /// ログイン失敗でロックするまでの回数（非機能要件 4.1）
    def maxFailedAttempts(): Int32 = 5

    /// ロックの継続時間（分）
    def lockoutMinutes(): Int64 = 30i64

    /// 利用者の検索
    def selectUserSql(): String =
        "SELECT u.id, u.username, u.password, u.enabled, u.failed_attempts, " +
        "u.locked_until, r.role " +
        "FROM users u " +
        "JOIN user_roles r ON r.user_id = u.id " +
        "WHERE u.username = ?"

    /// 失敗回数の加算
    def incrementFailureSql(): String =
        "UPDATE users SET failed_attempts = failed_attempts + 1, " +
        "updated_at = CURRENT_TIMESTAMP WHERE id = ?"

    /// ロックの設定
    def applyLockSql(): String =
        "UPDATE users SET locked_until = ?, failed_attempts = 0, " +
        "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND failed_attempts >= ?"

    /// 失敗回数のリセット
    def resetFailuresSql(): String =
        "UPDATE users SET failed_attempts = 0, locked_until = NULL, " +
        "updated_at = CURRENT_TIMESTAMP WHERE id = ?"

    /// 行を `UserRecord` へデコードする
    def decodeUser(rs: ResultSet): Option[UserRecord] \ IO =
        let id = rs.getLong("id");
        let name = rs.getString("username");
        let hash = rs.getString("password");
        let isEnabled = rs.getBoolean("enabled");
        let failures = rs.getInt("failed_attempts");
        let lockedUntil = rs.getTimestamp("locked_until");
        let lockedMillis = if (Object.isNull(lockedUntil)) 0i64 else lockedUntil.getTime();
        let roleValue = rs.getString("role");
        // 未知のロールを持つ利用者は認証できない。既定ロールへ倒すと
        // 権限のない利用者に権限を与えることになる
        Option.map(r -> userRecord(id, name, hash, isEnabled, r, failures, lockedMillis),
                   roleFromPersisted(roleValue))

    /// 利用者を引く
    def find(name: String): Option[UserRecord] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectUserSql(), stmt -> {
            stmt.setString(1, name);
            let rs = stmt.executeQuery();
            let found = if (rs.next()) decodeUser(rs) else None;
            rs.close();
            found
        })

    ///
    /// ログイン失敗を記録する。
    ///
    /// 加算してから「上限に達していればロックする」を 1 回の UPDATE で行う。
    /// 読み取ってから判定して書き戻すと、同時ログイン試行で失敗回数を数え落とす。
    ///
    def recordFailure(id: Int64): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, incrementFailureSql(), stmt -> {
            stmt.setLong(1, id);
            discard stmt.executeUpdate()
        });
        let until = new Timestamp(Instant.now().toEpochMilli() + lockoutMinutes() * 60000i64);
        withPreparedStatement(conn, applyLockSql(), stmt -> {
            stmt.setTimestamp(1, until);
            stmt.setLong(2, id);
            stmt.setInt(3, maxFailedAttempts());
            discard stmt.executeUpdate()
        })

    /// 失敗回数をリセットする
    def resetFailures(id: Int64): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, resetFailuresSql(), stmt -> {
            stmt.setLong(1, id);
            discard stmt.executeUpdate()
        })

    ///
    /// `UserRepo` 効果に JDBC 実装を注入する。
    ///
    /// 合成ルートとテストからのみ適用する（arch-lint 規約 5）。
    ///
    pub def withJdbcUserRepo(f: Unit -> a \ ef + UserRepo): a \ (ef - UserRepo) + Tx + IO =
        run {
            f()
        } with handler UserRepo {
            def findByUsername(name, k) = k(find(name))
            def recordFailure(id, k) = {
                recordFailure(id);
                k()
            }
            def resetFailures(id, k) = {
                resetFailures(id);
                k()
            }
        }
}
