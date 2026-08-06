///
/// 共有カーネル: UN/LOCODE と日付・日時の形式判定。
///
/// **なぜ共有するか**: `Location` は共有カーネルであり（ドメインモデル設計）、
/// その識別子の形式判定を BC ごとに書くと規則が分岐する。
/// IT4 は `Booking` に置いたが、IT6 で `Routing` が同じ判定を必要とした。
/// **BC が相手のモジュールを参照すると `arch-lint` 規約 4 に違反する**ため、
/// 共有カーネルへ移す。
///
/// 効果を一切要求しない。純粋な判定のみを持つ。
///
mod SharedCalendarText {

    ///
    /// UN/LOCODE の形（英大文字・数字の 5 文字）か。
    ///
    /// `Char.isLetter` / `Char.isDigit` を使わない。**全角数字や他言語の文字を
    /// 通してしまう**（IT4 で `日本大阪港` が UN/LOCODE として通った）。
    ///
    /// 実在するかは判定しない。マスタとの突合は `LocationCatalog` の責務である。
    ///
    pub def isUnLocode(value: String): Bool =
        String.length(value) == 5 and
        String.forAll(isAsciiAlphanumeric, value)

    /// ASCII の英大文字（呼び出し側で大文字化済み）または数字か
    def isAsciiAlphanumeric(c: Char): Bool =
        let code = Char.toBmpCodePoint(c);
        (code >= 0x41 and code <= 0x5A) or (code >= 0x30 and code <= 0x39)

    ///
    /// `YYYY-MM-DD` の形をしており、**暦として存在する日付**か。
    ///
    /// 形式だけを見ると `2026-02-30` や `2026-13-45` を通す。IT4 でこれが
    /// 実際に起き、保存時の型変換で例外になって**応答が返らなくなった**。
    ///
    pub def isIsoDate(value: String): Bool =
        String.length(value) == 10 and
        String.charAt(4, value) == '-' and
        String.charAt(7, value) == '-' and
        isAllAsciiDigits(String.slice(start = 0, end = 4, value)) and
        isAllAsciiDigits(String.slice(start = 5, end = 7, value)) and
        isAllAsciiDigits(String.slice(start = 8, end = 10, value)) and
        (match (Int32.fromString(String.slice(start = 0, end = 4, value)),
                Int32.fromString(String.slice(start = 5, end = 7, value)),
                Int32.fromString(String.slice(start = 8, end = 10, value))) {
            case (Some(y), Some(m), Some(d)) => isValidDate(y, m, d)
            case _                           => false
         })

    ///
    /// `YYYY-MM-DDTHH:MM` の形をしており、**暦と時計として存在する日時**か。
    ///
    /// 航海スケジュールの出発・到着に使う。日付だけでは
    /// 「同じ日の午前と午後」を区別できず、区間の順序を判定できない。
    ///
    pub def isIsoDateTime(value: String): Bool =
        String.length(value) == 16 and
        String.charAt(10, value) == 'T' and
        isIsoDate(String.slice(start = 0, end = 10, value)) and
        isTimeOfDay(String.slice(start = 11, end = 16, value))

    /// `HH:MM` の形をしており、時計として存在する時刻か
    def isTimeOfDay(value: String): Bool =
        String.length(value) == 5 and
        String.charAt(2, value) == ':' and
        isAllAsciiDigits(String.slice(start = 0, end = 2, value)) and
        isAllAsciiDigits(String.slice(start = 3, end = 5, value)) and
        (match (Int32.fromString(String.slice(start = 0, end = 2, value)),
                Int32.fromString(String.slice(start = 3, end = 5, value))) {
            case (Some(h), Some(m)) => h >= 0 and h <= 23 and m >= 0 and m <= 59
            case _                  => false
         })

    /// 暦として存在する日付か（閏年を含む）
    pub def isValidDate(year: Int32, month: Int32, day: Int32): Bool =
        year >= 1 and month >= 1 and month <= 12 and
        day >= 1 and day <= daysInMonth(year, month)

    def daysInMonth(year: Int32, month: Int32): Int32 = match month {
        case 2  => if (isLeapYear(year)) 29 else 28
        case 4  => 30
        case 6  => 30
        case 9  => 30
        case 11 => 30
        case _  => 31
    }

    ///
    /// `YYYY-MM-DD` または `YYYY-MM-DDTHH:MM` から**日付部分**を取り出す。
    ///
    /// **期限は日付（`DATE`）、到着は日時（`TIMESTAMP`）である**。素朴に比較すると
    /// `"2026-04-15T18:00" <= "2026-04-15"` が偽になり、**期限当日に着く便が
    /// すべて刈られる**（[テスト戦略](../../../../../../docs/design/test_strategy.md) 3.1.1
    /// 「境界の落とし穴」）。比較の前に必ず日付単位へ揃える。
    ///
    /// 形式が不正な場合は入力をそのまま返す。判定は `isIsoDate` /
    /// `isIsoDateTime` の責務であり、ここで握り潰さない。
    ///
    pub def dateOf(value: String): String =
        if (String.length(value) >= 10) String.slice(start = 0, end = 10, value) else value

    ///
    /// `YYYY-MM-DD` を 1970-01-01 からの通算日数へ変換する。
    ///
    /// 所要日数の算出（出航日から最終到着日まで）に使う。文字列の引き算では
    /// **月をまたいだ日数が出せない**。暦法（グレゴリオ）を 1 箇所に閉じる。
    ///
    /// 形式が不正な場合は `None` を返す。
    ///
    pub def toEpochDay(value: String): Option[Int64] =
        let d = dateOf(value);
        if (not isIsoDate(d))
            None
        else
            match (Int32.fromString(String.slice(start = 0, end = 4, d)),
                   Int32.fromString(String.slice(start = 5, end = 7, d)),
                   Int32.fromString(String.slice(start = 8, end = 10, d))) {
                case (Some(y), Some(m), Some(day)) => Some(epochDayOf(y, m, day))
                case _                             => None
            }

    ///
    /// 通算日数の算出（Howard Hinnant の `days_from_civil` と同じ式）。
    ///
    /// 3 月を年始とみなすことで閏日を年末へ寄せ、月ごとの場合分けを消す。
    ///
    def epochDayOf(year: Int32, month: Int32, day: Int32): Int64 =
        let y = Int32.toInt64(if (month <= 2) year - 1 else year);
        let era = (if (y >= 0i64) y else y - 399i64) / 400i64;
        let yoe = y - era * 400i64;                                  // [0, 399]
        let m = Int32.toInt64(month);
        let doy = (153i64 * (if (m > 2i64) m - 3i64 else m + 9i64) + 2i64) / 5i64 +
                  Int32.toInt64(day) - 1i64;                         // [0, 365]
        let doe = yoe * 365i64 + yoe / 4i64 - yoe / 100i64 + doy;    // [0, 146096]
        era * 146097i64 + doe - 719468i64

    ///
    /// 通算時間（エポックからの時間）。
    ///
    /// **日付と時刻の両方を見る**（IT12・US19）。エスカレーションの閾値は
    /// 48 時間であり、日数だけでは「2 日ちょうど」と「2 日と 1 時間」を
    /// 区別できない——閾値ちょうどを含めるか否かが判定できなくなる。
    ///
    /// 分は切り捨てる。**時間単位の閾値に分の精度は要らない**。
    ///
    pub def toEpochHours(value: String): Option[Int32] =
        match toEpochDay(value) {
            case None      => None
            case Some(day) =>
                let hour = hourOf(value);
                let total = day * 24i64 + Int32.toInt64(hour);
                if (total > 2147483647i64) Some(2147483647)
                else if (total < -2147483648i64) Some(-2147483648)
                else Some(Int64.tryToInt32(total) |> Option.getWithDefault(0))
        }

    ///
    /// 時刻部の「時」。
    ///
    /// 区切りは `T` と半角空白の両方を受ける（画面と DB で違う）。
    /// 読めなければ 0 とする——**日付は読めているため、日単位の判定は成立する**。
    ///
    def hourOf(value: String): Int32 =
        if (String.length(value) < 13)
            0
        else
            Int32.fromString(String.slice(start = 11, end = 13, value))
                |> Option.getWithDefault(0)

    /// 2 つの日付の間の日数（`since` から `until` まで）
    pub def daysBetween(since: String, until: String): Option[Int32] =
        match (toEpochDay(since), toEpochDay(until)) {
            // 日数が Int32 に収まらない入力（約 590 万年）は業務上存在しない。
            // それでも切り詰めずに `Int32.MaxValue` へ倒すのは、
            // 黙って小さな値になるより「異常に大きい」と読める方が安全だからである
            case (Some(a), Some(b)) =>
                let diff = b - a;
                if (diff > 2147483647i64) Some(2147483647)
                else if (diff < -2147483648i64) Some(-2147483648)
                else Some(Int64.tryToInt32(diff) |> Option.getWithDefault(0))
            case _                  => None
        }

    /// 閏年か（グレゴリオ暦）
    pub def isLeapYear(year: Int32): Bool =
        (Int32.remainder(year, 4) == 0 and Int32.remainder(year, 100) != 0) or
        Int32.remainder(year, 400) == 0

    ///
    /// ASCII の数字のみか。
    ///
    /// `Char.isDigit` は全角数字を真とし、`Int32.fromString` はそれを
    /// 解釈できない。判定と変換で結論が食い違う。
    ///
    def isAllAsciiDigits(value: String): Bool =
        value != "" and String.forAll(c -> {
            let code = Char.toBmpCodePoint(c);
            code >= 0x30 and code <= 0x39
        }, value)

    ///
    /// 大文字英数字とハイフンだけからなる識別子か（1〜`maxLength` 文字）。
    ///
    /// **利用者が決める識別子**（航海番号）の形をここに置く。IT6 の当初は
    /// 集約（`RoutingModel`）と通知（`SharedHttpFlash`）が別々に規則を持ち、
    /// 集約が文字種を見ない一方で通知が大文字英数字だけを通していた。
    /// その結果、小文字を含む番号は**登録に成功するのに完了通知が出ない**
    /// という食い違いが起きた（IT6 レビュー H2）。
    ///
    /// 規則を共有カーネルへ置けば、片方だけ緩める余地が無くなる。
    /// 置き場所が共有カーネルなのは、**集約と通知の双方から見える唯一の層**
    /// だからである（`SharedHttpFlash` は共有の基盤であり、特定の BC の
    /// ドメインへ依存させない。`arch-lint` 規約 4）。
    ///
    /// `Char.isLetter` / `Char.isDigit` は使わない。**全角英数字を通してしまう**
    /// （IT4 で `日本大阪港` が UN/LOCODE として通った原因）。
    ///
    pub def isUpperAlnumCode(maxLength: Int32, value: String): Bool =
        String.length(value) >= 1 and String.length(value) <= maxLength and
        (value |> String.toList |> List.forAll(c ->
            (c >= 'A' and c <= 'Z') or (c >= '0' and c <= '9') or c == '-'))

    ///
    /// `YYYY-MM-DDTHH:MM` を SQL の TIMESTAMP 文字列（`YYYY-MM-DD HH:MM:SS`）へ。
    ///
    /// 画面の `datetime-local` は秒を持たない形で送ってくるため補う。
    ///
    /// **共有カーネルに置く**（IT11・L1）。IT10 の時点で Booking と Handling が
    /// 同じ関数を各自持っており、**片方だけ直せる状態**だった。
    /// 日時の書式は特定の BC のものではない。
    ///
    /// **空文字は渡さない**——`CAST('' AS TIMESTAMP)` は例外になる
    /// （集約が空を拒むためここへは来ない）。
    ///
    pub def toSqlTimestamp(value: String): String =
        let replaced = String.replace(src = "T", dst = " ", value);
        if (String.length(replaced) == 16) replaced + ":00" else replaced
}
