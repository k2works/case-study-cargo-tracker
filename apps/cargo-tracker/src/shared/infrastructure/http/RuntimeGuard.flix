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
            SharedHttp.plain(503, "混み合っています。しばらくしてからお試しください"),
            "Retry-After", "${retryAfterSeconds()}")

    ///
    /// 処理時間の上限を超えたときの応答。
    ///
    /// 例外のメッセージやスタックトレースを載せない。実装の構造が外から読める。
    ///
    pub def timedOut(): Response =
        SharedHttp.plain(504, "処理に時間がかかりすぎました。条件を絞ってお試しください")

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
    pub def leave(p: Permits): Unit \ IO =
        p.release()
}
