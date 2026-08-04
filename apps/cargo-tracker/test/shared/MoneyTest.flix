///
/// 金額（共有カーネル）の単体テスト（IT8・US01）。
///
/// # このテストが見るもの
///
/// [ADR-0006](../../../../docs/adr/ADR-0006-fixed-point-money.md) が禁じた
/// **浮動小数点による誤差**は、混入しても例外を出さない。
/// テストが無ければ、請求書を受け取った荷主だけが気付く。
///
/// 端数処理の方向（切り上げ）と、DB との往復で値が変わらないことを固定する。
///
mod MoneyTest {
    use SharedMoney.{ofYen, ofSen, senOf, add, times, scaleByBasisPoints,
                     toYenText, toDecimalString, zero}

    /// 円と銭の換算（1 円 = 100 銭）
    @Test
    def testConvertsYenToSen(): Unit \ Assert =
        Assert.assertEq(expected = 120000i64, senOf(ofYen(1200i64)))

    @Test
    def testAddsAmounts(): Unit \ Assert =
        Assert.assertEq(expected = ofYen(300i64), add(ofYen(100i64), ofYen(200i64)))

    @Test
    def testMultipliesByCount(): Unit \ Assert =
        Assert.assertEq(expected = ofYen(150000i64), times(ofYen(50000i64), 3i64))

    ///
    /// **割増は万分率の整数で掛けること**。
    ///
    /// `1.5` を `Float64` で掛けると、値によって 1 銭ずれる。
    /// この式が偽になる入力: 浮動小数点を経由する実装。
    ///
    @Test
    def testScalesBySurcharge(): Unit \ Assert =
        Assert.assertEq(expected = ofYen(150i64), scaleByBasisPoints(ofYen(100i64), 15000i64))

    ///
    /// **端数は切り上げること**。
    ///
    /// 1 銭を 130% にすると 1.3 銭。切り捨てると 1 銭になり、
    /// **明細を足しても合計に合わない**。
    ///
    /// この式が偽になる入力: 切り捨て（`/`）または四捨五入の実装。
    ///
    @Test
    def testRoundsUpFractionsOfSen(): Unit \ Assert =
        Assert.assertEq(expected = ofSen(2i64), scaleByBasisPoints(ofSen(1i64), 13000i64))

    /// 割増なし（100%）は値を変えないこと
    @Test
    def testKeepsAmountWhenSurchargeIsHundredPercent(): Unit \ Assert =
        Assert.assertEq(expected = ofYen(1234i64), scaleByBasisPoints(ofYen(1234i64), 10000i64))

    /// 0 円に割増を掛けても 0 円（切り上げが 1 銭を生まないこと）
    @Test
    def testKeepsZeroAtZero(): Unit \ Assert =
        Assert.assertEq(expected = zero(), scaleByBasisPoints(zero(), 15000i64))

    ///
    /// **表示は 3 桁区切りにすること**。
    ///
    /// 見積は電話口で読み上げる。`1234567 円` は読み間違える。
    ///
    @Test
    def testFormatsWithDigitGrouping(): Unit \ Assert =
        Assert.assertEq(expected = "1,234,567 円", toYenText(ofYen(1234567i64)))

    /// 3 桁以下は区切らないこと
    @Test
    def testDoesNotGroupSmallAmounts(): Unit \ Assert =
        Assert.assertEq(expected = "999 円", toYenText(ofYen(999i64)))

    /// 円未満は切り上げて表示すること（見積が実額を下回るより上回るほうがよい）
    @Test
    def testRoundsUpToYenForDisplay(): Unit \ Assert =
        Assert.assertEq(expected = "13 円", toYenText(ofSen(1201i64)))

    ///
    /// **DB へ入れる十進数が銭まで保たれること**。
    ///
    /// 列は `NUMERIC(12,2)` である。往復して値が変わると、
    /// 一覧と詳細で金額が食い違う（読み書きの両端が同じ実装だと気付けない）。
    ///
    @Test
    def testRendersDecimalForDatabase(): Unit \ Assert =
        Assert.assertEq(expected = "12.34", toDecimalString(ofSen(1234i64)));
        // **1 桁の端数を 0 で埋めること**。`12.4` は 12 円 40 銭と読まれる
        Assert.assertEq(expected = "12.04", toDecimalString(ofSen(1204i64)));
        Assert.assertEq(expected = "1200.00", toDecimalString(ofYen(1200i64)))
}
