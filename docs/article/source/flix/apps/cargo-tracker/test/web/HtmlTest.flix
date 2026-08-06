mod HtmlTest {
    use SharedHtml.{element, text, fragment, render, attr, class_}
    use SharedHtml.Html

    /// 要素と属性が描画されること
    @Test
    def testRendersElementWithAttributes(): Unit \ Assert =
        let html = element("p", class_("lead") :: Nil, text("こんにちは") :: Nil);
        Assert.assertEq(expected = "<p class=\"lead\">こんにちは</p>", render(html))

    /// テキストノードは既定でエスケープされること（XSS 対策の中核）
    @Test
    def testEscapesTextByDefault(): Unit \ Assert =
        let html = text("<script>alert(1)</script>");
        Assert.assertEq(
            expected = "&lt;script&gt;alert(1)&lt;/script&gt;",
            render(html)
        )

    /// 属性値のエスケープ（引用符を含む値で属性を抜け出せないこと）
    @Test
    def testEscapesAttributeValue(): Unit \ Assert =
        let html = element("div", attr("data-x", "a\"onload=\"evil()") :: Nil, Nil);
        Assert.assertTrue(not String.contains(substr = "onload=\"evil", render(html)))

    /// void 要素は閉じタグを出力しないこと
    @Test
    def testVoidElementHasNoClosingTag(): Unit \ Assert =
        Assert.assertEq(expected = "<br>", render(element("br", Nil, Nil)))

    /// フラグメントは複数ノードを連結すること
    @Test
    def testFragmentConcatenatesChildren(): Unit \ Assert =
        let html = fragment(text("a") :: text("b") :: Nil);
        Assert.assertEq(expected = "ab", render(html))
}
