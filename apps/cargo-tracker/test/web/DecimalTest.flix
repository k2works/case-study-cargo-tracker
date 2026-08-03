///
/// 10 進小数のパースのテスト。
///
/// IT4 のレビューで、**負の小数の符号が消える**バグが 2 箇所に同じ形で
/// 存在することが判明した（`-0.5` は整数部 `-0` が `0` になり、正の値として通過する）。
/// 整数だけを見ていたテストが素通りさせていた。
///
/// 画面から届く値の解釈は「解釈できないものを既定値へ倒さない」ことが要であり、
/// **境界と誤入力をここで網羅する**。
///
mod DecimalTest {
    use SharedDecimal.{parseScaled, parseNonNegativeInt}

    // ---- 整数 ----

    /// 整数を桁数分だけ持ち上げること
    @Test
    def testScalesWholeNumbers(): Unit \ Assert =
        Assert.assertEq(expected = Some(1500i64), parseScaled(2, "15"));
        Assert.assertEq(expected = Some(0i64), parseScaled(2, "0"));
        Assert.assertEq(expected = Some(1200000i64), parseScaled(3, "1200"))

    /// 前後の空白を無視すること
    @Test
    def testTrimsWhitespace(): Unit \ Assert =
        Assert.assertEq(expected = Some(1500i64), parseScaled(2, "  15  "))

    // ---- 小数 ----

    /// 小数を桁数分だけ持ち上げること
    @Test
    def testScalesDecimals(): Unit \ Assert =
        Assert.assertEq(expected = Some(750i64), parseScaled(2, "7.5"));
        Assert.assertEq(expected = Some(1550i64), parseScaled(2, "15.5"));
        Assert.assertEq(expected = Some(12500i64), parseScaled(3, "12.5"));
        Assert.assertEq(expected = Some(1i64), parseScaled(3, "0.001"))

    ///
    /// 桁数を超える小数を切り詰めること（四捨五入しない）。
    ///
    /// **利用者が入力していない桁を勝手に繰り上げない**。`12.5678` を
    /// 小数第 3 位までで `12.568` にすると、入力していない 1 グラムが増える。
    ///
    @Test
    def testTruncatesExcessFractionDigits(): Unit \ Assert =
        Assert.assertEq(expected = Some(12567i64), parseScaled(3, "12.5678"));
        Assert.assertEq(expected = Some(1599i64), parseScaled(2, "15.999"))

    // ---- 負の値（IT4 レビューで見つかったバグ） ----

    ///
    /// **負の小数を受け付けないこと**。
    ///
    /// `-0.5` は整数部が `-0` となり、素朴な実装では `0` に解釈されて
    /// **符号が消え、正の値として検証を通過する**。整数の `-5` は弾けるため、
    /// 整数だけを見ていたテストではこの穴に気付けなかった。
    ///
    @Test
    def testRejectsNegativeDecimals(): Unit \ Assert =
        let negatives = "-0.5" :: "-0.001" :: "-0.0" :: "-1.5" :: Nil;
        Assert.assertTrue(negatives |> List.forAll(v -> Option.isEmpty(parseScaled(3, v))))

    /// 負の整数も受け付けないこと
    @Test
    def testRejectsNegativeIntegers(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(parseScaled(2, "-5")));
        Assert.assertTrue(Option.isEmpty(parseScaled(2, "-0")))

    /// 明示的な正符号も受け付けないこと（解釈の揺れを作らない）
    @Test
    def testRejectsExplicitPlusSign(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(parseScaled(2, "+5")))

    // ---- 解釈できない入力 ----

    /// 数値でない入力を受け付けないこと
    @Test
    def testRejectsNonNumeric(): Unit \ Assert =
        let invalid = "abc" :: "十五" :: "" :: "   " :: "1e3" :: "1,200" :: Nil;
        Assert.assertTrue(invalid |> List.forAll(v -> Option.isEmpty(parseScaled(2, v))))

    ///
    /// 全角数字を受け付けないこと。
    ///
    /// `Char.isDigit` は全角数字を真とするが `Int64.fromString` は解釈できない。
    /// 判定と変換で結論が食い違うと、**判定を通ったのに変換で落ちる**。
    ///
    @Test
    def testRejectsFullWidthDigits(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(parseScaled(2, "１５")))

    ///
    /// 小数点だけ・整数部だけの入力を受け付けないこと。
    ///
    /// `.5` や `15.` は入力の途中に見える。曖昧なまま解釈しない。
    ///
    @Test
    def testRejectsIncompleteDecimals(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(parseScaled(2, ".5")));
        Assert.assertTrue(Option.isEmpty(parseScaled(2, "15.")));
        Assert.assertTrue(Option.isEmpty(parseScaled(2, ".")))

    /// 小数点が 2 つある入力を受け付けないこと
    @Test
    def testRejectsMultipleDecimalPoints(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(parseScaled(2, "1.2.3")))

    // ---- 非負整数 ----

    /// 非負整数を解釈できること
    @Test
    def testParsesNonNegativeInt(): Unit \ Assert =
        Assert.assertEq(expected = Some(5), parseNonNegativeInt("5"));
        Assert.assertEq(expected = Some(0), parseNonNegativeInt("0"))

    /// 負・小数・非数値を受け付けないこと
    @Test
    def testRejectsInvalidNonNegativeInt(): Unit \ Assert =
        let invalid = "-1" :: "1.5" :: "abc" :: "" :: Nil;
        Assert.assertTrue(invalid |> List.forAll(v -> Option.isEmpty(parseNonNegativeInt(v))))
}
