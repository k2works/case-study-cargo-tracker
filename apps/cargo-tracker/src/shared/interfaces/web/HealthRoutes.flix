///
/// ヘルスチェックのルートハンドラ。
///
/// Actuator 相当が存在しないため自作する（docs/design/non_functional.md 3.4）。
///
/// - `/health/live`: プロセス応答のみ。**DB を見ない**（DB 障害時の再起動ループを防ぐため）
/// - `/health/ready`: DB 疎通を検査する
///
mod HealthRoutes {
    use SharedHttp.{Request, Response, json}
    use SharedDbPool.DataSource
    use SharedDbSchema.tableExists

    /// Liveness。コンテナの HEALTHCHECK から使用する
    pub def live(_req: Request): Response =
        json(200, "{\"status\":\"UP\"}")

    ///
    /// Readiness。ALB のターゲットグループから使用する。
    ///
    /// DB 疎通とマイグレーション適用済み判定を兼ねて、必須テーブルの存在を確認する。
    ///
    pub def ready(ds: DataSource): Response \ IO =
        if (tableExists(ds, "tracking_activity"))
            json(200, "{\"status\":\"UP\",\"checks\":{\"db\":\"UP\"}}")
        else
            json(503, "{\"status\":\"DOWN\",\"checks\":{\"db\":\"DOWN\"}}")
}
