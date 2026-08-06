///
/// テストから SQL を直接叩くための支援。
///
/// # なぜ要るか
///
/// **DB の制約が働いていることを確かめる**には、アプリケーションを通さずに
/// 制約を破りにいく必要がある。アプリケーションを通すと集約が先に止めてしまい、
/// その下にある最後の砦を検証できない（IT10・US14 の `uk_cargo_tracking_number`）。
///
/// **通常のテストでは使わない**。状態は利用者と同じ経路で作る——
/// SQL で直接作ると、途中の遷移が壊れていても気付けない（IT4 の教訓）。
/// 本モジュールを使ってよいのは「**制約そのものを検証する**」場合だけである。
///
mod TestSql {
    import java.lang.Throwable
    use SharedDbPool.DataSource
    use SharedDbPool.{withConnection, withStatement}

    ///
    /// 更新が成功したかを返す（例外なら `false`）。
    ///
    /// **例外を握りつぶして `false` にする**のは、検証したいのが
    /// 「拒まれること」そのものだからである。どの例外かまでは見ない——
    /// 制約違反のクラス名はドライバの都合であり、業務の関心事ではない。
    ///
    /// 逆に**成功したときに `true` を返す**ため、呼び出し側は
    /// 「拒まれるはずが通った」を失敗として報告できる。
    ///
    pub def updateSucceeds(ds: DataSource, sql: String): Bool \ IO =
        try {
            withConnection(ds, conn -> withStatement(conn, stmt -> {
                discard stmt.executeUpdate(sql);
                true
            }))
        } catch {
            case _: Throwable => false
        }
}
