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
}
