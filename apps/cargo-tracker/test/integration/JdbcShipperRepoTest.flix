///
/// 荷主リポジトリの JDBC 実装の統合テスト。
///
/// **本プロジェクト初の書き込みトランザクション**を検証する。単体テスト
/// （`RegisterShipperTest`）はインメモリのハンドラで判断の順序を固定した。
/// ここで見るのは **SQL とマッピングの正しさ**、そしてコミット・ロールバックの境界である。
///
mod JdbcShipperRepoTest {
    import java.lang.Exception

    use SharedDbMigration.migrate
    use SharedDbPool.{create, h2Config, close, withConnection, withStatement}
    use SharedDbPool.DataSource
    use SharedDbTx.{transactional, readOnly}
    use ShipperJdbcRepo.withJdbcShipperRepo
    use ShipperModel.{Shipper, ShipperType}
    use ShipperModel.{registerIndividual, registerCorporate}
    use ShipperRepo.ShipperRepo

    def setup(dbName: String): DataSource \ IO =
        let config = h2Config(dbName);
        discard migrate(config);
        create(config)

    def individual(id: String, name: String, email: String): Shipper =
        match registerIndividual(id, name, email, Some("03-1234-5678"), Some("東京都千代田区")) {
            case Ok(s)  => s
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    def corporate(id: String, name: String, email: String, rate: Int32): Shipper =
        match registerCorporate(id, name, email, None, None, "CT-0001", rate) {
            case Ok(s)  => s
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    def anId(): String = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
    def otherId(): String = "11111111-2222-3333-4444-555555555555"

    ///
    /// 保存した荷主を読み戻せること（往復）。
    ///
    /// 任意項目まで含めて確認する。**片道だけのテストは、書けているが読めていない
    /// 状態を見逃す**。
    ///
    @Test
    def testSavesAndReadsBackIndividual(): Unit \ Assert + IO =
        let ds = setup("shipper_roundtrip");
        transactional(ds, () -> withJdbcShipperRepo(() ->
            ShipperRepo.save(individual(anId(), "山田太郎", "yamada@example.com"))));
        let found = readOnly(ds, () -> withJdbcShipperRepo(() ->
            ShipperRepo.findByShipperId(anId())));
        close(ds);
        match found {
            case None => Assert.fail("保存した荷主を読み戻せなかった")
            case Some(s) =>
                Assert.assertEq(expected = "山田太郎", ShipperModel.nameOf(s));
                Assert.assertEq(expected = "yamada@example.com", ShipperModel.emailOf(s));
                Assert.assertEq(expected = "SHP-3F2504E0", ShipperModel.shipperCodeOf(s));
                Assert.assertEq(expected = Some("03-1234-5678"), ShipperModel.phoneOf(s));
                Assert.assertEq(expected = Some("東京都千代田区"), ShipperModel.addressOf(s));
                Assert.assertTrue(ShipperModel.shipperTypeOf(s) == ShipperType.Individual);
                Assert.assertTrue(Option.isEmpty(ShipperModel.discountRateOf(s)))
        }

    ///
    /// 割引率が桁落ちせずに往復すること。
    ///
    /// 内部は万分率の整数、DB は `NUMERIC(5,4)`。`Float64` を挟むと
    /// 0.1500 が 0.14999... になり、**復元時に 1499 へ落ちる**。
    /// 端数の出やすい値（7.5%・0.01%・30%）で確かめる。
    ///
    @Test
    def testDiscountRateRoundTripsWithoutPrecisionLoss(): Unit \ Assert + IO =
        let ds = setup("shipper_rate_roundtrip");
        let rates = 1500 :: 750 :: 1 :: 3000 :: 0 :: Nil;
        // **先頭 8 文字を変える**。荷主コードは ID の先頭 8 文字から作るため、
        // ここを固定すると `shipper_code` の一意制約に当たる（IT4 で実際に踏んだ）
        let ids = List.mapWithIndex((i, _) -> "0000000${i}-0000-0000-0000-000000000000", rates);
        List.forEach(pair -> {
            let (id, rate) = pair;
            transactional(ds, () -> withJdbcShipperRepo(() ->
                ShipperRepo.save(corporate(id, "法人${rate}", "c${rate}@example.com", rate))))
        }, List.zip(ids, rates));
        let restored = readOnly(ds, () -> withJdbcShipperRepo(() ->
            List.map(id -> ShipperRepo.findByShipperId(id), ids)));
        close(ds);
        let actual = restored |> List.map(o -> Option.flatMap(ShipperModel.discountRateOf, o));
        Assert.assertEq(expected = rates |> List.map(r -> Some(r)), actual)

    ///
    /// メールアドレスの検索が大文字小文字を区別しないこと。
    ///
    /// **インメモリ実装と同じ挙動であること**をここで確かめる。両者がずれると、
    /// 単体テストは緑なのに本番で二重登録が起きる。
    ///
    @Test
    def testFindByEmailIsCaseInsensitive(): Unit \ Assert + IO =
        let ds = setup("shipper_email_case");
        transactional(ds, () -> withJdbcShipperRepo(() ->
            ShipperRepo.save(individual(anId(), "山田太郎", "Yamada@Example.com"))));
        let lower = readOnly(ds, () -> withJdbcShipperRepo(() ->
            ShipperRepo.findByEmail("yamada@example.com")));
        let upper = readOnly(ds, () -> withJdbcShipperRepo(() ->
            ShipperRepo.findByEmail("YAMADA@EXAMPLE.COM")));
        close(ds);
        Assert.assertTrue(not Option.isEmpty(lower));
        Assert.assertTrue(not Option.isEmpty(upper))

    /// 未登録のメールでは引けないこと
    @Test
    def testFindByEmailReturnsNoneWhenAbsent(): Unit \ Assert + IO =
        let ds = setup("shipper_email_absent");
        let found = readOnly(ds, () -> withJdbcShipperRepo(() ->
            ShipperRepo.findByEmail("nobody@example.com")));
        close(ds);
        Assert.assertTrue(Option.isEmpty(found))

    /// 一覧が荷主コード順に返ること
    @Test
    def testListsOrderedByShipperCode(): Unit \ Assert + IO =
        let ds = setup("shipper_list_order");
        transactional(ds, () -> withJdbcShipperRepo(() -> {
            ShipperRepo.save(individual("ffffffff-0000-0000-0000-000000000000", "後", "z@example.com"));
            ShipperRepo.save(individual("00000000-0000-0000-0000-000000000000", "先", "a@example.com"))
        }));
        let all = readOnly(ds, () -> withJdbcShipperRepo(() -> ShipperRepo.findAll()));
        close(ds);
        Assert.assertEq(expected = "先" :: "後" :: Nil, List.map(ShipperModel.nameOf, all))

    ///
    /// 同一トランザクション内の保存が 1 つの単位として見えること。
    ///
    /// 2 件を 1 トランザクションで保存し、両方が読めることを確認する。
    ///
    @Test
    def testSavesMultipleInOneTransaction(): Unit \ Assert + IO =
        let ds = setup("shipper_tx_unit");
        transactional(ds, () -> withJdbcShipperRepo(() -> {
            ShipperRepo.save(individual(anId(), "一人目", "one@example.com"));
            ShipperRepo.save(individual(otherId(), "二人目", "two@example.com"))
        }));
        let all = readOnly(ds, () -> withJdbcShipperRepo(() -> ShipperRepo.findAll()));
        close(ds);
        Assert.assertEq(expected = 2, List.length(all))

    ///
    /// **読み取り専用トランザクション内の書き込みが永続化されないこと**。
    ///
    /// `TxMode.ReadOnly` と宣言したルートが誤って保存した場合に、静かに残っては
    /// モードを設ける意味がない。
    ///
    /// **IT4 で判明した事実**: `setReadOnly(true)` だけでは H2 は書き込みを拒否しない。
    /// さらに自動コミットが有効なままだと、その 1 文が即座にコミットされ、
    /// トランザクション制御の外側で永続化される。そのため `readOnly` は
    /// 自動コミットを切り、最後に必ずロールバックする。
    ///
    /// 「例外が飛ぶこと」ではなく「**残らないこと**」を検証するのは、
    /// 拒否の仕方が DB 実装で変わっても、守りたい性質は変わらないからである。
    ///
    @Test
    def testReadOnlyTransactionDiscardsWrites(): Unit \ Assert + IO =
        let ds = setup("shipper_readonly_guard");
        readOnly(ds, () -> withJdbcShipperRepo(() ->
            ShipperRepo.save(individual(anId(), "山田太郎", "yamada@example.com"))));
        let all = readOnly(ds, () -> withJdbcShipperRepo(() -> ShipperRepo.findAll()));
        close(ds);
        Assert.assertTrue(List.isEmpty(all))

    ///
    /// **荷主コードの衝突は登録失敗になること**（既知の限界を仕様として固定する）。
    ///
    /// 荷主コードは `ShipperId` の先頭 8 文字から作る（ビジネスルール 5）。
    /// UUID の先頭 8 文字は 32 ビットしかなく、**誕生日問題により約 77,000 件で
    /// 5 割の確率で衝突する**。`shipper_code` には一意制約があるため、
    /// 衝突した登録は失敗する。
    ///
    /// 現時点では**衝突を再試行しない**。件数が桁違いに増えるまで実害がなく、
    /// 再試行の導入はコード生成の責務をアプリケーション層へ移す設計変更を伴うためである。
    /// **この限界を知らないまま運用に入らないよう、振る舞いをテストで固定する。**
    ///
    @Test
    def testShipperCodeCollisionIsRejected(): Unit \ Assert + IO =
        let ds = setup("shipper_code_collision");
        transactional(ds, () -> withJdbcShipperRepo(() ->
            ShipperRepo.save(individual("aaaaaaaa-1111-0000-0000-000000000000", "先", "a@example.com"))));
        let second = try {
            // 先頭 8 文字が同じ別 ID。コードは両方 `SHP-AAAAAAAA` になる
            transactional(ds, () -> withJdbcShipperRepo(() ->
                ShipperRepo.save(individual("aaaaaaaa-2222-0000-0000-000000000000", "後", "b@example.com"))));
            true
        } catch {
            case _: Exception => false
        };
        let all = readOnly(ds, () -> withJdbcShipperRepo(() -> ShipperRepo.findAll()));
        close(ds);
        Assert.assertTrue(not second);
        Assert.assertEq(expected = 1, List.length(all))

    ///
    /// メールの一意制約が DB 側でも効いていること。
    ///
    /// **壊れ方**（US02 受入条件 5）: アプリケーション層の重複判定は、同時に 2 つの
    /// 登録が走ると両方とも「重複なし」と判定しうる。DB の制約が最後の砦になる。
    ///
    @Test
    def testUniqueEmailConstraintIsEnforced(): Unit \ Assert + IO =
        let ds = setup("shipper_unique_email");
        transactional(ds, () -> withJdbcShipperRepo(() ->
            ShipperRepo.save(individual(anId(), "先", "same@example.com"))));
        let duplicated = try {
            transactional(ds, () -> withJdbcShipperRepo(() ->
                ShipperRepo.save(individual(otherId(), "後", "same@example.com"))));
            true
        } catch {
            case _: Exception => false
        };
        let all = readOnly(ds, () -> withJdbcShipperRepo(() -> ShipperRepo.findAll()));
        close(ds);
        Assert.assertTrue(not duplicated);
        Assert.assertEq(expected = 1, List.length(all))
}
