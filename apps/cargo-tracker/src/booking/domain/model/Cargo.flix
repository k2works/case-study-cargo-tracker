///
/// Booking Context のドメインモデル。
///
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md) の
/// 「1. Booking Context」を正典とする。
///
/// **IT4 のスコープ**: `Cargo` 集約・`BookingId`・`ShipperId`・`RouteSpecification`・
/// `BookingStatus`・`CargoType`・`Dimensions`・`Quantity`・`Description`。
/// `Consignee`・`CargoItinerary`・`Leg`・`Delivery`・`Money`・`HazardousDeclaration`・
/// `TemperatureRequirement` は IT5 以降で追加する。
///
/// **効果を一切要求しない**。現在時刻も識別子も引数で受け取る。
///
mod BookingModel {

    ///
    /// 予約の状態（8 段階）。
    ///
    /// IT4 で**作れるのは `Preliminary` のみ**である。遷移は IT5 以降で実装する。
    /// 8 値すべてを定義するのは、パターンマッチの網羅性検査を効かせるためであり、
    /// 状態を追加したときにコンパイラが考慮漏れを検出する。
    ///
    pub enum BookingStatus with Eq, ToString {
        case Preliminary
        case RouteProposed
        case Confirmed
        case TrackingIssued
        case InTransit
        case Delivered
        case Settled
        case Cancelled
    }

    ///
    /// 貨物種別。
    ///
    /// `Hazardous` / `Refrigerated` は IT5（US05）で申告情報・温度条件を伴う。
    /// **IT4 では種別だけを持てる**ようにし、条件フィールドの拡張点を用意する。
    ///
    pub enum CargoType with Eq, ToString {
        case General
        case Hazardous
        case Refrigerated
    }

    ///
    /// 貨物として成立しない理由。
    ///
    /// **コンテキスト固有型**とし、Shipper Context の `ShipperError` と共有しない
    /// （`arch-lint` 規約 4）。同じ「必須項目がない」でも、業務上の意味は文脈で異なる。
    ///
    pub enum BookingError with Eq, ToString {
        case BookingIdIsRequired
        case ShipperIdIsRequired
        case ShipperNotFound
        case OriginIsRequired
        case DestinationIsRequired
        case OriginAndDestinationAreSame
        case LocationCodeIsMalformed
        case LocationIsNotAvailable
        case ArrivalDeadlineIsRequired
        case ArrivalDeadlineIsMalformed
        case WeightIsRequired
        case WeightIsNotPositive
        case CargoTypeIsMalformed
        case QuantityIsNotPositive
        case DescriptionIsTooLong
        case DimensionIsNotPositive
    }

    ///
    /// 輸送要件（出発地・目的地・到着期限）。
    ///
    /// 3 つを 1 つの値としてまとめるのは、**個別に持つと「目的地だけ変わった」ような
    /// 中途半端な状態が作れてしまう**ためである。
    ///
    pub enum RouteSpecification({
        origin = String,
        destination = String,
        arrivalDeadline = String
    })

    ///
    /// 貨物（集約ルート）。
    ///
    /// 重量は**グラム単位の整数**で保持する。`NUMERIC(10,3)`（キログラム・小数第 3 位）に
    /// 対応し、浮動小数点の誤差を料金計算へ持ち込まない（割引率と同じ方針）。
    /// 寸法も同様にミリメートル単位の整数とする。
    ///
    pub enum Cargo({
        bookingId = String,
        shipperId = String,
        routeSpecification = RouteSpecification,
        bookingStatus = BookingStatus,
        cargoType = CargoType,
        weightGrams = Int64,
        dimensions = Option[(Int32, Int32, Int32)],
        quantity = Option[Int32],
        description = Option[String]
    })

    // ---- 生成 ----

    ///
    /// 貨物を予約する（`BookCargoCommand` に対応）。
    ///
    /// 生成できるのは `Preliminary` のみである（ビジネスルール 4 の起点）。
    /// **状態を引数で受け取らない**のは、任意の状態の貨物を作れてしまうと
    /// 状態遷移のルールが意味を失うためである。
    ///
    pub def bookCargo(bookingId: String, shipperId: String, origin: String,
                      destination: String, arrivalDeadline: String, cargoType: CargoType,
                      weightGrams: Int64, dimensions: Option[(Int32, Int32, Int32)],
                      quantity: Option[Int32], description: Option[String]):
            Result[BookingError, Cargo] =
        forM (
            b <- requireBookingId(bookingId);
            s <- requireShipperId(shipperId);
            r <- requireRouteSpecification(origin, destination, arrivalDeadline);
            w <- requireWeight(weightGrams);
            d <- checkDimensions(dimensions);
            q <- checkQuantity(quantity);
            desc <- checkDescription(description)
        ) yield Cargo.Cargo({
            bookingId = b,
            shipperId = s,
            routeSpecification = r,
            bookingStatus = BookingStatus.Preliminary,
            cargoType = cargoType,
            weightGrams = w,
            dimensions = d,
            quantity = q,
            description = desc
        })

    ///
    /// 永続化された行から貨物を組み立てる。
    ///
    /// 登録時の検証をやり直さない。ルールを厳しくした瞬間に既存データが
    /// 読めなくなることを避ける。
    ///
    pub def reconstruct(bookingId: String, shipperId: String, spec: RouteSpecification,
                        status: BookingStatus, cargoType: CargoType, weightGrams: Int64,
                        dimensions: Option[(Int32, Int32, Int32)], quantity: Option[Int32],
                        description: Option[String]): Cargo =
        Cargo.Cargo({
            bookingId = bookingId, shipperId = shipperId, routeSpecification = spec,
            bookingStatus = status, cargoType = cargoType, weightGrams = weightGrams,
            dimensions = dimensions, quantity = quantity, description = description
        })

    /// 永続化された行から輸送要件を組み立てる（検証をやり直さない）
    pub def reconstructSpec(origin: String, destination: String,
                            arrivalDeadline: String): RouteSpecification =
        RouteSpecification.RouteSpecification({
            origin = origin, destination = destination, arrivalDeadline = arrivalDeadline
        })

    // ---- 検証 ----

    def requireBookingId(value: String): Result[BookingError, String] =
        if (String.trim(value) == "") Err(BookingError.BookingIdIsRequired) else Ok(value)

    def requireShipperId(value: String): Result[BookingError, String] =
        if (String.trim(value) == "") Err(BookingError.ShipperIdIsRequired) else Ok(value)

    ///
    /// 輸送要件を検証する（ビジネスルール 2）。
    ///
    /// 出発地と目的地が同一の予約は成立しない。運ぶ先が出発地なら運ぶ必要がない。
    ///
    pub def requireRouteSpecification(origin: String, destination: String,
                                      arrivalDeadline: String):
            Result[BookingError, RouteSpecification] =
        let o = String.trim(String.toUpperCase(origin));
        let d = String.trim(String.toUpperCase(destination));
        let deadline = String.trim(arrivalDeadline);
        if (o == "")
            Err(BookingError.OriginIsRequired)
        else if (d == "")
            Err(BookingError.DestinationIsRequired)
        else if (not isUnLocode(o) or not isUnLocode(d))
            Err(BookingError.LocationCodeIsMalformed)
        else if (o == d)
            Err(BookingError.OriginAndDestinationAreSame)
        else if (deadline == "")
            Err(BookingError.ArrivalDeadlineIsRequired)
        else if (not isIsoDate(deadline))
            Err(BookingError.ArrivalDeadlineIsMalformed)
        else
            Ok(RouteSpecification.RouteSpecification({
                origin = o, destination = d, arrivalDeadline = deadline
            }))

    ///
    /// UN/LOCODE の形をしているか。
    ///
    /// 国際規格は「ISO 3166-1 alpha-2（2 文字）+ ロケーションコード（3 文字）」であり、
    /// いずれも **ASCII の英大文字と数字**である。**実在するかは検査しない**
    /// （それは `location` テーブルの役目）。
    ///
    /// **`Char.isLetter` を使わない**。Java の `isLetter` は Unicode の文字全般を
    /// 真とするため、`日本大阪港` のような 5 文字の日本語が UN/LOCODE として通ってしまう
    /// （IT4 のテストで実際に検出した）。ASCII の範囲に限定する。
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
    /// 形式だけを見ていた当初の実装は `2026-02-30` や `2026-13-45` を通していた。
    /// 通した値は `CAST(? AS DATE)` で DB の例外になり、トランザクションは例外を
    /// 捕捉しないため、**利用者には 500 ですらなく応答なし（接続断）が返っていた**
    /// （IT4 のレビューで検出）。他の入力（重量・割引率）は「解釈できないものを
    /// 静かに通さない」規律で書かれており、日付だけが外れていた。
    ///
    pub def isIsoDate(value: String): Bool =
        if (String.length(value) != 10 or
            String.charAt(4, value) != '-' or String.charAt(7, value) != '-')
            false
        else
            let yearText = String.slice(start = 0, end = 4, value);
            let monthText = String.slice(start = 5, end = 7, value);
            let dayText = String.slice(start = 8, end = 10, value);
            if (not (isAsciiDigits(yearText) and isAsciiDigits(monthText) and isAsciiDigits(dayText)))
                false
            else
                match (Int32.fromString(yearText), Int32.fromString(monthText),
                       Int32.fromString(dayText)) {
                    case (Some(y), Some(m), Some(d)) => isValidDate(y, m, d)
                    case _                           => false
                }

    /// ASCII の数字のみか（`Char.isDigit` は全角数字も真とする）
    def isAsciiDigits(value: String): Bool =
        value != "" and String.forAll(c -> {
            let code = Char.toBmpCodePoint(c);
            code >= 0x30 and code <= 0x39
        }, value)

    ///
    /// 暦として存在する日付か。
    ///
    /// うるう年はグレゴリオ暦の規則（4 の倍数かつ 100 の倍数でない、または 400 の倍数）に従う。
    ///
    pub def isValidDate(year: Int32, month: Int32, day: Int32): Bool =
        month >= 1 and month <= 12 and day >= 1 and day <= daysInMonth(year, month)

    /// 月の日数
    def daysInMonth(year: Int32, month: Int32): Int32 = match month {
        case 2                        => if (isLeapYear(year)) 29 else 28
        case 4 => 30
        case 6 => 30
        case 9 => 30
        case 11 => 30
        case _                        => 31
    }

    /// うるう年か
    pub def isLeapYear(year: Int32): Bool =
        (Int32.remainder(year, 4) == 0 and Int32.remainder(year, 100) != 0) or
        Int32.remainder(year, 400) == 0

    ///
    /// 重量を検証する。
    ///
    /// 0 以下を弾くのは、**重量 0 の貨物は運ぶ対象になりえない**ためである。
    /// 料金は重量に比例するため、0 を通すと料金 0 の予約が作れる。
    ///
    def requireWeight(grams: Int64): Result[BookingError, Int64] =
        if (grams <= 0i64) Err(BookingError.WeightIsNotPositive) else Ok(grams)

    def checkQuantity(value: Option[Int32]): Result[BookingError, Option[Int32]] =
        match value {
            case None    => Ok(None)
            case Some(n) => if (n <= 0) Err(BookingError.QuantityIsNotPositive) else Ok(Some(n))
        }

    def checkDimensions(value: Option[(Int32, Int32, Int32)]):
            Result[BookingError, Option[(Int32, Int32, Int32)]] =
        match value {
            case None => Ok(None)
            case Some((l, w, h)) =>
                if (l <= 0 or w <= 0 or h <= 0)
                    Err(BookingError.DimensionIsNotPositive)
                else
                    Ok(Some((l, w, h)))
        }

    def checkDescription(value: Option[String]): Result[BookingError, Option[String]] =
        match value {
            case None => Ok(None)
            case Some(raw) =>
                let trimmed = String.trim(raw);
                if (trimmed == "")
                    Ok(None)
                else if (String.length(trimmed) > maxDescriptionLength())
                    Err(BookingError.DescriptionIsTooLong)
                else
                    Ok(Some(trimmed))
        }

    // ---- 予約番号 ----

    ///
    /// 予約番号を生成する。
    ///
    /// `BK-` + 識別子の先頭 8 文字（大文字）。`shipper_code` と同じ形であり、
    /// **同じ衝突の限界を持つ**（ドメインモデル設計のビジネスルール 5 の注記を参照）。
    ///
    pub def generateBookingId(rawId: String): String =
        let head = if (String.length(rawId) >= 8)
                       String.slice(start = 0, end = 8, rawId)
                   else
                       rawId;
        "BK-" + String.toUpperCase(head)

    // ---- 永続化値との対応 ----

    pub def statusToPersisted(s: BookingStatus): String = match s {
        case BookingStatus.Preliminary    => "PRELIMINARY"
        case BookingStatus.RouteProposed  => "ROUTE_PROPOSED"
        case BookingStatus.Confirmed      => "CONFIRMED"
        case BookingStatus.TrackingIssued => "TRACKING_ISSUED"
        case BookingStatus.InTransit      => "IN_TRANSIT"
        case BookingStatus.Delivered      => "DELIVERED"
        case BookingStatus.Settled        => "SETTLED"
        case BookingStatus.Cancelled      => "CANCELLED"
    }

    /// 未知の値は `None`。既定値（仮受付）へ倒すと、確定済みの予約が未確定に見える
    pub def statusFromPersisted(value: String): Option[BookingStatus] = match value {
        case "PRELIMINARY"     => Some(BookingStatus.Preliminary)
        case "ROUTE_PROPOSED"  => Some(BookingStatus.RouteProposed)
        case "CONFIRMED"       => Some(BookingStatus.Confirmed)
        case "TRACKING_ISSUED" => Some(BookingStatus.TrackingIssued)
        case "IN_TRANSIT"      => Some(BookingStatus.InTransit)
        case "DELIVERED"       => Some(BookingStatus.Delivered)
        case "SETTLED"         => Some(BookingStatus.Settled)
        case "CANCELLED"       => Some(BookingStatus.Cancelled)
        case _                 => None
    }

    ///
    /// 予約状態の表示名（画面に列挙値を出さない）。
    ///
    /// **[UI 設計](../../../../../../docs/design/ui_design.md) の
    /// 「BookingStatus バッジ定義」が正典**である。ここで独自の言い回しを作らない。
    ///
    pub def statusLabel(s: BookingStatus): String = match s {
        case BookingStatus.Preliminary    => "仮受付"
        case BookingStatus.RouteProposed  => "経路提案済"
        case BookingStatus.Confirmed      => "確認済"
        case BookingStatus.TrackingIssued => "追跡番号発行済"
        case BookingStatus.InTransit      => "輸送中"
        case BookingStatus.Delivered      => "配送完了"
        case BookingStatus.Settled        => "精算完了"
        case BookingStatus.Cancelled      => "キャンセル"
    }

    ///
    /// 予約状態のバッジ配色（UI 設計のバッジ定義に対応）。
    ///
    pub def statusBadgeClass(s: BookingStatus): String = match s {
        case BookingStatus.Preliminary    => "badge bg-warning text-dark"
        case BookingStatus.RouteProposed  => "badge bg-primary"
        case BookingStatus.Confirmed      => "badge bg-success"
        case BookingStatus.TrackingIssued => "badge bg-info text-dark"
        case BookingStatus.InTransit      => "badge bg-primary"
        case BookingStatus.Delivered      => "badge bg-success"
        case BookingStatus.Settled        => "badge bg-secondary"
        case BookingStatus.Cancelled      => "badge bg-danger"
    }

    pub def cargoTypeToPersisted(t: CargoType): String = match t {
        case CargoType.General      => "GENERAL"
        case CargoType.Hazardous    => "HAZARDOUS"
        case CargoType.Refrigerated => "REFRIGERATED"
    }

    pub def cargoTypeFromPersisted(value: String): Option[CargoType] = match value {
        case "GENERAL"      => Some(CargoType.General)
        case "HAZARDOUS"    => Some(CargoType.Hazardous)
        case "REFRIGERATED" => Some(CargoType.Refrigerated)
        case _              => None
    }

    /// 貨物種別の表示名
    pub def cargoTypeLabel(t: CargoType): String = match t {
        case CargoType.General      => "一般貨物"
        case CargoType.Hazardous    => "危険物"
        case CargoType.Refrigerated => "冷凍・冷蔵貨物"
    }

    /// 取り扱う貨物種別の一覧（画面の選択肢と検証で共有する）
    pub def allCargoTypes(): List[CargoType] =
        CargoType.General :: CargoType.Hazardous :: CargoType.Refrigerated :: Nil

    // ---- 参照 ----

    pub def bookingIdOf(c: Cargo): String =
        let Cargo.Cargo(x) = c;
        x#bookingId

    pub def shipperIdOf(c: Cargo): String =
        let Cargo.Cargo(x) = c;
        x#shipperId

    pub def routeSpecificationOf(c: Cargo): RouteSpecification =
        let Cargo.Cargo(x) = c;
        x#routeSpecification

    pub def bookingStatusOf(c: Cargo): BookingStatus =
        let Cargo.Cargo(x) = c;
        x#bookingStatus

    pub def cargoTypeOf(c: Cargo): CargoType =
        let Cargo.Cargo(x) = c;
        x#cargoType

    pub def weightGramsOf(c: Cargo): Int64 =
        let Cargo.Cargo(x) = c;
        x#weightGrams

    pub def dimensionsOf(c: Cargo): Option[(Int32, Int32, Int32)] =
        let Cargo.Cargo(x) = c;
        x#dimensions

    pub def quantityOf(c: Cargo): Option[Int32] =
        let Cargo.Cargo(x) = c;
        x#quantity

    pub def descriptionOf(c: Cargo): Option[String] =
        let Cargo.Cargo(x) = c;
        x#description

    pub def originOf(spec: RouteSpecification): String =
        let RouteSpecification.RouteSpecification(x) = spec;
        x#origin

    pub def destinationOf(spec: RouteSpecification): String =
        let RouteSpecification.RouteSpecification(x) = spec;
        x#destination

    pub def arrivalDeadlineOf(spec: RouteSpecification): String =
        let RouteSpecification.RouteSpecification(x) = spec;
        x#arrivalDeadline

    // ---- 表示 ----

    ///
    /// 重量をキログラム表記へ変換する。
    ///
    /// 内部はグラム。そのまま出すと `1200000` と表示され、桁を読み違える。
    ///
    pub def weightKilograms(grams: Int64): String =
        let whole = grams / 1000i64;
        let fraction = grams - whole * 1000i64;
        if (fraction == 0i64)
            "${whole}"
        else
            let padded = if (fraction < 10i64) "00${fraction}"
                         else if (fraction < 100i64) "0${fraction}"
                         else "${fraction}";
            "${whole}.${padded}"

    // ---- 制限値 ----

    /// `cargo.description` の桁数と一致させる
    def maxDescriptionLength(): Int32 = 500
}
