///
/// ヘルスチェックのルートハンドラ。
///
/// Actuator 相当が存在しないため自作する（docs/design/non_functional.md 3.4）。
///
/// - `/health/live`: プロセス応答のみ。**DB を見ない**（DB 障害時の再起動ループを防ぐため）
/// - `/health/ready`: DB 疎通を検査する
///
mod SharedHealthRoutes {
    import java.lang.Thread

    use SharedHttp.{Request, Response, json, queryParam}
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

    ///
    /// **診断用**。指定ミリ秒だけ待ってから応答する。
    ///
    /// 同時実行の上限とタイムアウトは、**処理に時間がかかる経路が無いと
    /// 検証できない**。既存のルートはどれも即座に返るため、上限へ到達させる
    /// ことも上限時間を超えることもできない。
    ///
    /// **本番のルーティング表には載せない**（`AppComposition.appRoutes` は
    /// 本ルートを含まず、`startServerWith` の診断フラグが真のときだけ足す）。
    /// 誰でも呼べる待機の口は、それ自体が最も安価な攻撃手段になる。
    ///
    /// 待ち時間は 5 秒で頭打ちにする。テストの誤りでビルドが止まらないようにする。
    ///
    pub def slow(req: Request): Response \ IO =
        let requested = queryParam(req, "ms")
            |> Option.flatMap(Int32.fromString)
            |> Option.getWithDefault(0);
        let waitMs = Int32.min(5000, Int32.max(0, requested));
        Thread.sleep(Int32.toInt64(waitMs));
        json(200, "{\"status\":\"UP\",\"sleptMs\":${waitMs}}")
}
