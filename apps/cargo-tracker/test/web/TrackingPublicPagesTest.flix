mod TrackingPublicPagesTest {
    use TrackingPublicPages.{renderShow, statusLabel, statusBadgeClass, notFoundMessage, eventTypeLabel}
    use TrackingView.{detail, event}

    def sampleView(): TrackingView.TrackingDetailView =
        detail("TRK-20260803-0001", "ONBOARD_CARRIER", "Tokyo",
            event("2026-08-03 09:15", "LOAD", "Tokyo") ::
            event("2026-08-02 14:00", "RECEIVE", "Tokyo") :: Nil)

    /// 追跡番号・ステータス・現在地・イベント履歴が表示されること
    @Test
    def testShowRendersTrackingDetail(): Unit \ Assert =
        let html = renderShow(Some(sampleView()));
        Assert.assertTrue(String.contains(substr = "TRK-20260803-0001", html));
        Assert.assertTrue(String.contains(substr = "輸送中", html));
        Assert.assertTrue(String.contains(substr = "Tokyo", html));
        Assert.assertTrue(String.contains(substr = "LOAD", html))

    /// 未検出時にメッセージが表示されること
    @Test
    def testShowRendersNotFoundMessage(): Unit \ Assert =
        let html = renderShow(None);
        Assert.assertTrue(String.contains(substr = notFoundMessage(), html))

    /// スクリプト混入入力がエスケープされること（XSS 対策）
    @Test
    def testShowEscapesScriptInput(): Unit \ Assert =
        let malicious = detail("<script>alert(1)</script>", "RECEIVED", "Tokyo", Nil);
        let html = renderShow(Some(malicious));
        Assert.assertTrue(not String.contains(substr = "<script>alert(1)</script>", html));
        Assert.assertTrue(String.contains(substr = "&lt;script&gt;", html))

    /// 反映遅延の注記が表示されること（UI 設計の仕様）
    @Test
    def testShowRendersDelayNotice(): Unit \ Assert =
        Assert.assertTrue(String.contains(substr = "最大 30 秒", renderShow(Some(sampleView()))))

    /// 荷役種別が業務用語で表示されること（英語の生値を荷主に見せない）
    @Test
    def testShowRendersEventTypeInBusinessTerms(): Unit \ Assert =
        let html = renderShow(Some(sampleView()));
        Assert.assertTrue(String.contains(substr = "積込（LOAD）", html));
        Assert.assertTrue(String.contains(substr = "受取（RECEIVE）", html))

    /// 全荷役種別にラベルが定義されていること
    @Test
    def testAllHandlingTypesHaveLabel(): Unit \ Assert =
        let types = "RECEIVE" :: "LOAD" :: "UNLOAD" :: "CUSTOMS" :: "CLAIM" :: Nil;
        let allJapanese = types |> List.forAll(t -> eventTypeLabel(t) != t);
        Assert.assertTrue(allJapanese)

    /// TransportStatus の全 9 値にラベルとバッジ色が定義されていること
    @Test
    def testAllTransportStatusesHaveLabel(): Unit \ Assert =
        let statuses = "NOT_RECEIVED" :: "RECEIVED" :: "LOADED" :: "ONBOARD_CARRIER" ::
                       "UNLOADED" :: "AWAITING_CLAIM" :: "CLAIMED" :: "EXCEPTION" :: "UNKNOWN" :: Nil;
        let allLabeled = statuses |> List.forAll(s -> statusLabel(s) != "" and statusBadgeClass(s) != "");
        Assert.assertTrue(allLabeled)
}
