mod TrackingPublicPagesTest {
    use TrackingPublicPages.{renderShow, renderForm, notFoundMessage, eventTypeLabel,
                             locationDisplay, estimatedArrivalDisplay}
    use TrackingView.{detail, event}

    def sampleView(): TrackingView.TrackingDetailView =
        detail("TRK-20260803-0001", "ONBOARD_CARRIER", "Tokyo", "2026-08-10",
            event("2026-08-03 09:15", "LOAD", "Tokyo", "JPTYO") ::
            event("2026-08-02 14:00", "RECEIVE", "Tokyo", "JPTYO") :: Nil)

    /// 追跡番号・ステータス・現在地・イベント履歴が表示されること
    @Test
    def testShowRendersTrackingDetail(): Unit \ Assert =
        let html = renderShow("TRK-20260803-0001", Some(sampleView()));
        Assert.assertTrue(String.contains(substr = "TRK-20260803-0001", html));
        Assert.assertTrue(String.contains(substr = "輸送中", html));
        Assert.assertTrue(String.contains(substr = "Tokyo", html));
        Assert.assertTrue(String.contains(substr = "LOAD", html))

    /// 未検出時にメッセージが表示されること
    @Test
    def testShowRendersNotFoundMessage(): Unit \ Assert =
        let html = renderShow("TRK-NOPE", None);
        Assert.assertTrue(String.contains(substr = notFoundMessage(), html))

    /// スクリプト混入入力がエスケープされること（XSS 対策）
    @Test
    def testShowEscapesScriptInput(): Unit \ Assert =
        let malicious = detail("<script>alert(1)</script>", "RECEIVED", "Tokyo", "", Nil);
        let html = renderShow("x", Some(malicious));
        Assert.assertTrue(not String.contains(substr = "<script>alert(1)</script>", html));
        Assert.assertTrue(String.contains(substr = "&lt;script&gt;", html))

    /// 反映遅延の注記が表示されること（UI 設計の仕様）
    @Test
    def testShowRendersDelayNotice(): Unit \ Assert =
        Assert.assertTrue(String.contains(substr = "最大 30 秒", renderShow("TRK-20260803-0001", Some(sampleView()))))

    /// 荷役種別が業務用語で表示されること（英語の生値を荷主に見せない）
    @Test
    def testShowRendersEventTypeInBusinessTerms(): Unit \ Assert =
        let html = renderShow("TRK-20260803-0001", Some(sampleView()));
        Assert.assertTrue(String.contains(substr = "積込（LOAD）", html));
        Assert.assertTrue(String.contains(substr = "受取（RECEIVE）", html))

    /// 全荷役種別にラベルが定義されていること
    @Test
    def testAllHandlingTypesHaveLabel(): Unit \ Assert =
        let types = "RECEIVE" :: "LOAD" :: "UNLOAD" :: "CUSTOMS" :: "CLAIM" :: Nil;
        let allJapanese = types |> List.forAll(t -> eventTypeLabel(t) != t);
        Assert.assertTrue(allJapanese)

    /// 推定到着日が表示されること（US18 の受入基準）
    @Test
    def testShowRendersEstimatedArrival(): Unit \ Assert =
        let html = renderShow("TRK-20260803-0001", Some(sampleView()));
        Assert.assertTrue(String.contains(substr = "推定到着日", html));
        Assert.assertTrue(String.contains(substr = "2026-08-10", html))

    /// 推定到着日が未設定なら「未定」と表示すること（経路未確定の貨物）
    @Test
    def testShowRendersUndeterminedArrival(): Unit \ Assert =
        let view = detail("TRK-NOETA", "RECEIVED", "Tokyo", "", Nil);
        let html = renderShow("TRK-NOETA", Some(view));
        Assert.assertTrue(String.contains(substr = "推定到着日: 未定", html))

    /// 場所が「都市名（UN/LOCODE）」で併記されること（同名港の区別）
    @Test
    def testShowRendersLocationWithUnlocode(): Unit \ Assert =
        let html = renderShow("TRK-20260803-0001", Some(sampleView()));
        Assert.assertTrue(String.contains(substr = "Tokyo（JPTYO）", html))

    /// 片方しかない場合はある方だけを出すこと
    @Test
    def testLocationDisplayFallsBack(): Unit \ Assert =
        Assert.assertEq(expected = "JPTYO", locationDisplay("", "JPTYO"));
        Assert.assertEq(expected = "Tokyo", locationDisplay("Tokyo", ""));
        Assert.assertEq(expected = "-", locationDisplay("", ""))

    /// 未設定の推定到着日の変換
    @Test
    def testEstimatedArrivalDisplayForEmpty(): Unit \ Assert =
        Assert.assertEq(expected = "未定", estimatedArrivalDisplay(""))

    /// 照会結果ページにも追跡番号の入力フォームがあること（再照会の導線）
    @Test
    def testShowRendersSearchForm(): Unit \ Assert =
        let html = renderShow("TRK-20260803-0001", Some(sampleView()));
        Assert.assertTrue(String.contains(substr = "<form action=\"/public/tracking\" method=\"get\"", html));
        Assert.assertTrue(String.contains(substr = "value=\"TRK-20260803-0001\"", html))

    /// 追跡番号未指定ではフォームのみを表示し、未検出メッセージを出さないこと
    @Test
    def testFormOnlyPageHasNoResultSection(): Unit \ Assert =
        let html = renderForm();
        Assert.assertTrue(String.contains(substr = "追跡番号", html));
        Assert.assertTrue(not String.contains(substr = notFoundMessage(), html))
}
