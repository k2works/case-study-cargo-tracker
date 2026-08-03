///
/// 航路画面の単体テスト。
///
/// 画面関数は純粋関数のため、`render` した文字列を検証するだけでよい。
/// ブラウザも DB も起動しない。
///
/// **IT6 のレビューで、`VoyagePages`（455 行）に対応する描画テストが
/// 1 件も無いことが指摘された**（`ShipperPages` 等には既にある）。
/// 寄港地の整形・対応貨物のラベル・ロールによる列の増減・**エスケープ**は、
/// すべて統合テスト経由の部分文字列一致でしか触られていなかった。
/// とりわけエスケープの検証は、統合テストを含めてどのレベルにも無かった。
///
mod VoyagePagesTest {
    use SharedHtml.render
    use RoutingModel.{CargoCapability, Voyage}
    use RoutingModel.{reconstruct, reconstructMovement}
    use VoyagePages.index

    def locations(): List[(String, String)] =
        ("JPOSA", "大阪") :: ("JPTYO", "東京") :: ("CNSHA", "上海") ::
        ("USLAX", "ロサンゼルス") :: Nil

    def noCriteria(): (String, String, String, String, String) = ("", "", "", "", "")

    /// 大阪 → 東京 → 上海 → LA（寄港地 2 つ）
    def viaTwoPorts(vesselName: String): Voyage =
        reconstruct("V-0001", vesselName, "さくら汽船",
            CargoCapability.General :: CargoCapability.Refrigerated :: Nil,
            reconstructMovement("JPOSA", "JPTYO", "2026-11-01T10:00", "2026-11-02T09:00", 1) ::
            reconstructMovement("JPTYO", "CNSHA", "2026-11-03T10:00", "2026-11-05T08:00", 2) ::
            reconstructMovement("CNSHA", "USLAX", "2026-11-06T10:00", "2026-11-20T08:00", 3) :: Nil)

    def direct(): Voyage =
        reconstruct("V-0002", "SS Kaede", "もみじ海運",
            CargoCapability.Hazardous :: Nil,
            reconstructMovement("JPOSA", "USLAX", "2026-12-01T10:00", "2026-12-15T08:00", 1) :: Nil)

    // ---- 寄港地の整形 ----

    ///
    /// 寄港地が**順序どおり**に並ぶこと。
    ///
    /// 集合として出すと運航の順序が消える。東京の次が上海であることは、
    /// そこで積める貨物を探す経路設計者にとって意味を持つ。
    ///
    @Test
    def testShowsPortsOfCallInOrder(): Unit \ Assert =
        let html = render(index(viaTwoPorts("SS Sakura") :: Nil, noCriteria(),
                                locations(), None, true));
        Assert.assertTrue(String.contains(substr = "<td>JPTYO→CNSHA</td>", html))

    /// 端点は寄港地に含めないこと（出発地・仕向地は別の列）
    @Test
    def testExcludesEndpointsFromPortsOfCall(): Unit \ Assert =
        let html = render(index(viaTwoPorts("SS Sakura") :: Nil, noCriteria(),
                                locations(), None, true));
        Assert.assertTrue(not String.contains(substr = "<td>JPOSA→", html));
        Assert.assertTrue(not String.contains(substr = "→USLAX</td>", html))

    /// 直行便は「直行」と出ること（空欄にすると入力漏れと区別できない）
    @Test
    def testShowsDirectWhenNoPortsOfCall(): Unit \ Assert =
        let html = render(index(direct() :: Nil, noCriteria(), locations(), None, true));
        Assert.assertTrue(String.contains(substr = "<td>直行</td>", html))

    // ---- 対応貨物のラベル ----

    ///
    /// 対応貨物種別が業務の言葉で並ぶこと（行に永続化表現を出さない）。
    ///
    /// **ページ全体の部分一致で判定しない**。検索フォームの `<select>` は
    /// `value="GENERAL"` を持つため、`GENERAL` が本文に無いことを求めると
    /// 常に落ちる。逆に「行に出ていないこと」をページ全体で確かめようとすると
    /// 常に真になる。**セルの内容**で判定する
    ///（tester が指摘した空虚アサートと同じ形を、このテスト自身で踏んだ）。
    ///
    @Test
    def testShowsCapabilityLabels(): Unit \ Assert =
        let html = render(index(viaTwoPorts("SS Sakura") :: Nil, noCriteria(),
                                locations(), None, true));
        Assert.assertTrue(String.contains(substr = "<td>一般貨物・冷凍・冷蔵貨物</td>", html));
        Assert.assertTrue(not String.contains(substr = "<td>GENERAL", html))

    // ---- ロールによる出し分け ----

    ///
    /// 経路設計者には操作列が出ること。
    ///
    @Test
    def testShowsManageColumnForRouter(): Unit \ Assert =
        let html = render(index(direct() :: Nil, noCriteria(), locations(), None, true));
        Assert.assertTrue(String.contains(substr = "/voyages/V-0002/edit", html));
        Assert.assertTrue(String.contains(substr = "/voyages/new", html))

    ///
    /// 操作できないロールにはボタンを出さないこと。
    ///
    /// **押せるのに 403 になるボタンを出さない**（観点表の軸 3-7）。
    /// 営業は検索のために一覧を見るが、登録・更新はできない。
    ///
    @Test
    def testHidesManageColumnForNonRouter(): Unit \ Assert =
        let html = render(index(direct() :: Nil, noCriteria(), locations(), None, false));
        Assert.assertTrue(not String.contains(substr = "/voyages/V-0002/edit", html));
        Assert.assertTrue(not String.contains(substr = "/voyages/new", html))

    // ---- エスケープ ----

    ///
    /// **保存された値を画面へ出すときにエスケープすること**。
    ///
    /// 船名・運送会社名は自由入力であり、`<script>` を含む値を登録できる。
    /// 一覧は認証済みの利用者全員が見るため、エスケープが漏れると
    /// **登録した人が他の利用者のブラウザでスクリプトを動かせる**。
    ///
    /// 集約は文字種を制限していない（船名に記号は普通に現れる）。
    /// したがって安全性は**出力側**が持つ。IT6 のレビューまで、
    /// この検証はどのレベルのテストにも無かった。
    ///
    @Test
    def testEscapesVesselName(): Unit \ Assert =
        let html = render(index(viaTwoPorts("<script>alert(1)</script>") :: Nil,
                                noCriteria(), locations(), None, true));
        Assert.assertTrue(not String.contains(substr = "<script>alert(1)</script>", html));
        Assert.assertTrue(String.contains(substr = "&lt;script&gt;", html))

    ///
    /// 検索条件を書き戻すときもエスケープすること。
    ///
    /// 条件は URL のクエリから来る。**他人に踏ませるリンクを作れる**
    /// 経路であり、一覧の行より攻撃者に使いやすい。
    ///
    @Test
    def testEscapesSearchCriteria(): Unit \ Assert =
        let injected = ("\"><script>alert(1)</script>", "", "", "", "");
        let html = render(index(Nil, injected, locations(), None, true));
        Assert.assertTrue(not String.contains(substr = "<script>alert(1)</script>", html))

    // ---- 0 件の書き分け ----

    /// 「登録がない」と「条件に合わない」を区別すること（US07 受入基準 4）
    @Test
    def testDistinguishesEmptyFromNoMatch(): Unit \ Assert =
        let empty = render(index(Nil, noCriteria(), locations(), None, true));
        Assert.assertTrue(String.contains(substr = "登録されている航海はありません", empty));
        let noMatch = render(index(Nil, ("JPOSA", "", "", "", ""), locations(), None, true));
        Assert.assertTrue(String.contains(substr = "条件を緩和して再検索", noMatch))
}
