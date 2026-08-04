///
/// 合成ルートの配線が BC 別に畳まれていること（IT8・TS14）。
///
/// # このテストが存在する理由
///
/// IT7 のレビュー（architect）で、BC を 1 本足すたびに 4 箇所を同時編集する必要があり、
/// うち 1 箇所（`runWithoutDb`）は入れ子が深いと指摘された。
/// 残る BC が入ると 11 段になるという見積もりだった。
///
/// **触る箇所の数（4）は減らせない**。効果は静的な型であり、ハンドラのリストを
/// 畳み込むことはできない。減らせるのは**1 箇所あたりの分量**である。
///
/// 本モジュールが固定するのは「畳まれた形が崩れていないこと」ではなく、
/// **畳んだ結果として保たれるべき振る舞い**である。形はレビューで見ればよいが、
/// 振る舞いは実行しないと分からない。
///
/// # 何を見るか
///
/// [IT8 計画](../../../../docs/development/iteration_plan-8.md) のタスク 0.1 で
/// 「実行経路が変わったとき何が壊れるか」を数えた。結論は次のとおりだった。
///
/// > 1・2・3・6 は**コンパイルで捕まる**（効果が型に現れる設計の利点）。
/// > 実行時に静かに壊れうるのは **5（Cookie の配線）**だけである。
///
/// したがって本モジュールは **`NoTx` の経路**（既定値へ倒れていないか）と
/// **効果の持ち上げ**（実行されていないか）を見る。Cookie の配線は
/// `LoginHttpTest` が既に固定しており、そちらを回帰として使う。
///
mod WiringTest {
    use AppComposition.appRoutes
    use SharedDbPool.{create, h2Config, close}
    use SharedHttpRouter.{Route, TxMode, routeTxMode, routeKey, routeRole}
    use SharedHttp.RequiredRole

    def withRoutes(f: List[Route[AppComposition.AppEff]] -> a \ ef): a \ ef + IO =
        let ds = create(h2Config("wiring_check"));
        let result = f(appRoutes(ds));
        close(ds);
        result

    ///
    /// **`NoTx` のルートは `Anonymous` に限られること**。
    ///
    /// 認可の判定にはセッションの参照（= DB アクセス）が要る。`NoTx` なのに
    /// ロールを要求するルートを書くと、`runWithoutDb` が `Session` を要求されて落ちる。
    ///
    /// `AppRoutesTest` が同じことを見ているが、**BC 別に畳んだ後も成り立つこと**を
    /// ここで独立に固定する。畳み込みで `SharedWiring.withoutDb` の適用が漏れると、
    /// この不変条件が静かに破れる。
    ///
    /// この式が偽になる入力: `/health/live` に `RoleAnyOf` を与えた実装。
    ///
    @Test
    def testNoTxRoutesRemainAnonymous(): Unit \ Assert + IO =
        withRoutes(routes -> {
            let violations = routes
                |> List.filter(r -> routeTxMode(r) == TxMode.NoTx)
                |> List.filter(r -> routeRole(r) != RequiredRole.Anonymous)
                |> List.map(routeKey);
            Assert.assertEq(expected = Nil, violations)
        })

    ///
    /// **`NoTx` のルートが実際に DB を使わずに応答すること**。
    ///
    /// `runWithoutDb` のハンドラは `bug!` で落とす設計であり、**既定値を返さない**。
    /// 畳み込みの過程で「落とす」から「既定値を返す」へ変わると、
    /// `NoTx` と宣言したルートが DB を要求していたときに**未認証として静かに通る**。
    ///
    /// この式が偽になる入力: `WiringGuard.noDbViolation` が `bug!` ではなく
    /// `None` や `Nil` を返す実装。`/health/live` は落ちずに 200 を返し続けるため、
    /// **この経路では気付けない**——だからこそ `bug!` である必要がある。
    ///
    @Test
    def testNoTxRouteRespondsWithoutDb(): Unit \ Assert + IO =
        TestApp.withApp("wiring_no_tx", p -> {
            SharedHttpTestClient.assertStatus(200, SharedHttpTestClient.get(p, "/health/live"))
        })

    ///
    /// **全ルートが 1 度ずつ通ること**（畳み込みの回帰）。
    ///
    /// ハンドラの適用順が変わって効果が解決されない場合、そのルートだけが
    /// 実行時に落ちる。**表の全ルートを叩けば、落ちるルートが特定できる**。
    ///
    /// 認可で弾かれてよい（302・403 は正常）。見たいのは
    /// **「応答が返ること」**であり、内容ではない。応答が返らない・500 になる
    /// ルートがあれば、それが配線の漏れである。
    ///
    /// この式が偽になる入力: いずれかの BC の `withDb` を適用し忘れた実装
    /// （その BC のポートを使うルートだけが 500 になる）。
    ///
    @Test
    def testEveryGetRouteResponds(): Unit \ Assert + IO =
        TestApp.withApp("wiring_all_routes", p -> {
            // パス変数を含むルートは値を埋める。表から機械的に生成すると
            // `{bookingId}` がそのままパスになり 404 の意味が変わる
            let paths =
                "/" :: "/login" :: "/health/live" :: "/health/ready" ::
                "/public/tracking" :: "/public/tracking/TRK-20260803-0001" ::
                "/shippers" :: "/shippers/new" :: "/shippers/new/corporate-fields" ::
                "/bookings" :: "/bookings/new" :: "/bookings/new/cargo-type-fields" ::
                "/bookings/BK-00000001" :: "/bookings/BK-00000001/route" ::
                "/voyages" :: "/voyages/new" :: "/voyages/leg-fields" ::
                "/voyages/V-0001/edit" :: Nil;
            let broken = paths |> List.filter(path -> {
                let code = SharedHttpTestClient.status(SharedHttpTestClient.get(p, path));
                // 500 は配線の漏れ。それ以外（200・302・403・404）は認可と存在の話
                code >= 500
            });
            Assert.assertEq(expected = Nil, broken)
        })
}
