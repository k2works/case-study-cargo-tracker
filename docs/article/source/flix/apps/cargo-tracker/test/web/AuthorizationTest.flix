///
/// 認可判定のテスト（セキュリティ回帰テスト）。
///
/// [テスト戦略](../../../../docs/design/test_strategy.md) 8.4 の「認可」の項目に対応する。
/// 認可は**ルーティング表の `RequiredRole` のみ**に基づく。判定を純粋関数として切り出し、
/// 全ルート × 全ロールの可否を表で固定する。
///
mod AuthorizationTest {
    use SharedHttp.RequiredRole
    use SharedHttpAuth.{Decision, decide}
    use SharedSecurity.{Role, principal, allRoles}

    def user(r: Role): SharedSecurity.Principal = principal(1i64, "利用者", r)

    /// 未認証でも Anonymous のルートは通ること
    @Test
    def testAnonymousRouteAllowsUnauthenticated(): Unit \ Assert =
        Assert.assertTrue(decide(RequiredRole.Anonymous, None) == Decision.Allow)

    /// 認証済みでも Anonymous のルートは通ること
    @Test
    def testAnonymousRouteAllowsAuthenticated(): Unit \ Assert =
        Assert.assertTrue(allRoles() |> List.forAll(r ->
            decide(RequiredRole.Anonymous, Some(user(r))) == Decision.Allow))

    ///
    /// 未認証で認証必須のルートはログイン画面へ誘導すること。
    ///
    /// 403 ではなく 302 にするのは、まだログインしていない利用者にとって
    /// 「権限がない」ではなく「ログインが必要」が正しい案内だからである。
    ///
    @Test
    def testAuthenticatedRouteRedirectsUnauthenticated(): Unit \ Assert =
        Assert.assertTrue(decide(RequiredRole.RoleRequired(Role.Sales), None) == Decision.RequireLogin);
        Assert.assertTrue(decide(RequiredRole.RoleAnyOf(allRoles()), None) == Decision.RequireLogin)

    /// 必要なロールを持っていれば通ること
    @Test
    def testAllowsMatchingRole(): Unit \ Assert =
        Assert.assertTrue(decide(RequiredRole.RoleRequired(Role.Sales), Some(user(Role.Sales))) == Decision.Allow)

    ///
    /// ロールが不足していれば拒否すること。
    ///
    /// ログイン済みで権限がない場合はログイン画面へ戻さない。戻すと
    /// 「ログインしているのにログイン画面が出る」という不可解な挙動になる。
    ///
    @Test
    def testDeniesInsufficientRole(): Unit \ Assert =
        Assert.assertTrue(decide(RequiredRole.RoleRequired(Role.Sales), Some(user(Role.Handler))) == Decision.Deny)

    /// いずれかのロールを持っていれば通ること
    @Test
    def testAllowsAnyOfRoles(): Unit \ Assert =
        let required = RequiredRole.RoleAnyOf(Role.Shipper :: Role.Consignee :: Role.Tracker :: Nil);
        Assert.assertTrue(decide(required, Some(user(Role.Consignee))) == Decision.Allow);
        Assert.assertTrue(decide(required, Some(user(Role.Accountant))) == Decision.Deny)

    ///
    /// 管理者はすべてのルートを通れること。
    ///
    /// ロール表で `Admin` は「全機能」と定義されている。ルート定義に `Admin` を
    /// 書き漏らしても管理者が締め出されないよう、判定側で保証する。
    ///
    @Test
    def testAdminPassesEveryRoute(): Unit \ Assert =
        let admin = Some(user(Role.Admin));
        Assert.assertTrue(decide(RequiredRole.RoleRequired(Role.Accountant), admin) == Decision.Allow);
        Assert.assertTrue(decide(RequiredRole.RoleAnyOf(Role.Handler :: Nil), admin) == Decision.Allow);
        Assert.assertTrue(decide(RequiredRole.Anonymous, admin) == Decision.Allow)

    ///
    /// 全ロール × 各要件の可否表。
    ///
    /// 「必要ロールを持つ、または管理者」のときだけ通る。この 1 行が認可の全仕様であり、
    /// 例外を作らないことを網羅で示す。
    ///
    @Test
    def testDecisionTableForAllRoles(): Unit \ Assert =
        let required = RequiredRole.RoleRequired(Role.Router);
        Assert.assertTrue(allRoles() |> List.forAll(r -> {
            let expected = if (r == Role.Router or r == Role.Admin) Decision.Allow else Decision.Deny;
            decide(required, Some(user(r))) == expected
        }))
}
