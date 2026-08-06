///
/// HTTP ランタイムの防御（TS12）。
///
/// [バックエンドアーキテクチャ](../../../../../../docs/design/architecture_backend.md)
/// 「構成方針」が定めながら IT1 以降**未実装のまま**だった 3 項目を実装する。
///
/// | 項目 | 決定 |
/// | :--- | :--- |
/// | 同時実行の上限 | セマフォで制限する（既定 200） |
/// | バックプレッシャ | 上限に達したら `503` + `Retry-After` |
/// | タイムアウト | 上限時間を超えたら `504` |
///
/// # なぜ上限が要るか
///
/// Executor は仮想スレッドであり、**スレッドは無制限に生成できる**。
/// 一方 JDBC のコネクションは 20 本しかない。上限を設けないと、
/// コネクション待ちのスレッドが積み上がり、レイテンシが青天井になる。
/// キューへ無制限に積むより、**明示的に拒否する方が復旧が速い**。
///
/// # 設定で変えられるようにする理由
///
/// 既定の 200 に対して 201 本を同時に投げるテストは、CI の資源を食う割に
/// 不安定である。上限を設定として外へ出し、テストは 1〜2 で確かめる。
/// **設定を通す経路は本番と同じ**であり、テスト専用の分岐を作らない。
///
mod SharedHttpRuntimeGuard {
    import java.lang.System
    import java.util.concurrent.Semaphore

    use SharedHttp.Response

    ///
    /// ランタイムの上限。
    ///
    /// - `maxConcurrent`: 同時に処理するリクエスト数の上限
    /// - `timeoutSeconds`: 1 リクエストの処理時間の上限
    ///
    pub enum RuntimeLimits({
        maxConcurrent = Int32,
        timeoutSeconds = Int32
    })

    /// 上限を組み立てる
    pub def limits(maxConcurrent: Int32, timeoutSeconds: Int32): RuntimeLimits =
        RuntimeLimits.RuntimeLimits({
            maxConcurrent = Int32.max(1, maxConcurrent),
            timeoutSeconds = Int32.max(1, timeoutSeconds)
        })

    pub def maxConcurrentOf(l: RuntimeLimits): Int32 =
        let RuntimeLimits.RuntimeLimits(r) = l; r#maxConcurrent

    pub def timeoutSecondsOf(l: RuntimeLimits): Int32 =
        let RuntimeLimits.RuntimeLimits(r) = l; r#timeoutSeconds

    ///
    /// 既定の上限。
    ///
    /// 設計どおり同時実行 200・タイムアウト 30 秒。環境変数で上書きできる。
    ///
    /// **未設定・解釈できない値は既定へ倒す**。ここは「未知の値を既定へ
    /// 倒さない」（貨物種別・温度単位）とは扱いが逆である。あちらは
    /// **業務上の意味**を持つ値であり、誤読すると危険物が一般貨物になる。
    /// こちらは**運用上の調整値**であり、読めないときに起動できない方が困る。
    ///
    pub def defaultLimits(): RuntimeLimits \ IO =
        limits(envInt("APP_MAX_CONCURRENT_REQUESTS", 200),
               envInt("APP_REQUEST_TIMEOUT_SECONDS", 30))

    def envInt(name: String, fallback: Int32): Int32 \ IO =
        let value = System.getenv(name);
        if (Object.isNull(value))
            fallback
        else
            Int32.fromString(value) |> Option.getWithDefault(fallback)

    ///
    /// **同時実行の枠を通さない経路**（ヘルスチェック）。
    ///
    /// 過負荷で liveness が `503` を返すと、オーケストレータは
    /// 「混んでいるだけで生きている」インスタンスを**再起動する**。
    /// 負荷が高いときにインスタンスが落ちるため、事態は必ず悪化する。
    ///
    /// IT3 で「`/health/live` が DB を見ると DB 障害時に再起動ループになる」と
    /// 学び、`TxMode.NoTx` で防いだ（レビュー H4）。**同じ障害が、DB ではなく
    /// セマフォ経由で再現する**——防御を足したことが新しい単一障害点を作る
    /// （IT7 レビュー・architect 指摘）。
    ///
    /// `/health/slow` は含めない。**診断用であり本番の表には載らない**うえ、
    /// 上限そのものを検証するために枠を通る必要がある。
    ///
    pub def isProbePath(requestPath: String): Bool =
        requestPath == "/health/live" or requestPath == "/health/ready"

    ///
    /// 再試行の目安（秒）。
    ///
    /// **拒否だけして目安を示さない**と、クライアントは即座に再送して
    /// 過負荷を増幅させる。短すぎても同じことが起きるため 1 秒とする。
    ///
    pub def retryAfterSeconds(): Int32 = 1

    ///
    /// 同時実行の上限に達したときの応答。
    ///
    /// **本文に内部情報を載せない**。上限値を返すと、攻撃者に
    /// 「何本送れば落とせるか」を教えることになる（観点表の軸 6）。
    ///
    pub def tooBusy(): Response =
        SharedHttpAuth.withHeader(
            SharedHttp.htmlWithStatus(503, errorPage("混み合っています",
                "ただいまアクセスが集中しています。" +
                "${retryAfterSeconds()} 秒ほど待ってから、もう一度お試しください")),
            "Retry-After", "${retryAfterSeconds()}")

    ///
    /// 処理時間の上限を超えたときの応答。
    ///
    /// 例外のメッセージやスタックトレースを載せない。実装の構造が外から読める。
    ///
    pub def timedOut(): Response =
        SharedHttp.htmlWithStatus(504, errorPage("処理に時間がかかりました",
            "時間内に処理が終わりませんでした。時間をおいて開き直してください。" +
            "繰り返す場合は、開いていた画面と予約番号・航海番号を添えて管理者へご連絡ください"))

    ///
    /// 防御が返す画面。
    ///
    /// **`text/plain` の 1 行で返さない**。業務利用者から見れば「画面が壊れた」であり、
    /// ナビもリンクも無い行き止まりになる。本システムは 404 でも認可エラーでも
    /// 画面を返す判断をしており、ここだけ例外にする理由がない
    /// （IT7 レビュー・user-rep 指摘）。
    ///
    /// **`Layout` を使わない**。レイアウトはセッション（`Principal`）を要求するが、
    /// 防御はセッションを引く前——認可より手前——で働く。DB を引けない状況こそ
    /// 防御が要る場面であり、ここでセッションを引くと防御自体が詰まりうる。
    ///
    /// **文言に内部情報を載せない**。上限値を返すと「何本送れば落とせるか」を
    /// 教えることになる（観点表の軸 6）。
    ///
    def errorPage(title: String, message: String): String =
        "<!DOCTYPE html><html lang=\"ja\"><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<title>${title} | CargoTracker</title>" +
        "<link rel=\"stylesheet\" href=\"/static/css/bootstrap.min.css\"></head>" +
        "<body><main class=\"container-fluid py-4\">" +
        "<div class=\"row\"><div class=\"col-md-8 offset-md-2\">" +
        "<h1 class=\"h4 mb-3\">${title}</h1>" +
        "<div class=\"alert alert-warning\" role=\"alert\">${message}</div>" +
        "<a class=\"btn btn-outline-primary\" href=\"/\">ダッシュボードへ戻る</a>" +
        "</div></div></main></body></html>"

    ///
    /// 同時実行を制限する枠。
    ///
    /// `java.util.concurrent.Semaphore` をそのまま使う。**公平性を要求しない**
    /// （`new Semaphore(n)`）のは、公平モードが待ち行列を作るためである。
    /// ここで欲しいのは「待たせる」ではなく「即座に断る」であり、
    /// `tryAcquire` は待たずに失敗を返す。
    ///
    pub type alias Permits = Semaphore

    /// 上限ぶんの枠を用意する
    pub def createPermits(l: RuntimeLimits): Permits \ IO =
        new Semaphore(maxConcurrentOf(l))

    ///
    /// 枠を確保できたか。**待たない**。
    ///
    /// 待つ実装にすると、上限は「同時実行数」ではなく「待ち行列の長さ」を
    /// 制限することになり、レイテンシが青天井になる問題が形を変えて残る。
    ///
    pub def tryEnter(p: Permits): Bool \ IO =
        p.tryAcquire()

    ///
    /// 枠を返す。
    ///
    /// **確保した経路すべてから必ず呼ぶ**。1 つでも漏れると枠が減り続け、
    /// 最終的にすべてのリクエストが `503` になる。
    /// `RuntimeGuardTest.testPermitsAreReleasedAfterRejection` が
    /// 「拒否の後に後続が成功すること」を固定しているのはこのためである。
    ///
    /// **既知の限界（IT7 レビュー・programmer / architect 指摘）**:
    /// タイムアウトで枠を返した時点で、**処理はまだ走っている可能性がある**。
    /// `Future.cancel(true)` は割り込みを要求するだけであり、JDBC の待機や
    /// 純粋な計算ループは割り込みに応答しない。したがって
    /// **タイムアウトが多発する状況では「同時実行 ≤ N」が破れる**——
    /// 枠は空いているのに、実処理は N + 滞留ぶんが走る。
    ///
    /// これは防御が最も要る場面でだけ効かないという意味で重い。
    /// 本 IT では**限界を明示するに留める**。恒久対策は 2 案あり、
    /// いずれも IT8 の負荷試験（TS12b）の実測を見てから決める。
    ///
    /// 1. 枠の解放をタスク側の末尾へ移し「実処理が終わったら返す」意味に変える
    ///    （応答は先に返るが枠は残る。滞留が可視化される代わりに枠が枯れやすい）
    /// 2. `Statement.setQueryTimeout` を併用し、DB 側で確実に切る
    ///
    pub def leave(p: Permits): Unit \ IO =
        p.release()
}
