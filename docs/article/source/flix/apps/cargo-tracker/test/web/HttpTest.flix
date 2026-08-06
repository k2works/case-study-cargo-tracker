///
/// HTTP 層の単体テスト。
///
/// `parseQuery` は外部入力の最前線でありながら、IT2 では仕様をコメントで宣言しただけで
/// テストがなかった（IT2 レビュー M9）。同値クラスごとに振る舞いを固定する。
///
mod HttpTest {
    use SharedHttp.{parseQuery, queryParam, requestWithQuery, HttpMethod}

    def q(raw: String): Map[String, String] \ IO = parseQuery(raw)

    /// 空文字では空の Map を返すこと
    @Test
    def testParseQueryReturnsEmptyForBlank(): Unit \ Assert + IO =
        Assert.assertTrue(Map.isEmpty(q("")))

    /// 単純な key=value を解析すること
    @Test
    def testParseQueryParsesSinglePair(): Unit \ Assert + IO =
        Assert.assertEq(expected = Some("TRK-0001"), Map.get("trackingNumber", q("trackingNumber=TRK-0001")))

    /// 値が空の項目は空文字として扱うこと（フォームを未入力で送信した形）
    @Test
    def testParseQueryKeepsEmptyValue(): Unit \ Assert + IO =
        Assert.assertEq(expected = Some(""), Map.get("trackingNumber", q("trackingNumber=")))

    /// `=` のない項目は空文字として扱うこと
    @Test
    def testParseQueryTreatsBareKeyAsEmpty(): Unit \ Assert + IO =
        Assert.assertEq(expected = Some(""), Map.get("trackingNumber", q("trackingNumber")))

    /// 複数の項目を解析すること
    @Test
    def testParseQueryParsesMultiplePairs(): Unit \ Assert + IO =
        let m = q("a=1&b=2");
        Assert.assertEq(expected = Some("1"), Map.get("a", m));
        Assert.assertEq(expected = Some("2"), Map.get("b", m))

    ///
    /// 同名キーは先勝ちとすること。
    ///
    /// 現在の画面はいずれも単一値のフォームである。複数値（チェックボックス等）が必要に
    /// なった時点で仕様を変更する（IT2 レビューのスコープ外の発見）。
    ///
    @Test
    def testParseQueryTakesFirstOfDuplicateKeys(): Unit \ Assert + IO =
        Assert.assertEq(expected = Some("1"), Map.get("a", q("a=1&a=2")))

    /// 空のペア（`&&`・末尾の `&`）を無視すること
    @Test
    def testParseQueryIgnoresEmptyPairs(): Unit \ Assert + IO =
        let m = q("&&a=1&");
        Assert.assertEq(expected = Some("1"), Map.get("a", m));
        Assert.assertEq(expected = 1, Map.size(m))

    /// パーセントエンコードを復号すること
    @Test
    def testParseQueryDecodesPercentEncoding(): Unit \ Assert + IO =
        Assert.assertEq(expected = Some("TRK-0001"), Map.get("n", q("n=TRK%2D0001")))

    ///
    /// `+` を空白として復号すること。
    ///
    /// `application/x-www-form-urlencoded` の仕様であり、GET フォームの送信形式に一致する。
    /// 追跡番号に `+` は現れないため実害はないが、振る舞いを明示しておく。
    ///
    @Test
    def testParseQueryDecodesPlusAsSpace(): Unit \ Assert + IO =
        Assert.assertEq(expected = Some("a b"), Map.get("n", q("n=a+b")))

    /// 日本語（UTF-8）を復号すること
    @Test
    def testParseQueryDecodesUtf8(): Unit \ Assert + IO =
        Assert.assertEq(expected = Some("東京"), Map.get("city", q("city=%E6%9D%B1%E4%BA%AC")))

    /// クエリパラメータをリクエストから取り出せること
    @Test
    def testQueryParamReadsFromRequest(): Unit \ Assert =
        let req = requestWithQuery(HttpMethod.Get, "/public/tracking", Map.empty(),
                                   Map#{"trackingNumber" => "TRK-0001"}, Map.empty());
        Assert.assertEq(expected = Some("TRK-0001"), queryParam(req, "trackingNumber"))

    /// 存在しないクエリパラメータは None を返すこと
    @Test
    def testQueryParamReturnsNoneWhenAbsent(): Unit \ Assert =
        let req = requestWithQuery(HttpMethod.Get, "/public/tracking", Map.empty(),
                                   Map.empty(), Map.empty());
        Assert.assertTrue(Option.isEmpty(queryParam(req, "trackingNumber")))
}
