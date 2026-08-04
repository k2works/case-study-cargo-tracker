///
/// `LIKE` パターンの組み立て（IT8・TS14。IT6 レビュー M6）。
///
/// # このテストが存在する理由
///
/// `escapeLike` は 3 箇所に同じ実装で複製されていた。**どの複製にも
/// テストが無かった**——だから 3 つに増えても誰も気付かなかった。
///
/// この関数は**置換の順序を間違えると静かに壊れる**。壊れても検索結果が
/// 増えるだけで例外は出ない。**テストが無ければ運用で気付く手段がない**。
///
mod SqlTest {
    use SharedDbSql.{escapeLike, likePattern}

    ///
    /// **`\` を最初に置換すること**。
    ///
    /// この式が偽になる入力: `%` → `\%` を先に行う実装。
    /// その場合 `\` の置換が後から `\%` の `\` まで二重化し、
    /// `\\\%` になる（打ち消しが 1 段ずれてワイルドカードが復活する）。
    ///
    @Test
    def testEscapesBackslashFirst(): Unit \ Assert =
        Assert.assertEq(expected = "\\\\\\%", escapeLike("\\%"))

    /// `%` はワイルドカードとして扱われないこと
    @Test
    def testEscapesPercent(): Unit \ Assert =
        Assert.assertEq(expected = "100\\%", escapeLike("100%"))

    /// `_` はワイルドカードとして扱われないこと
    @Test
    def testEscapesUnderscore(): Unit \ Assert =
        Assert.assertEq(expected = "a\\_b", escapeLike("a_b"))

    /// メタ文字を含まない値はそのまま
    @Test
    def testLeavesPlainTextAlone(): Unit \ Assert =
        Assert.assertEq(expected = "yamada", escapeLike("yamada"))

    /// パターンは前後の空白を落とし、小文字へ揃え、`%` で挟むこと
    @Test
    def testBuildsPatternFromUserInput(): Unit \ Assert =
        Assert.assertEq(expected = "%jposa%", likePattern("  JPOSA  "))

    ///
    /// **`%` の 1 文字で全件が出ないこと**。
    ///
    /// この式が偽になる入力: 打ち消しを忘れた実装。利用者は
    /// 「絞り込んだのに全部出る」ことになり、絞り込みを信用しなくなる。
    ///
    @Test
    def testPatternNeutralizesWildcardInput(): Unit \ Assert =
        Assert.assertEq(expected = "%\\%%", likePattern("%"))

    /// 空文字は全件一致のパターンになること（「絞らない」は別の条件で表す）
    @Test
    def testEmptyInputMatchesEverything(): Unit \ Assert =
        Assert.assertEq(expected = "%%", likePattern("   "))
}
