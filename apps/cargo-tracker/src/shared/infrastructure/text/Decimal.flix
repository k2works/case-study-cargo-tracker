///
/// 画面から届く 10 進小数の文字列を、固定小数点の整数へ変換する。
///
/// **なぜ共有するか**: 割引率（万分率）・重量（グラム）はいずれも
/// 「小数の文字列 → 桁を固定した整数」の変換であり、内容は小数桁数しか違わない。
/// IT4 では 2 箇所へ別々に書いた結果、**負の小数の符号が消えるという同じバグを
/// 2 箇所に作り込んだ**（`-0.5` は整数部 `-0` が `0` になり、正の値として通過する）。
/// 1 箇所にまとめ、境界値を直接テストできるようにする。
///
mod SharedDecimal {

    ///
    /// 小数第 `scale` 位までの固定小数点整数として解釈する。
    ///
    /// - `parseScaled(2, "15")`    => `Some(1500)`（15.00%）
    /// - `parseScaled(2, "7.5")`   => `Some(750)`（7.50%）
    /// - `parseScaled(3, "12.5")`  => `Some(12500)`（12.500 kg = 12500 g）
    /// - `parseScaled(3, "-0.5")`  => `None`（**負の値は受け付けない**）
    /// - `parseScaled(2, "abc")`   => `None`
    ///
    /// **負を `None` にする**のは、この関数の利用者（割引率・重量・個数）が
    /// いずれも非負の量だからである。符号付きの値が必要になった時点で
    /// 別の関数を用意する。0 や既定値へ倒さないのは、**打ち間違いが
    /// 「割引なし」や「重量 0」として静かに通る**のを防ぐためである。
    ///
    pub def parseScaled(scale: Int32, value: String): Option[Int64] =
        let trimmed = String.trim(value);
        if (trimmed == "" or String.startsWith(prefix = "-", trimmed) or
            String.startsWith(prefix = "+", trimmed))
            None
        else
            match String.indexOf(substr = ".", trimmed) {
                case None    => scaleWhole(scale, trimmed)
                case Some(i) =>
                    let whole = String.slice(start = 0, end = i, trimmed);
                    let rawFraction = String.slice(start = i + 1, end = String.length(trimmed), trimmed);
                    if (whole == "" or rawFraction == "")
                        // `.5` や `15.` は入力の途中に見える。曖昧なまま解釈しない
                        None
                    else
                        combine(scale, whole, rawFraction)
            }

    /// 整数のみの入力を桁数分だけ持ち上げる
    def scaleWhole(scale: Int32, whole: String): Option[Int64] =
        if (not isAllDigits(whole))
            None
        else
            Option.map(w -> w * factor(scale), Int64.fromString(whole))

    ///
    /// 整数部と小数部を合成する。
    ///
    /// 小数部は `scale` 桁で切り詰める（`12.5678` は小数第 3 位までなら `12.567`）。
    /// 四捨五入しないのは、**利用者が入力していない桁を勝手に繰り上げない**ためである。
    ///
    def combine(scale: Int32, whole: String, rawFraction: String): Option[Int64] =
        if (not isAllDigits(whole) or not isAllDigits(rawFraction))
            None
        else
            let fraction = String.padRight(scale, '0', String.takeLeft(scale, rawFraction));
            match (Int64.fromString(whole), Int64.fromString(fraction)) {
                case (Some(w), Some(f)) => Some(w * factor(scale) + f)
                case _                  => None
            }

    /// `10^scale`
    def factor(scale: Int32): Int64 =
        if (scale <= 0) 1i64 else 10i64 * factor(scale - 1)

    ///
    /// ASCII の数字のみか。
    ///
    /// `Char.isDigit` を使わないのは、Java の実装が全角数字（`１２３`）など
    /// Unicode の数字を真とするためである。`Int64.fromString` はそれらを
    /// 解釈できず、判定と変換で結論が食い違う。
    ///
    def isAllDigits(value: String): Bool =
        value != "" and String.forAll(c -> {
            let code = Char.toBmpCodePoint(c);
            code >= 0x30 and code <= 0x39
        }, value)

    ///
    /// 非負の整数として解釈する。
    ///
    /// 個数のように小数を取らない値に使う。
    ///
    pub def parseNonNegativeInt(value: String): Option[Int32] =
        let trimmed = String.trim(value);
        if (not isAllDigits(trimmed)) None else Int32.fromString(trimmed)
}
