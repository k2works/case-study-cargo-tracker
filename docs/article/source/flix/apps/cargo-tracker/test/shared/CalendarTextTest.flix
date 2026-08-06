///
/// 共有カーネルの日付・識別子判定のテスト。
///
/// **IT7 のレビューで「テストが 1 件も無い」と指摘された**（tester）。
/// このモジュールには次が入っている。
///
/// - `epochDayOf` — Howard Hinnant の `days_from_civil`。整数除算を含む自明でない式
/// - `isLeapYear` / `daysInMonth` — 100 年・400 年ルール
/// - `dateOf` — **期限当日問題の根治点そのもの**
///
/// にもかかわらず、これらを触る唯一のアサートは
/// `RouteFinderTest.testTransitDaysSpanTheWholeJourney` の `11` 一点であり、
/// **「文字列の引き算では月をまたげない」という設計理由そのものが未検証**だった。
///
/// 効果を一切要求しない純粋関数のため、サーバも DB も要らない。
///
mod CalendarTextTest {
    use SharedCalendarText.{isUnLocode, isIsoDate, isIsoDateTime, isValidDate, isLeapYear,
                            dateOf, toEpochDay, daysBetween, isUpperAlnumCode}

    // ---- daysBetween（月・年・閏日をまたぐ） ----

    ///
    /// **月をまたぐ日数が出ること**。
    ///
    /// これが `toEpochDay` を導入した理由である。文字列の引き算では出せない。
    ///
    /// この式が偽になる入力: 日の部分だけを引く実装（`01 - 28 = -27`）。
    ///
    @Test
    def testDaysBetweenAcrossMonth(): Unit \ Assert =
        Assert.assertEq(expected = Some(1), daysBetween("2026-02-28", "2026-03-01"))

    ///
    /// **閏年では 2 月 28 日と 3 月 1 日の間が 2 日あること**。
    ///
    /// この式が偽になる入力: 2 月を常に 28 日として扱う実装。
    ///
    @Test
    def testDaysBetweenAcrossLeapDay(): Unit \ Assert =
        Assert.assertEq(expected = Some(2), daysBetween("2028-02-28", "2028-03-01"))

    ///
    /// 年をまたぐ日数（`era` の計算）。
    ///
    /// この式が偽になる入力: 年内の通算日だけを見る実装。
    ///
    @Test
    def testDaysBetweenAcrossYear(): Unit \ Assert =
        Assert.assertEq(expected = Some(1), daysBetween("2026-12-31", "2027-01-01"))

    ///
    /// 1 年ぶんの日数（平年 365 日・閏年 366 日）。
    ///
    /// この式が偽になる入力: 1 年を一律 365 日とする実装。
    ///
    @Test
    def testDaysBetweenFullYear(): Unit \ Assert =
        Assert.assertEq(expected = Some(365), daysBetween("2026-01-01", "2027-01-01"));
        Assert.assertEq(expected = Some(366), daysBetween("2028-01-01", "2029-01-01"))

    ///
    /// 世紀の境界（400 年ルール）。2000 年は閏年、2100 年は平年である。
    ///
    /// この式が偽になる入力: 4 で割り切れれば閏年とする実装（2100 年が 2 日になる）。
    ///
    @Test
    def testDaysBetweenAcrossCenturyBoundary(): Unit \ Assert =
        Assert.assertEq(expected = Some(2), daysBetween("2000-02-28", "2000-03-01"));
        Assert.assertEq(expected = Some(1), daysBetween("2100-02-28", "2100-03-01"))

    ///
    /// 同日は 0 日。逆順は負値。
    ///
    /// **0 が正当に出るケースがあること**を明示する。`transitDaysOf` は
    /// 読めなかった場合も 0 へ倒すため、0 の意味が 2 つある
    /// （IT7 レビュー・tester 指摘。実装側の是正は IT8 の返済枠）。
    ///
    /// この式が偽になる入力: 常に絶対値を返す実装。
    ///
    @Test
    def testDaysBetweenSameDayAndReversed(): Unit \ Assert =
        Assert.assertEq(expected = Some(0), daysBetween("2026-04-15", "2026-04-15"));
        Assert.assertEq(expected = Some(-13), daysBetween("2026-04-15", "2026-04-02"))

    ///
    /// 日時（時刻付き）でも日付部分だけで数えること。
    ///
    /// この式が偽になる入力: `isIsoDate` に時刻付きを渡して `None` になる実装
    /// （`dateOf` を通していない）。
    ///
    @Test
    def testDaysBetweenAcceptsDateTime(): Unit \ Assert =
        Assert.assertEq(expected = Some(11),
                        daysBetween("2026-04-02T08:00", "2026-04-13T06:00"))

    ///
    /// 読めない日付は `None`。
    ///
    /// この式が偽になる入力: 解釈できない値を 0 として扱う実装。
    ///
    @Test
    def testDaysBetweenRejectsMalformed(): Unit \ Assert =
        Assert.assertEq(expected = None, daysBetween("abc", "2026-04-15"));
        Assert.assertEq(expected = None, daysBetween("2026-04-15", "2026-02-30"))

    // ---- dateOf（期限当日問題の根治点） ----

    ///
    /// 日時から日付部分を取り出すこと。
    ///
    /// この式が偽になる入力: 先頭 10 文字を切らずそのまま返す実装
    /// （`"2026-04-15T18:00" <= "2026-04-15"` が偽になり、期限当日着が全滅する）。
    ///
    @Test
    def testDateOfTrimsTime(): Unit \ Assert =
        Assert.assertEq(expected = "2026-04-15", dateOf("2026-04-15T18:00"));
        Assert.assertEq(expected = "2026-04-15", dateOf("2026-04-15"))

    ///
    /// 10 文字未満は**そのまま返す**（握り潰さない）。
    ///
    /// 判定は `isIsoDate` の責務であり、ここで既定値へ倒すと
    /// 「壊れた入力」が「妥当な日付」に化ける。
    ///
    /// この式が偽になる入力: 短い入力を空文字や既定日付へ倒す実装。
    ///
    @Test
    def testDateOfKeepsShortInput(): Unit \ Assert =
        Assert.assertEq(expected = "2026-04-1", dateOf("2026-04-1"));
        Assert.assertEq(expected = "", dateOf(""))

    // ---- toEpochDay（基準点） ----

    ///
    /// エポック（1970-01-01）が 0 であること。
    ///
    /// 基準がずれると差分は正しくても絶対値が狂う。
    ///
    /// この式が偽になる入力: `719468` の定数を書き間違えた実装。
    ///
    @Test
    def testEpochDayOrigin(): Unit \ Assert =
        Assert.assertEq(expected = Some(0i64), toEpochDay("1970-01-01"));
        Assert.assertEq(expected = Some(1i64), toEpochDay("1970-01-02"));
        Assert.assertEq(expected = Some(-1i64), toEpochDay("1969-12-31"))

    // ---- isValidDate（100/400 年ルールの 4 象限） ----

    ///
    /// 2 月 29 日の可否（4 象限）。
    ///
    /// この式が偽になる入力: 100 年・400 年の例外を持たない実装
    /// （1900 年が真、2000 年が偽になる）。
    ///
    @Test
    def testFebruaryTwentyNinth(): Unit \ Assert =
        // 4 で割り切れる（閏年）
        Assert.assertTrue(isValidDate(2028, 2, 29));
        // 4 で割り切れない
        Assert.assertTrue(not isValidDate(2026, 2, 29));
        // 100 で割り切れる（平年）
        Assert.assertTrue(not isValidDate(1900, 2, 29));
        // 400 で割り切れる（閏年）
        Assert.assertTrue(isValidDate(2000, 2, 29))

    /// 月の日数の境界（30 日の月・31 日の月）
    @Test
    def testMonthLengthBoundaries(): Unit \ Assert =
        Assert.assertTrue(isValidDate(2026, 4, 30));
        Assert.assertTrue(not isValidDate(2026, 4, 31));
        Assert.assertTrue(isValidDate(2026, 1, 31));
        Assert.assertTrue(not isValidDate(2026, 13, 1));
        Assert.assertTrue(not isValidDate(2026, 0, 1));
        Assert.assertTrue(not isValidDate(2026, 1, 0))

    /// 閏年の判定そのもの
    @Test
    def testLeapYear(): Unit \ Assert =
        Assert.assertTrue(isLeapYear(2028));
        Assert.assertTrue(not isLeapYear(2026));
        Assert.assertTrue(not isLeapYear(1900));
        Assert.assertTrue(isLeapYear(2000))

    // ---- isIsoDate / isIsoDateTime ----

    ///
    /// 暦として存在しない日付を弾くこと。
    ///
    /// IT4 で `2026-02-30` が保存時の型変換で例外になり、**応答が返らなくなった**。
    ///
    /// この式が偽になる入力: 形式（桁と区切り）だけを見る実装。
    ///
    @Test
    def testIsIsoDateRejectsImpossibleDate(): Unit \ Assert =
        Assert.assertTrue(isIsoDate("2026-04-15"));
        Assert.assertTrue(not isIsoDate("2026-02-30"));
        Assert.assertTrue(not isIsoDate("2026-13-01"));
        Assert.assertTrue(not isIsoDate("2026/04/15"));
        Assert.assertTrue(not isIsoDate("2026-4-15"));
        Assert.assertTrue(not isIsoDate("2026-04-15T00:00"))

    ///
    /// 時計として存在しない時刻を弾くこと。
    ///
    /// この式が偽になる入力: 時・分の範囲を見ない実装。
    ///
    @Test
    def testIsIsoDateTimeRejectsImpossibleTime(): Unit \ Assert =
        Assert.assertTrue(isIsoDateTime("2026-04-15T18:00"));
        Assert.assertTrue(isIsoDateTime("2026-04-15T00:00"));
        Assert.assertTrue(isIsoDateTime("2026-04-15T23:59"));
        Assert.assertTrue(not isIsoDateTime("2026-04-15T24:00"));
        Assert.assertTrue(not isIsoDateTime("2026-04-15T23:60"));
        Assert.assertTrue(not isIsoDateTime("2026-04-15"))

    // ---- isUnLocode ----

    ///
    /// **全角英数字を通さないこと**。
    ///
    /// IT4 で `日本大阪港` が UN/LOCODE として通った。`Char.isLetter` /
    /// `Char.isDigit` は全角を真とし、`Int32.fromString` はそれを解釈できない
    /// ——判定と変換で結論が食い違う。
    ///
    /// この式が偽になる入力: `Char.isLetter` を使う実装。
    ///
    @Test
    def testUnLocodeRejectsFullWidth(): Unit \ Assert =
        Assert.assertTrue(isUnLocode("JPOSA"));
        Assert.assertTrue(not isUnLocode("ＪＰＯＳＡ"));
        Assert.assertTrue(not isUnLocode("日本大阪港"))

    ///
    /// 桁数と文字種の境界。
    ///
    /// この式が偽になる入力: 長さを見ない実装、小文字を通す実装。
    ///
    @Test
    def testUnLocodeBoundaries(): Unit \ Assert =
        Assert.assertTrue(not isUnLocode("JPOS"));
        Assert.assertTrue(not isUnLocode("JPOSAX"));
        Assert.assertTrue(not isUnLocode("jposa"));
        Assert.assertTrue(not isUnLocode(""));
        Assert.assertTrue(isUnLocode("US1AX"))

    // ---- isUpperAlnumCode ----

    ///
    /// 利用者が決める識別子（航海番号・予約番号）の形。
    ///
    /// この式が偽になる入力: ハイフンを許さない実装、長さ上限を見ない実装。
    ///
    @Test
    def testUpperAlnumCode(): Unit \ Assert =
        Assert.assertTrue(isUpperAlnumCode(20, "V-0042"));
        Assert.assertTrue(isUpperAlnumCode(20, "BK-12345678"));
        Assert.assertTrue(not isUpperAlnumCode(20, "v-0042"));
        Assert.assertTrue(not isUpperAlnumCode(20, ""));
        Assert.assertTrue(not isUpperAlnumCode(5, "V-00420"));
        Assert.assertTrue(isUpperAlnumCode(5, "V-004"));
        // SQL 断片・日本語を通さない（`RouteRoutes.isBookingIdText` が依存する）
        Assert.assertTrue(not isUpperAlnumCode(20, "' OR 1=1--"));
        Assert.assertTrue(not isUpperAlnumCode(20, "予約"))
}
