///
/// 金額（共有カーネル。IT8・US01）。
///
/// # 整数の最小単位で持つ
///
/// [ADR-0006](../../../../../../docs/adr/ADR-0006-fixed-point-money.md) のとおり、
/// **金額は浮動小数点で持たない**。`0.1 + 0.2 != 0.3` になる表現で運賃を積み上げると、
/// 合計が 1 円ずれる。ずれても例外は出ず、**請求書を受け取った荷主だけが気付く**。
///
/// 単位は**銭**（1/100 円）である。円ではなく銭にするのは、
/// `estimate` / `route_candidate` の金額列が `NUMERIC(12,2)` であり、
/// **DB と往復しても値が変わらない**必要があるためである。
/// 重量（グラム）・温度（千分の一度）・割引率（万分率）と同じ方針である。
///
/// # 通貨を持たない
///
/// 本システムの見積・請求は**円建てのみ**である（[要件定義](../../../../../../docs/requirements/requirements.md)）。
/// 通貨を持たせると、足し算のたびに「通貨が違う場合」の枝が要る——
/// **起こらない場合の分岐は、テストされないまま残る**。
/// 多通貨が要るようになった時点で型を分ける。
///
mod SharedMoney {

    /// 金額（銭単位。1 円 = 100 銭）
    pub enum Money(Int64) with Eq, Order, ToString

    /// 銭から組み立てる
    pub def ofSen(sen: Int64): Money = Money.Money(sen)

    /// 円から組み立てる
    pub def ofYen(yen: Int64): Money = Money.Money(yen * 100i64)

    pub def senOf(m: Money): Int64 = let Money.Money(v) = m; v

    pub def zero(): Money = Money.Money(0i64)

    pub def add(a: Money, b: Money): Money = Money.Money(senOf(a) + senOf(b))

    /// 金額を整数倍する（数量・区間数などを掛ける）
    pub def times(m: Money, factor: Int64): Money = Money.Money(senOf(m) * factor)

    ///
    /// 割合を掛ける（万分率。`15000` = 150.00%）。
    ///
    /// **割増率も万分率の整数で受け取る**。`1.5` を浮動小数点で掛けると、
    /// 危険物の割増だけ 1 銭ずれる可能性がある。割引率（`shipper.discount_rate`）と
    /// 同じ表現に揃えてあるため、両者を混ぜても表現が変わらない。
    ///
    /// **端数は切り上げる**。切り捨てると、積み上げた合計が
    /// 個別の合計より小さくなり、「明細を足しても合計に合わない」ことが起きる。
    /// 切り上げなら差は最大 1 銭で、かつ常に事業者側に不利にならない方向である。
    ///
    pub def scaleByBasisPoints(m: Money, basisPoints: Int64): Money =
        let numerator = senOf(m) * basisPoints;
        Money.Money(divideRoundingUp(numerator, 10000i64))

    ///
    /// 切り上げ除算。
    ///
    /// **負数を扱わない**。金額に負が現れるのは返金であり、本 IT の範囲外である。
    /// 現れた場合は `bug!` で落とす——静かに丸めると、返金額が 1 銭増える。
    ///
    def divideRoundingUp(numerator: Int64, denominator: Int64): Int64 =
        if (numerator < 0i64)
            bug!("負の金額は扱わない: ${numerator}")
        else
            (numerator + denominator - 1i64) / denominator

    ///
    /// 円に丸めて表示する（`1,234 円`）。
    ///
    /// **切り上げる**。見積は概算であり、実額が見積を上回るより
    /// 下回るほうが荷主との関係で問題が小さい。
    ///
    pub def toYenText(m: Money): String =
        let yen = divideRoundingUp(senOf(m), 100i64);
        "${groupDigits(yen)} 円"

    /// 3 桁ごとに区切る（`1234567` → `1,234,567`）
    pub def groupDigits(value: Int64): String =
        let digits = Int64.toString(value);
        digits
            |> String.toList
            |> List.reverse
            |> List.zipWithIndex
            |> List.map(match (i, c) -> if (i > 0 and Int32.remainder(i, 3) == 0) "${c}," else "${c}")
            |> List.reverse
            |> List.join("")

    ///
    /// DB へ入れる十進数（円単位・小数 2 桁）へ写す。
    ///
    /// **`Float64` を通さない**。`BigDecimal` へ整数のまま渡し、
    /// 小数点を 2 桁戻す（重量・温度と同じ形）。
    ///
    pub def toDecimalString(m: Money): String =
        let sen = senOf(m);
        let yen = sen / 100i64;
        let fraction = Int64.remainder(sen, 100i64);
        let padded = if (fraction < 10i64) "0${fraction}" else "${fraction}";
        "${yen}.${padded}"
}
