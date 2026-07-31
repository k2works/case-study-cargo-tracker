mod ComponentsTest {
    use SharedHtml.{text, element, render}
    use SharedHtmlComponents.{form, formField, statusBadge, statusLabel, statusBadgeClass, alert}

    /// フォームは action / method を持つこと
    @Test
    def testFormRendersActionAndMethod(): Unit \ Assert =
        let html = render(form("/bookings", "post", None, text("中身") :: Nil));
        Assert.assertTrue(String.contains(substr = "<form action=\"/bookings\" method=\"post\"", html));
        Assert.assertTrue(String.contains(substr = "中身", html))

    /// CSRF トークンが hidden フィールドとして自動付与されること
    @Test
    def testFormAddsCsrfHiddenField(): Unit \ Assert =
        let html = render(form("/bookings", "post", Some("tok-123"), Nil));
        Assert.assertTrue(String.contains(
            substr = "<input type=\"hidden\" name=\"_csrf\" value=\"tok-123\">", html))

    /// GET フォームには CSRF を付けないこと（状態を変更しないため不要）
    @Test
    def testFormOmitsCsrfForGet(): Unit \ Assert =
        let html = render(form("/public/tracking", "get", Some("tok-123"), Nil));
        Assert.assertTrue(not String.contains(substr = "_csrf", html))

    /// 入力欄はラベルと id / for が対応すること（アクセシビリティ要件）
    @Test
    def testFormFieldLinksLabelToInput(): Unit \ Assert =
        let html = render(formField("trackingNumber", "追跡番号", "", Nil));
        Assert.assertTrue(String.contains(substr = "<label for=\"trackingNumber\"", html));
        Assert.assertTrue(String.contains(substr = "id=\"trackingNumber\"", html));
        Assert.assertTrue(String.contains(substr = "name=\"trackingNumber\"", html))

    /// 入力値が value に反映されること（再入力を強いないため）
    @Test
    def testFormFieldKeepsSubmittedValue(): Unit \ Assert =
        let html = render(formField("trackingNumber", "追跡番号", "CT-0001", Nil));
        Assert.assertTrue(String.contains(substr = "value=\"CT-0001\"", html))

    /// エラーがある場合、メッセージと is-invalid を出すこと
    @Test
    def testFormFieldRendersErrors(): Unit \ Assert =
        let html = render(formField("trackingNumber", "追跡番号", "x", "形式が正しくありません" :: Nil));
        Assert.assertTrue(String.contains(substr = "is-invalid", html));
        Assert.assertTrue(String.contains(substr = "形式が正しくありません", html));
        Assert.assertTrue(String.contains(substr = "aria-invalid=\"true\"", html))

    /// エラーがない場合は is-invalid を出さないこと
    @Test
    def testFormFieldHasNoErrorMarkupWhenValid(): Unit \ Assert =
        let html = render(formField("trackingNumber", "追跡番号", "CT-0001", Nil));
        Assert.assertTrue(not String.contains(substr = "is-invalid", html))

    /// 入力値がエスケープされること
    @Test
    def testFormFieldEscapesValue(): Unit \ Assert =
        let html = render(formField("q", "検索", "\"><script>alert(1)</script>", Nil));
        Assert.assertTrue(not String.contains(substr = "<script>", html))

    /// TransportStatus の全 9 値がラベルとバッジ色を持つこと
    @Test
    def testStatusBadgeCoversAllTransportStatuses(): Unit \ Assert =
        let statuses = "NOT_RECEIVED" :: "RECEIVED" :: "LOADED" :: "ONBOARD_CARRIER" ::
                       "UNLOADED" :: "AWAITING_CLAIM" :: "CLAIMED" :: "EXCEPTION" ::
                       "UNKNOWN" :: Nil;
        Assert.assertTrue(statuses |> List.forAll(s ->
            statusLabel(s) != "" and statusBadgeClass(s) != ""))

    /// バッジがラベルと Bootstrap クラスを描画すること
    @Test
    def testStatusBadgeRendersLabelAndClass(): Unit \ Assert =
        let html = render(statusBadge("CLAIMED"));
        Assert.assertTrue(String.contains(substr = "badge bg-success", html));
        Assert.assertTrue(String.contains(substr = "引取完了", html))

    /// 未知の値でも表示を壊さないこと（永続化値の追加に対する耐性）
    @Test
    def testStatusBadgeFallsBackForUnknownValue(): Unit \ Assert =
        let html = render(statusBadge("SOMETHING_NEW"));
        Assert.assertTrue(String.contains(substr = "不明", html))

    /// フラッシュメッセージが Bootstrap の alert として描画されること
    @Test
    def testAlertRendersBootstrapAlert(): Unit \ Assert =
        let html = render(alert("success", "貨物予約 BK-1234 を登録しました"));
        Assert.assertTrue(String.contains(substr = "alert alert-success", html));
        Assert.assertTrue(String.contains(substr = "貨物予約 BK-1234 を登録しました", html))

    /// スクリーンリーダーへ通知されること（ui_design.md のアクセシビリティ要件）
    @Test
    def testAlertHasLiveRegionAttributes(): Unit \ Assert =
        let html = render(alert("danger", "入力内容に誤りがあります"));
        Assert.assertTrue(String.contains(substr = "role=\"status\"", html));
        Assert.assertTrue(String.contains(substr = "aria-live=\"polite\"", html))

    /// メッセージがエスケープされること
    @Test
    def testAlertEscapesMessage(): Unit \ Assert =
        let html = render(alert("danger", "<img src=x onerror=alert(1)>"));
        Assert.assertTrue(not String.contains(substr = "<img", html))
}
