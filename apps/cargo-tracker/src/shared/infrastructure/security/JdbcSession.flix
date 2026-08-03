///
/// `Session` 効果の DB 実装（本番アダプタ）。
///
/// インメモリではなく DB に置くのは、ECS を複数タスクで運用する際に
/// **別タスクが保持するセッションを無効化する手段**が必要になるためである
/// （[ADR-0003](../../../../../../docs/adr/ADR-0003-session-concurrency.md)）。
///
/// SQL は本モジュール内の定数として持つ。変数の埋め込みは行わない（arch-lint 規約 9）。
///
mod SharedJdbcSession {
    import java.sql.Timestamp
    import java.util.UUID

    use SharedClock.Clock
    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx
    use SharedSecurity.{Principal, Role}
    use SharedSecurity.{principal, userId, roleFromPersisted}
    use SharedSession.Session
    use SharedSession.TerminationReason

    /// セッションが存在するか（期限切れを含む）。終了理由の判別に使う
    def existsSessionSql(): String = "SELECT 1 FROM sessions WHERE session_id = ?"

    ///
    /// 最終アクセス時刻と失効時刻の更新。
    ///
    /// [非機能要件](../../../../../../docs/design/non_functional.md) 4.1 は
    /// タイムアウトを「**最終アクセスから**」と定めている。作成時に一度だけ
    /// 失効時刻を決めると絶対タイムアウトになり、連続操作中でも切断される。
    ///
    def touchSessionSql(): String =
        "UPDATE sessions SET last_accessed_at = ?, expires_at = ? WHERE session_id = ?"

    /// セッションの検索（有効期限内のもののみ）
    def selectSessionSql(): String =
        "SELECT s.session_id, s.csrf_token, u.id, u.username, r.role " +
        "FROM sessions s " +
        "JOIN users u ON u.id = s.user_id " +
        "JOIN user_roles r ON r.user_id = u.id " +
        "WHERE s.session_id = ? AND s.expires_at > ?"

    /// セッションの作成
    def insertSessionSql(): String =
        "INSERT INTO sessions " +
        "(session_id, user_id, csrf_token, created_at, last_accessed_at, expires_at) " +
        "VALUES (?, ?, ?, ?, ?, ?)"

    /// 同一利用者の既存セッションの削除（ADR-0003）
    def deleteByUserSql(): String = "DELETE FROM sessions WHERE user_id = ?"

    /// セッション ID による削除
    def deleteByIdSql(): String = "DELETE FROM sessions WHERE session_id = ?"

    ///
    /// セッションのタイムアウト（分）。
    ///
    /// 荷役作業員は港湾・倉庫での連続作業とバーコードスキャンを考慮して 2 時間、
    /// その他は 30 分（[非機能要件](../../../../../../docs/design/non_functional.md) 4.1）。
    ///
    def timeoutMinutes(r: Role): Int64 = match r {
        case Role.Handler => 120i64
        case _            => 30i64
    }

    ///
    /// 推測困難な ID を生成する。
    ///
    /// `UUID.randomUUID()` は内部で `SecureRandom` を使い、122bit のランダム性を持つ。
    /// これを 2 つ連結して 244bit とし、ハイフンを除いた 64 文字にする
    /// （`sessions.session_id` は `VARCHAR(64)`）。
    ///
    /// 予測可能な ID はセッションハイジャックに直結するため、`Random` は使わない。
    ///
    def generateId(): String \ IO =
        let first = UUID.randomUUID().toString();
        let second = UUID.randomUUID().toString();
        String.replace(src = "-", dst = "", first + second)

    ///
    /// 現在時刻。
    ///
    /// `Instant.now()` を直接呼ばず `Clock` 効果から受け取る。
    /// **タイムアウトの判定をテストで固定できるようにするため**であり、
    /// AU-11（ロール別のセッションタイムアウト）はこれなしには検証できない。
    ///
    def now(): Timestamp \ Clock + IO = new Timestamp(Clock.nowMillis())

    /// 現在時刻から指定分だけ後の時刻
    def expiresAt(minutes: Int64): Timestamp \ Clock + IO =
        new Timestamp(Clock.nowMillis() + minutes * 60000i64)

    ///
    /// セッション ID から利用者を引く。
    ///
    /// 有効期限切れのセッションは存在しないものとして扱う（`WHERE expires_at > ?`）。
    /// 期限切れ行の削除は日次のバッチに任せ、参照経路では判定のみ行う。
    ///
    def lookup(sessionId: String): Option[(Principal, String)] \ Clock + Tx + IO =
        let found = selectSession(sessionId);
        // 命中したら失効時刻を延長する（最終アクセスからのタイムアウト）
        match found {
            case Some((p, _)) => touch(sessionId, SharedSecurity.role(p))
            case None         => ()
        };
        found

    /// セッションを引く
    def selectSession(sessionId: String): Option[(Principal, String)] \ Clock + Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectSessionSql(), stmt -> {
            stmt.setString(1, sessionId);
            stmt.setTimestamp(2, now());
            let rs = stmt.executeQuery();
            let found = if (rs.next()) {
                let csrf = rs.getString("csrf_token");
                let id = rs.getLong("id");
                let name = rs.getString("username");
                let roleValue = rs.getString("role");
                match roleFromPersisted(roleValue) {
                    case Some(r) => Some((principal(id, name, r), csrf))
                    // 未知のロールは認証されていないものとして扱う。
                    // 既定ロールへ倒すと、権限のない利用者に権限を与えることになる
                    case None    => None
                }
            } else None;
            rs.close();
            found
        })

    /// 最終アクセス時刻と失効時刻を更新する
    def touch(sessionId: String, r: Role): Unit \ Clock + Tx + IO =
        let conn = Tx.connection();
        let current = now();
        let expiry = expiresAt(timeoutMinutes(r));
        withPreparedStatement(conn, touchSessionSql(), stmt -> {
            stmt.setTimestamp(1, current);
            stmt.setTimestamp(2, expiry);
            stmt.setString(3, sessionId);
            discard stmt.executeUpdate()
        })

    ///
    /// セッションが終了していた理由を判別する。
    ///
    /// 行が残っていれば期限切れ、消えていれば別端末のログイン（または明示的な
    /// ログアウト）による無効化である。同時セッション数 1（ADR-0003）で
    /// 締め出された利用者に「操作がなかったため」と案内しないために要る。
    ///
    def terminationReasonOf(sessionId: String): TerminationReason \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, existsSessionSql(), stmt -> {
            stmt.setString(1, sessionId);
            let rs = stmt.executeQuery();
            let expired = rs.next();
            rs.close();
            if (expired) TerminationReason.Timeout else TerminationReason.Superseded
        })

    ///
    /// セッションを作成する。
    ///
    /// 同一利用者の既存セッションを削除してから作る。同時セッション数 1（ADR-0003）と
    /// セッション固定攻撃対策（ID の再生成）を同時に満たす。
    ///
    def create(p: Principal): (String, String) \ Clock + Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, deleteByUserSql(), stmt -> {
            stmt.setLong(1, userId(p));
            discard stmt.executeUpdate()
        });
        let sessionId = generateId();
        let csrf = generateId();
        let current = now();
        let expiry = expiresAt(timeoutMinutes(SharedSecurity.role(p)));
        withPreparedStatement(conn, insertSessionSql(), stmt -> {
            stmt.setString(1, sessionId);
            stmt.setLong(2, userId(p));
            stmt.setString(3, csrf);
            stmt.setTimestamp(4, current);
            stmt.setTimestamp(5, current);
            stmt.setTimestamp(6, expiry);
            discard stmt.executeUpdate()
        });
        (sessionId, csrf)

    /// セッションを削除する
    def destroy(sessionId: String): Unit \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, deleteByIdSql(), stmt -> {
            stmt.setString(1, sessionId);
            discard stmt.executeUpdate()
        })

    ///
    /// `Session` 効果に DB 実装を注入する。
    ///
    /// `sessionId` はリクエストの Cookie から取り出した値（未認証なら `None`）。
    /// `onSessionChanged` は Cookie を張り替えるためのコールバックで、
    /// レスポンスの組み立ては呼び出し側（合成ルート）が行う。
    ///
    /// 合成ルートとテストからのみ適用する（arch-lint 規約 5）。
    ///
    pub def withJdbcSession(sessionId: Option[String],
                            onSessionChanged: Option[String] -> Unit \ IO,
                            f: Unit -> a \ ef + Session): a \ (ef - Session) + Clock + Tx + IO =
        let loaded = match sessionId {
            case None     => None
            case Some(id) => lookup(id)
        };
        // Cookie はあるのにセッションを引けなかった場合のみ、その理由を判別する
        let reason = match (sessionId, loaded) {
            case (Some(id), None) => Some(terminationReasonOf(id))
            case _                => None
        };
        run {
            f()
        } with handler Session {
            def current(k) = k(Option.map(fst, loaded))
            def csrfToken(k) = k(Option.map(snd, loaded))
            def terminationReason(k) = k(reason)
            def login(p, k) = {
                let (newId, _) = create(p);
                onSessionChanged(Some(newId));
                k(newId)
            }
            def logout(k) = {
                match sessionId {
                    case Some(id) => destroy(id)
                    case None     => ()
                };
                onSessionChanged(None);
                k()
            }
        }
}
