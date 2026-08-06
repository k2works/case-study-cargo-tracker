///
/// 経路探索条件（`RouteSpec`）のテスト。
///
/// **IT7 のレビューで「専用テストが無い」と指摘された**（tester）。
/// `RouteSpec.flix` は「形式検証をここで済ませる。以降の探索は文字列の妥当性を
/// 再確認しない」と宣言しており、**この防波堤が破れたら下流全体が無防備**である。
/// にもかかわらず `RouteFinderTest.unsafeSpec` が `bug!` でエラーを握り潰すため、
/// 5 つの `RouteSpecError` は一度も検証されていなかった。
///
mod RouteSpecTest {
    use RoutingModel.CargoCapability
    use RoutingRouteSpec.{RouteSpecError, routeSpec, errorLabel}
    use RoutingRouteSpec.{originOf, destinationOf, earliestDepartureOf,
                          arrivalDeadlineOf, requiredOf}

    def ok(): Result[RouteSpecError, RoutingRouteSpec.RouteSpec] =
        routeSpec("JPOSA", "USLAX", "2026-04-01", "2026-04-15", CargoCapability.General)

    def errorOf(r: Result[RouteSpecError, RoutingRouteSpec.RouteSpec]): Option[RouteSpecError] =
        match r {
            case Err(e) => Some(e)
            case Ok(_)  => None
        }

    /// 妥当な条件が組み立てられ、値がそのまま読めること
    @Test
    def testBuildsValidSpec(): Unit \ Assert =
        match ok() {
            case Err(e) => Assert.fail("妥当な条件が拒否された: ${e}")
            case Ok(s)  =>
                Assert.assertEq(expected = "JPOSA", originOf(s));
                Assert.assertEq(expected = "USLAX", destinationOf(s));
                Assert.assertEq(expected = "2026-04-01", earliestDepartureOf(s));
                Assert.assertEq(expected = "2026-04-15", arrivalDeadlineOf(s));
                Assert.assertEq(expected = CargoCapability.General, requiredOf(s))
        }

    ///
    /// **出発地と仕向地の不正を区別すること**。
    ///
    /// 同じ「UN/LOCODE が不正」でも、どちらが悪いかで利用者の直し方が違う。
    ///
    /// この式が偽になる入力: 両方を 1 つのエラーにまとめた実装。
    ///
    @Test
    def testDistinguishesMalformedEndpoints(): Unit \ Assert =
        Assert.assertEq(expected = Some(RouteSpecError.OriginIsMalformed),
            errorOf(routeSpec("JPOS", "USLAX", "2026-04-01", "2026-04-15",
                              CargoCapability.General)));
        Assert.assertEq(expected = Some(RouteSpecError.DestinationIsMalformed),
            errorOf(routeSpec("JPOSA", "uslax", "2026-04-01", "2026-04-15",
                              CargoCapability.General)))

    ///
    /// **全角の UN/LOCODE を弾くこと**。
    ///
    /// IT4 で `日本大阪港` が通った経緯がある。
    ///
    /// この式が偽になる入力: 文字数だけを見る実装（全角 5 文字は長さ 5）。
    ///
    @Test
    def testRejectsFullWidthLocation(): Unit \ Assert =
        Assert.assertEq(expected = Some(RouteSpecError.OriginIsMalformed),
            errorOf(routeSpec("ＪＰＯＳＡ", "USLAX", "2026-04-01", "2026-04-15",
                              CargoCapability.General)))

    ///
    /// 出発地と仕向地が同じなら成立しないこと。
    ///
    /// この式が偽になる入力: 端点の一致を見ない実装（同一港への「経路」が 0 件で返り、
    /// 利用者は「便が無い」と誤解する）。
    ///
    @Test
    def testRejectsSameEndpoints(): Unit \ Assert =
        Assert.assertEq(expected = Some(RouteSpecError.EndpointsAreSame),
            errorOf(routeSpec("JPOSA", "JPOSA", "2026-04-01", "2026-04-15",
                              CargoCapability.General)))

    ///
    /// **暦として存在しない期限を弾くこと**。
    ///
    /// IT4 で `2026-02-30` が型変換に届いて応答が返らなくなった。
    ///
    /// この式が偽になる入力: 桁と区切りだけを見る実装。
    ///
    @Test
    def testRejectsImpossibleDeadline(): Unit \ Assert =
        Assert.assertEq(expected = Some(RouteSpecError.DeadlineIsMalformed),
            errorOf(routeSpec("JPOSA", "USLAX", "2026-04-01", "2026-02-30",
                              CargoCapability.General)));
        Assert.assertEq(expected = Some(RouteSpecError.EarliestDepartureIsMalformed),
            errorOf(routeSpec("JPOSA", "USLAX", "abc", "2026-04-15",
                              CargoCapability.General)))

    ///
    /// **期限が出発可能日より前なら「条件が成立しない」として弾くこと**。
    ///
    /// 0 件として返すと、経路設計者は「便が無い」と読んで航海を探しに行く。
    /// 実際には何本登録しても間に合わないため、**次の一手が違う**。
    ///
    /// この式が偽になる入力: 期限と出発可能日の関係を見ない実装。
    ///
    @Test
    def testRejectsDeadlineBeforeDeparture(): Unit \ Assert =
        Assert.assertEq(expected = Some(RouteSpecError.DeadlineIsBeforeDeparture),
            errorOf(routeSpec("JPOSA", "USLAX", "2026-04-15", "2026-04-01",
                              CargoCapability.General)))

    ///
    /// 期限と出発可能日が同日なら成立すること（境界）。
    ///
    /// 当日出航・当日到着の便は業務上ありうる（近距離のフェリー）。
    ///
    /// この式が偽になる入力: 比較を `<=` で書いた実装（同日が弾かれる）。
    ///
    @Test
    def testAcceptsDeadlineOnDepartureDay(): Unit \ Assert =
        Assert.assertEq(expected = None,
            errorOf(routeSpec("JPOSA", "USLAX", "2026-04-15", "2026-04-15",
                              CargoCapability.General)))

    ///
    /// **検証の順序が保たれること**。
    ///
    /// 出発地が不正で、かつ期限も不正な入力では、**出発地のエラーが返る**。
    /// 順序が定まっていないと、利用者は 1 つ直すたびに次のエラーが出る形になり、
    /// どの順で直せばよいか分からない。
    ///
    /// この式が偽になる入力: 検証を並列に行い任意の 1 つを返す実装。
    ///
    @Test
    def testValidationOrderIsStable(): Unit \ Assert =
        Assert.assertEq(expected = Some(RouteSpecError.OriginIsMalformed),
            errorOf(routeSpec("bad", "uslax", "abc", "xyz", CargoCapability.General)))

    ///
    /// **不成立理由が利用者の言葉になっていること**。
    ///
    /// 列挙値をそのまま見せない（IT2 ふりかえり T8）。
    ///
    /// この式が偽になる入力: `ToString` の結果をそのまま返す実装
    /// （`OriginIsMalformed` と表示される）。
    ///
    @Test
    def testErrorLabelsAreHumanReadable(): Unit \ Assert =
        let labels =
            errorLabel(RouteSpecError.OriginIsMalformed) ::
            errorLabel(RouteSpecError.DestinationIsMalformed) ::
            errorLabel(RouteSpecError.EndpointsAreSame) ::
            errorLabel(RouteSpecError.DeadlineIsMalformed) ::
            errorLabel(RouteSpecError.EarliestDepartureIsMalformed) ::
            errorLabel(RouteSpecError.DeadlineIsBeforeDeparture) :: Nil;
        // 列挙値の綴りが漏れていないこと
        let leaked = labels |> List.filter(l ->
            String.contains(substr = "Malformed", l) or
            String.contains(substr = "Is", l));
        Assert.assertEq(expected = Nil, leaked);
        // すべて相異なること（同じ文言なら区別した意味がない）
        Assert.assertEq(expected = 6, List.length(List.distinct(labels)))
}
