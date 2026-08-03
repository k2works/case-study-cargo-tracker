///
/// 共有カーネルの認証語彙のテスト。
///
/// ロールの永続化値との対応は、DB とコードが独立に変更されうる境界であり、
/// ずれると認可が静かに壊れる。往復変換と全 8 ロールの網羅を固定する。
///
mod SecurityTest {
    use SharedSecurity.{Role, Principal}
    use SharedSecurity.{principal, role, username, userId,
                        roleFromPersisted, roleToPersisted, roleLabel, allRoles}

    /// 8 ロールすべてが定義されていること（バックエンドアーキテクチャのロール表）
    @Test
    def testAllRolesHasEightEntries(): Unit \ Assert =
        Assert.assertEq(expected = 8, List.length(allRoles()))

    /// 永続化値との往復変換が同一のロールを返すこと
    @Test
    def testRoleRoundTripsThroughPersistedValue(): Unit \ Assert =
        Assert.assertTrue(allRoles() |> List.forAll(r ->
            roleFromPersisted(roleToPersisted(r)) == Some(r)))

    /// 永続化値が設計どおりの `ROLE_` 形式であること
    @Test
    def testPersistedValuesMatchDesign(): Unit \ Assert =
        let expected =
            (Role.Shipper,    "ROLE_SHIPPER") ::
            (Role.Consignee,  "ROLE_CONSIGNEE") ::
            (Role.Sales,      "ROLE_SALES") ::
            (Role.Router,     "ROLE_ROUTER") ::
            (Role.Handler,    "ROLE_HANDLER") ::
            (Role.Tracker,    "ROLE_TRACKER") ::
            (Role.Accountant, "ROLE_ACCOUNTANT") ::
            (Role.Admin,      "ROLE_ADMIN") :: Nil;
        Assert.assertTrue(expected |> List.forAll(match (r, value) -> roleToPersisted(r) == value))

    ///
    /// 未知の永続化値は既定値へ倒さず `None` を返すこと。
    ///
    /// ここで既定ロールを返すと、DB に想定外の値が入ったときに
    /// 権限のない利用者へ権限を与えてしまう。
    ///
    @Test
    def testUnknownPersistedValueIsRejected(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(roleFromPersisted("ROLE_SUPERUSER")));
        Assert.assertTrue(Option.isEmpty(roleFromPersisted("")));
        Assert.assertTrue(Option.isEmpty(roleFromPersisted("Shipper")))

    /// 全ロールに業務の言葉の表示名があること（列挙値をそのまま見せない）
    @Test
    def testAllRolesHaveBusinessLabel(): Unit \ Assert =
        let expected =
            (Role.Shipper,    "荷主") ::
            (Role.Consignee,  "荷受人") ::
            (Role.Sales,      "営業担当者") ::
            (Role.Router,     "経路設計者") ::
            (Role.Handler,    "荷役作業員") ::
            (Role.Tracker,    "追跡管理者") ::
            (Role.Accountant, "経理担当者") ::
            (Role.Admin,      "システム管理者") :: Nil;
        Assert.assertTrue(expected |> List.forAll(match (r, label) -> roleLabel(r) == label))

    /// Principal がアクセサどおりの値を返すこと
    @Test
    def testPrincipalAccessors(): Unit \ Assert =
        let p = principal(42i64, "yamada", Role.Sales);
        Assert.assertEq(expected = 42i64, userId(p));
        Assert.assertEq(expected = "yamada", username(p));
        Assert.assertTrue(role(p) == Role.Sales)

    // ---- 荷主との紐付け（IT6 TS11） ----

    ///
    /// 既定では荷主に紐づかないこと。
    ///
    /// 営業担当者・経路設計者・管理者は特定の荷主に属さない。
    /// **紐付けを必須にすると、これらの利用者を表現できない**。
    ///
    @Test
    def testPrincipalHasNoShipperByDefault(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(
            SharedSecurity.shipperIdOf(SharedSecurity.principal(1i64, "yamada", Role.Sales))))

    ///
    /// 荷主に紐づいた利用者を作れること。
    ///
    /// **`principal` を拡張せず別の関数にする**。既定を変えると、
    /// 呼び出し側が「紐付けを渡し忘れた」のか「意図的に紐づけない」のかを
    /// 区別できなくなる（IT5 で `parseScaled` と `parseSignedScaled` を
    /// 分けたのと同じ判断）。
    ///
    @Test
    def testPrincipalCanBeLinkedToShipper(): Unit \ Assert =
        let p = SharedSecurity.principalOf(2i64, "sato", Role.Shipper,
                                           Some("3f2504e0-4f89-11d3-9a0c-0305e82c3301"));
        Assert.assertEq(expected = Some("3f2504e0-4f89-11d3-9a0c-0305e82c3301"),
                        SharedSecurity.shipperIdOf(p))

    ///
    /// 紐付けの有無が他の属性へ影響しないこと。
    ///
    @Test
    def testLinkingDoesNotAffectOtherAttributes(): Unit \ Assert =
        let p = SharedSecurity.principalOf(2i64, "sato", Role.Shipper, Some("abc"));
        Assert.assertEq(expected = 2i64, SharedSecurity.userId(p));
        Assert.assertEq(expected = "sato", SharedSecurity.username(p));
        Assert.assertTrue(SharedSecurity.role(p) == Role.Shipper)
}
