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
    /// **Estimation と Routing の貨物種別の語彙が一致すること**（IT8 レビュー・architect）。
    ///
    /// `EstimationWiring.searchRoutes` は Estimation の
    /// `cargoKindToPersisted` が返す文字列を、Routing の
    /// `capabilityFromPersisted` へ渡す。**両者がたまたま同じ綴りだから動いている**。
    ///
    /// Estimation に種別を 1 つ足すと（たとえば「大型貨物」）、
    /// `capabilityFromPersisted` が `None` を返し、`searchRoutes` は `Nil` を返し、
    /// 画面は「希望期限に間に合う経路がありません」と表示する。
    /// **コンパイルも `arch-lint` も統合テストも通ったまま、その種別の見積だけが黙って死ぬ**。
    ///
    /// IT7 の `booking_status = 'ROUTE_PROPOSED'` と同型の欠陥である
    /// （[既知の穴](../../../../docs/design/arch_lint_rules.md)）。合成ルートに置いた
    /// 翻訳は `arch-lint` の対象外であり、**これを固定できるのはテストだけ**である。
    ///
    /// この式が偽になる入力: 片方の列挙に値を足して他方に足さない実装。
    ///
    @Test
    def testCargoVocabulariesAgreeAcrossContexts(): Unit \ Assert =
        let unmapped = EstimationModel.allCargoKinds()
            |> List.map(EstimationModel.cargoKindToPersisted)
            |> List.filter(v -> Option.isEmpty(RoutingModel.capabilityFromPersisted(v)));
        Assert.assertEq(expected = Nil, unmapped)

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
    /// # ルート表から生成する（IT8 レビュー・architect / tester）
    ///
    /// IT8 の当初は 18 件を手で並べていた。**その時点で追加したばかりの
    /// `/estimates` 系 3 本が漏れていた**——「全ルート」と宣言しながら、
    /// 最も新しい BC だけが対象外という状態である。
    ///
    /// `appRoutes` から GET を列挙し、パス変数はサンプル値の表で埋める。
    /// **表に無いパス変数が現れたらテスト自体を失敗させる**ため、
    /// BC を足したときにテストが黙って古くなることがない。
    ///
    @Test
    def testEveryGetRouteResponds(): Unit \ Assert + IO =
        TestApp.withApp("wiring_all_routes", p -> {
            let paths = withRoutes(routes ->
                routes
                    |> List.filter(r -> routeKey(r) |> String.startsWith(prefix = "GET "))
                    |> List.map(concretePath));
            // **表に無いパス変数を検出する**。埋められなければ `{` が残る
            let unresolved = paths |> List.filter(String.contains(substr = "{"));
            Assert.assertEq(expected = Nil, unresolved);
            let broken = paths |> List.filter(path -> {
                let code = SharedHttpTestClient.status(SharedHttpTestClient.get(p, path));
                // 500 は配線の漏れ。それ以外（200・302・403・404）は認可と存在の話
                code >= 500
            });
            Assert.assertEq(expected = Nil, broken)
        })

    ///
    /// パス変数にサンプル値を入れる。
    ///
    /// 値が存在する必要はない（404 でよい）。見たいのは**応答が返ること**である。
    /// 埋めずに `{bookingId}` のまま叩くと 404 の意味が変わる。
    ///
    def concretePath(r: Route[AppComposition.AppEff]): String =
        let path = routeKey(r) |> String.dropLeft(4);
        path
            |> String.replace(src = "{bookingId}", dst = "BK-00000001")
            |> String.replace(src = "{trackingNumber}", dst = "TRK-20260803-0001")
            |> String.replace(src = "{voyageNumber}", dst = "V-0001")
            |> String.replace(src = "{estimateId}", dst = "EST-00000001")
}
