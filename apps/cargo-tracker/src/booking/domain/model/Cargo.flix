///
/// Booking Context のドメインモデル。
///
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md) の
/// 「1. Booking Context」を正典とする。
///
/// **実装済み**: `Cargo` 集約・`BookingId`・`ShipperId`・`RouteSpecification`・
/// `BookingStatus`・`CargoType`・寸法・個数・品名（IT4）、
/// `HazardousDeclaration`・`TemperatureRequirement`・`assignToRouting`（IT5）。
/// `Consignee`・`CargoItinerary`・`Leg`・`Delivery`・`Money` は IT7 以降で追加する。
///
/// **効果を一切要求しない**。現在時刻も識別子も引数で受け取る。
///
mod BookingModel {

    ///
    /// 予約の状態（8 段階）。
    ///
    /// 生成できるのは `Preliminary` のみである（`bookCargo`）。
    /// 遷移は IT5 で `Preliminary -> RouteProposed`（`assignToRouting`）まで実装した。
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
        case ArrivalDeadlineIsInPast
        case WeightIsRequired
        case WeightIsNotPositive
        case CargoTypeIsMalformed
        case QuantityIsNotPositive
        case DescriptionIsTooLong
        case DimensionIsNotPositive
        // ---- 危険物申告・温度管理条件（IT5・US05） ----
        case HazardousClassIsRequired
        case UnNumberIsMalformed
        case ProperShippingNameIsRequired
        case ProperShippingNameIsTooLong
        case TemperatureIsMalformed
        case TemperatureRangeIsInverted
        case TemperatureUnitIsMalformed
        case HazardousDeclarationIsRequired
        case HazardousDeclarationIsNotApplicable
        case TemperatureRequirementIsRequired
        case TemperatureRequirementIsNotApplicable
        // ---- 状態遷移（IT5・US06） ----
        case CargoIsNotPreliminary
        // ---- 旅程の割り当て（IT9・US09/US11） ----
        case CargoIsNotUnderRouting
        case ItineraryIsEmpty
        case ItineraryDoesNotMatchRouteSpecification
        case ItineraryIsDisconnected
    }

    // ================================================================
    // 旅程（US09・US11。IT9）
    // ================================================================

    ///
    /// 輸送区間。
    ///
    /// **Routing の `RouteLeg` とは別の型**である（`arch-lint` 規約 4）。
    /// 同じ航海番号を指していても、Routing にとっては「候補の一部」、
    /// Booking にとっては「この貨物が確定した経路の一部」であり、決めるものが違う。
    ///
    /// 場所は `String`（UN/LOCODE）で持つ。`Location` 共有カーネル型は
    /// 実装に存在せず、IT1 から `String` + `LocationCatalog` で一貫している。
    ///
    pub enum Leg({
        voyageNumber = String,
        loadLocation = String,
        unloadLocation = String,
        loadTime = String,
        unloadTime = String
    })

    pub def leg(voyageNumber: String, loadLocation: String, unloadLocation: String,
                loadTime: String, unloadTime: String): Leg =
        Leg.Leg({
            voyageNumber = voyageNumber, loadLocation = loadLocation,
            unloadLocation = unloadLocation, loadTime = loadTime, unloadTime = unloadTime
        })

    pub def legVoyageNumberOf(l: Leg): String = let Leg.Leg(x) = l; x#voyageNumber
    pub def legLoadLocationOf(l: Leg): String = let Leg.Leg(x) = l; x#loadLocation
    pub def legUnloadLocationOf(l: Leg): String = let Leg.Leg(x) = l; x#unloadLocation
    pub def legLoadTimeOf(l: Leg): String = let Leg.Leg(x) = l; x#loadTime
    pub def legUnloadTimeOf(l: Leg): String = let Leg.Leg(x) = l; x#unloadTime

    /// 旅程（1 つ以上の区間）
    pub enum CargoItinerary({ legs = List[Leg] })

    pub def itineraryOf(legs: List[Leg]): CargoItinerary =
        CargoItinerary.CargoItinerary({ legs = legs })

    pub def legsOf(i: CargoItinerary): List[Leg] =
        let CargoItinerary.CargoItinerary(x) = i; x#legs

    ///
    /// 到着予定時刻（最終区間の荷降時刻）。
    ///
    /// **区間が空の旅程は集約に入らない**（`attachItinerary` が拒む）ため、
    /// ここへ来る旅程は必ず 1 つ以上の区間を持つ。
    ///
    pub def expectedArrivalTimeOf(i: CargoItinerary): String =
        legsOf(i) |> List.last |> Option.map(legUnloadTimeOf) |> Option.getWithDefault("")

    ///
    /// 配送状況。
    ///
    /// **設計どおり `Cargo` は `Delivery` を持ち、`Delivery` が経路状態を持つ**。
    /// `Cargo` に `routingStatus` を直接生やすと、後から輸送状態と
    /// 最終荷役イベントを足すときに置き場所が無くなる。
    ///
    /// 本 IT で埋まるのは `routingStatus` だけである。
    ///
    pub enum Delivery({ routingStatus = SharedRoutingStatus.RoutingStatus })

    pub def deliveryOf(routingStatus: SharedRoutingStatus.RoutingStatus): Delivery =
        Delivery.Delivery({ routingStatus = routingStatus })

    pub def routingStatusOf(d: Delivery): SharedRoutingStatus.RoutingStatus =
        let Delivery.Delivery(x) = d; x#routingStatus

    // ================================================================
    // 危険物申告・温度管理条件（US05）
    // ================================================================

    ///
    /// 危険物申告。
    ///
    /// 危険物クラス・UN 番号・正式輸送品名の 3 つで 1 つの申告になる。
    /// 個別に持つと「UN 番号だけある」状態が作れてしまい、書類として成立しない。
    ///
    pub enum HazardousDeclaration({
        hazardousClass = String,
        unNumber = String,
        properShippingName = String
    })

    /// 温度の単位
    pub enum TemperatureUnit with Eq, ToString {
        case Celsius
        case Fahrenheit
    }

    ///
    /// 温度管理条件。
    ///
    /// 温度は**千分の一度の整数**で保持する。浮動小数点で持つと、料金や
    /// 積載判定に誤差が混ざる。割引率（万分率）・重量（グラム）と同じ方針である。
    ///
    pub enum TemperatureRequirement({
        minTemperatureMilli = Int64,
        maxTemperatureMilli = Int64,
        unit = TemperatureUnit
    })

    ///
    /// 危険物申告を組み立てる。
    ///
    /// UN 番号の形式を検証するのは、**書類と積載制限がこの番号で決まる**ためである。
    /// 誤った番号のまま輸送すると、法令上の要件を満たさない貨物が動く。
    ///
    pub def requireHazardousDeclaration(hazardousClass: String, unNumber: String,
                                        properShippingName: String):
            Result[BookingError, HazardousDeclaration] =
        let cls = String.trim(hazardousClass);
        let un = String.trim(unNumber);
        let name = String.trim(properShippingName);
        if (cls == "")
            Err(BookingError.HazardousClassIsRequired)
        else if (not isUnNumber(un))
            Err(BookingError.UnNumberIsMalformed)
        else if (name == "")
            Err(BookingError.ProperShippingNameIsRequired)
        else if (String.length(name) > 200)
            // 上限は DB 列（`VARCHAR(200)`）に合わせる。アプリが受け入れた値を
            // DB が拒む状態は、利用者から見て「登録したのに保存されていない」
            // という最も分かりにくい壊れ方になる
            Err(BookingError.ProperShippingNameIsTooLong)
        else
            Ok(HazardousDeclaration.HazardousDeclaration({
                hazardousClass = cls, unNumber = un, properShippingName = name
            }))

    ///
    /// UN 番号の形式か（`UN` + 数字 4 桁）。
    ///
    /// `Char.isDigit` を使わない。**全角数字を通す**（IT4 で `日本大阪港` が
    /// UN/LOCODE として通った原因と同じ）。
    ///
    pub def isUnNumber(value: String): Bool =
        String.length(value) == 6 and
        String.startsWith(prefix = "UN", value) and
        String.slice(start = 2, end = 6, value)
            |> String.toList
            |> List.forAll(c -> c >= '0' and c <= '9')

    ///
    /// 温度管理条件を組み立てる。
    ///
    /// 上下限の逆転を拒む。通すと**どの温度でも条件を満たさない**貨物が
    /// 登録でき、荷役側は「条件を満たす保管場所がない」としか分からない。
    ///
    /// **温度は千分の一度の整数で受け取る**。文字列の解釈は境界（interfaces 層）の
    /// 責務であり、ドメインは数として成立するかだけを見る。割引率（万分率）・
    /// 重量（グラム）も同じ形にしてある。
    pub def requireTemperatureRequirement(minTemperatureMilli: Int64,
                                          maxTemperatureMilli: Int64,
                                          unit: String):
            Result[BookingError, TemperatureRequirement] =
        match temperatureUnitFromPersisted(String.trim(unit)) {
            case None => Err(BookingError.TemperatureUnitIsMalformed)
            case Some(u) =>
                if (minTemperatureMilli > maxTemperatureMilli)
                    Err(BookingError.TemperatureRangeIsInverted)
                else
                    Ok(TemperatureRequirement.TemperatureRequirement({
                        minTemperatureMilli = minTemperatureMilli,
                        maxTemperatureMilli = maxTemperatureMilli,
                        unit = u
                    }))
        }

    // ---- 参照 ----

    pub def hazardousClassOf(d: HazardousDeclaration): String =
        let HazardousDeclaration.HazardousDeclaration(x) = d;
        x#hazardousClass

    pub def unNumberOf(d: HazardousDeclaration): String =
        let HazardousDeclaration.HazardousDeclaration(x) = d;
        x#unNumber

    pub def properShippingNameOf(d: HazardousDeclaration): String =
        let HazardousDeclaration.HazardousDeclaration(x) = d;
        x#properShippingName

    pub def minTemperatureMilliOf(t: TemperatureRequirement): Int64 =
        let TemperatureRequirement.TemperatureRequirement(x) = t;
        x#minTemperatureMilli

    pub def maxTemperatureMilliOf(t: TemperatureRequirement): Int64 =
        let TemperatureRequirement.TemperatureRequirement(x) = t;
        x#maxTemperatureMilli

    pub def temperatureUnitOf(t: TemperatureRequirement): TemperatureUnit =
        let TemperatureRequirement.TemperatureRequirement(x) = t;
        x#unit

    /// 値から組み立てる（永続化からの復元用）
    pub def reconstructTemperature(minMilli: Int64, maxMilli: Int64,
                                   unit: TemperatureUnit): TemperatureRequirement =
        TemperatureRequirement.TemperatureRequirement({
            minTemperatureMilli = minMilli, maxTemperatureMilli = maxMilli, unit = unit
        })

    /// 値から組み立てる（永続化からの復元用）
    pub def reconstructHazardous(hazardousClass: String, unNumber: String,
                                 properShippingName: String): HazardousDeclaration =
        HazardousDeclaration.HazardousDeclaration({
            hazardousClass = hazardousClass, unNumber = unNumber,
            properShippingName = properShippingName
        })

    // ---- 永続化値との対応 ----

    pub def temperatureUnitToPersisted(u: TemperatureUnit): String = match u {
        case TemperatureUnit.Celsius    => "CELSIUS"
        case TemperatureUnit.Fahrenheit => "FAHRENHEIT"
    }

    ///
    /// 永続化値から温度単位を復元する。
    ///
    /// **未知の値を既定値へ倒さない**。華氏を摂氏として読み出すと
    /// `-13°F`（= -25°C）が `-13°C` になる。12 度の差は冷凍と冷蔵の違いであり、
    /// 貨物を傷める。
    ///
    pub def temperatureUnitFromPersisted(value: String): Option[TemperatureUnit] =
        match value {
            case "CELSIUS"    => Some(TemperatureUnit.Celsius)
            case "FAHRENHEIT" => Some(TemperatureUnit.Fahrenheit)
            case _            => None
        }

    // ---- 表示 ----

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
        description = Option[String],
        hazardousDeclaration = Option[HazardousDeclaration],
        temperatureRequirement = Option[TemperatureRequirement],
        // 旅程と配送状況（IT9・US09/US11）。旅程は未割り当てなら `None`
        cargoItinerary = Option[CargoItinerary],
        delivery = Delivery
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
                      destination: String, arrivalDeadline: String, today: String,
                      cargoType: CargoType,
                      weightGrams: Int64, dimensions: Option[(Int32, Int32, Int32)],
                      quantity: Option[Int32], description: Option[String],
                      hazardousDeclaration: Option[HazardousDeclaration],
                      temperatureRequirement: Option[TemperatureRequirement]):
            Result[BookingError, Cargo] =
        forM (
            b <- requireBookingId(bookingId);
            s <- requireShipperId(shipperId);
            r <- requireRouteSpecification(origin, destination, arrivalDeadline);
            _ <- checkDeadlineIsNotPast(today, arrivalDeadline);
            w <- requireWeight(weightGrams);
            d <- checkDimensions(dimensions);
            q <- checkQuantity(quantity);
            desc <- checkDescription(description);
            hz <- checkSpecialRequirements(cargoType, hazardousDeclaration, temperatureRequirement)
        ) yield Cargo.Cargo({
            bookingId = b,
            shipperId = s,
            routeSpecification = r,
            bookingStatus = BookingStatus.Preliminary,
            cargoType = cargoType,
            weightGrams = w,
            dimensions = d,
            quantity = q,
            description = desc,
            hazardousDeclaration = fst(hz),
            temperatureRequirement = snd(hz),
            // 予約した時点では経路は決まっていない
            cargoItinerary = None,
            delivery = deliveryOf(SharedRoutingStatus.RoutingStatus.NotRouted)
        })

    ///
    /// 到着期限が過去でないことを確かめる。
    ///
    /// **現在日は引数で受け取る**。ドメインは効果を要求しない（開発戦略・中盤の規律）。
    ///
    /// **当日は許す**。「今日中に着く」は業務上ありうる依頼であり、
    /// 境界を閉じると当日便が予約できない。
    ///
    /// ISO 8601（`YYYY-MM-DD`）は辞書順と日付順が一致するため文字列比較でよい。
    /// 形式は `requireRouteSpecification` が先に検証しており、ここへ来る値は
    /// 必ずその形である（順序が逆だと「過去の日付です」と言われて日付を直しても
    /// 通らず、利用者は何が悪いのか分からなくなる）。
    ///
    def checkDeadlineIsNotPast(today: String, arrivalDeadline: String):
            Result[BookingError, Unit] =
        if (String.trim(arrivalDeadline) < String.trim(today))
            Err(BookingError.ArrivalDeadlineIsInPast)
        else
            Ok()

    ///
    /// 貨物種別と特別要件の対応を検証する（ビジネスルール 7・8）。
    ///
    /// **不変条件は集約が守る**。画面側だけで必須にすると、別の入口
    /// （API・バッチ・テスト）から申告なしの危険物が入りうる。
    ///
    /// 種別に対応しない要件を**付けられないことも**守る。付けられると、
    /// 一般貨物に危険物の書類が付いた状態が作れ、取扱港・積載制限の判定が
    /// 種別と申告で食い違う。
    ///
    def checkSpecialRequirements(cargoType: CargoType,
                                 hazardous: Option[HazardousDeclaration],
                                 temperature: Option[TemperatureRequirement]):
            Result[BookingError, (Option[HazardousDeclaration], Option[TemperatureRequirement])] =
        match cargoType {
            case CargoType.Hazardous =>
                if (Option.isEmpty(hazardous))
                    Err(BookingError.HazardousDeclarationIsRequired)
                else if (not Option.isEmpty(temperature))
                    Err(BookingError.TemperatureRequirementIsNotApplicable)
                else
                    Ok((hazardous, None))
            case CargoType.Refrigerated =>
                if (Option.isEmpty(temperature))
                    Err(BookingError.TemperatureRequirementIsRequired)
                else if (not Option.isEmpty(hazardous))
                    Err(BookingError.HazardousDeclarationIsNotApplicable)
                else
                    Ok((None, temperature))
            case CargoType.General =>
                if (not Option.isEmpty(hazardous))
                    Err(BookingError.HazardousDeclarationIsNotApplicable)
                else if (not Option.isEmpty(temperature))
                    Err(BookingError.TemperatureRequirementIsNotApplicable)
                else
                    Ok((None, None))
        }

    ///
    /// 永続化された行から貨物を組み立てる。
    ///
    /// 登録時の検証をやり直さない。ルールを厳しくした瞬間に既存データが
    /// 読めなくなることを避ける。
    ///
    pub def reconstruct(bookingId: String, shipperId: String, spec: RouteSpecification,
                        status: BookingStatus, cargoType: CargoType, weightGrams: Int64,
                        dimensions: Option[(Int32, Int32, Int32)], quantity: Option[Int32],
                        description: Option[String],
                        hazardousDeclaration: Option[HazardousDeclaration],
                        temperatureRequirement: Option[TemperatureRequirement],
                        cargoItinerary: Option[CargoItinerary],
                        routingStatus: SharedRoutingStatus.RoutingStatus): Cargo =
        Cargo.Cargo({
            bookingId = bookingId, shipperId = shipperId, routeSpecification = spec,
            bookingStatus = status, cargoType = cargoType, weightGrams = weightGrams,
            dimensions = dimensions, quantity = quantity, description = description,
            hazardousDeclaration = hazardousDeclaration,
            temperatureRequirement = temperatureRequirement,
            cargoItinerary = cargoItinerary, delivery = deliveryOf(routingStatus)
        })

    // ================================================================
    // 状態遷移（US06）
    // ================================================================

    ///
    /// 経路設計へ引き渡す（`Preliminary` -> `RouteProposed`）。
    ///
    /// ビジネスルール 4 の 2 番目の遷移。**状態だけを変える**。
    /// 他の値まで作り直すと、遷移のたびに情報が失われる余地が生まれる。
    ///
    /// 仮受付以外からの遷移を拒むのは、二重の依頼や取り消し済み貨物の
    /// 経路設計を防ぐためである。連打や再送信で 2 回届くことは普通に起きる。
    ///
    pub def assignToRouting(c: Cargo): Result[BookingError, Cargo] =
        if (not canAssignToRouting(c))
            Err(BookingError.CargoIsNotPreliminary)
        else
            let Cargo.Cargo(x) = c;
            Ok(Cargo.Cargo({ bookingStatus = BookingStatus.RouteProposed | x }))

    // ---- 旅程の割り当て（US09・US11。IT9） ----

    ///
    /// 確定した経路を割り当てる。
    ///
    /// # ここが不変条件の唯一の関門である
    ///
    /// 呼び出し元は Routing の画面である（[ADR-0011](../../../../../../docs/adr/ADR-0011-routing-writes-booking-through-its-aggregate.md)）。
    /// **呼び出し元が誰であっても、ここを通る限り壊れた旅程は入らない**——
    /// これが「テーブルへ直接書かない」ことの実利である。
    ///
    /// 検証するのは 4 つ。
    ///
    /// 1. **経路設計中であること**。仮受付（まだ引き渡されていない）や
    ///    確定済み（荷主が承認済み）に後から別の経路を入れない
    /// 2. **区間が 1 つ以上あること**。空の旅程は「経路が決まった」と言えない
    /// 3. **端点が要件と一致すること**。大阪発ロサンゼルス行きの予約に
    ///    東京発の旅程を割り当てない
    /// 4. **区間が連結していること**。前の荷降港と次の積込港が違えば、
    ///    貨物は途中で降ろされたまま次の船に乗らない
    ///
    /// **`BookingStatus` は変えない**。経路が決まっても予約は経路設計中のままであり、
    /// 確定するのは営業が荷主の承認を得たとき（US13）である。
    /// 変えるのは `RoutingStatus` だけ——2 つの軸は独立に動く。
    ///
    pub def attachItinerary(c: Cargo, itinerary: CargoItinerary):
            Result[BookingError, Cargo] =
        let legs = legsOf(itinerary);
        let spec = routeSpecificationOf(c);
        if (bookingStatusOf(c) != BookingStatus.RouteProposed)
            Err(BookingError.CargoIsNotUnderRouting)
        else if (List.isEmpty(legs))
            Err(BookingError.ItineraryIsEmpty)
        else if (not endpointsMatch(spec, legs))
            Err(BookingError.ItineraryDoesNotMatchRouteSpecification)
        else if (not legsAreConnected(legs))
            Err(BookingError.ItineraryIsDisconnected)
        else
            let Cargo.Cargo(x) = c;
            Ok(Cargo.Cargo({
                cargoItinerary = Some(itinerary),
                delivery = deliveryOf(SharedRoutingStatus.RoutingStatus.Routed)
                | x
            }))

    ///
    /// 旅程の端点が要件と一致するか。
    ///
    /// **最初の積込港と最後の荷降港**を見る。途中の寄港地は要件に含まれない。
    ///
    def endpointsMatch(spec: RouteSpecification, legs: List[Leg]): Bool =
        match (List.head(legs), List.last(legs)) {
            case (Some(first), Some(last)) =>
                legLoadLocationOf(first) == originOf(spec) and
                legUnloadLocationOf(last) == destinationOf(spec)
            case _ => false
        }

    ///
    /// 区間が連結しているか（前の荷降港＝次の積込港）。
    ///
    /// **時刻の前後は見ない**。それは経路探索（Routing）の責務であり、
    /// ここで二重に持つと片方だけ直したときに食い違う。
    /// Booking が守るのは「貨物が物理的につながっているか」だけである。
    ///
    def legsAreConnected(legs: List[Leg]): Bool =
        match legs {
            case Nil            => true
            case _ :: Nil       => true
            case a :: b :: rest =>
                legUnloadLocationOf(a) == legLoadLocationOf(b) and
                legsAreConnected(b :: rest)
        }

    ///
    /// 旅程を外す（経路の選び直し・差し戻し）。
    ///
    /// **経路状態も戻す**。旅程だけ消して `ROUTED` のままにすると、
    /// 「経路提案済だが経路が無い」という読めない状態になる（壊れ方の観点・軸 4）。
    ///
    pub def detachItinerary(c: Cargo): Cargo =
        let Cargo.Cargo(x) = c;
        Cargo.Cargo({
            cargoItinerary = None,
            delivery = deliveryOf(SharedRoutingStatus.RoutingStatus.NotRouted)
            | x
        })

    ///
    /// 復元時に旅程を差し込む（永続化からの再構成用）。
    ///
    /// **検証しない**。`reconstruct` と同じ理由で、ルールを厳しくした瞬間に
    /// 既存データが読めなくなることを避ける。
    ///
    pub def withItinerary(c: Cargo, itinerary: CargoItinerary): Cargo =
        let Cargo.Cargo(x) = c;
        Cargo.Cargo({ cargoItinerary = Some(itinerary) | x })

    /// 旅程が割り当て済みか（画面がボタンの活性を決めるのに使う）
    pub def isRouted(c: Cargo): Bool =
        routingStatusOf(deliveryOfCargo(c)) == SharedRoutingStatus.RoutingStatus.Routed

    ///
    /// 経路設計へ引き渡せる状態か。
    ///
    /// 画面はボタンの活性・非活性の判断にこれを使う。**判断を画面に持たせない**。
    /// 持たせると「押せるのに失敗する」「押せないが実は可能」がすぐに生まれる。
    ///
    pub def canAssignToRouting(c: Cargo): Bool =
        bookingStatusOf(c) == BookingStatus.Preliminary

    ///
    /// 引き渡せない理由（引き渡せるなら `None`）。
    ///
    /// 「できません」だけでは、利用者は待てばよいのか誰かに聞くのか分からない。
    ///
    pub def assignmentBlockedReason(c: Cargo): Option[String] =
        if (canAssignToRouting(c))
            None
        else
            Some("この予約は「${statusLabel(bookingStatusOf(c))}」のため、経路設計へ引き渡せません")

    /// 危険物申告（保持していれば）
    pub def hazardousDeclarationOf(c: Cargo): Option[HazardousDeclaration] =
        let Cargo.Cargo(x) = c;
        x#hazardousDeclaration

    /// 温度管理条件（保持していれば）
    pub def temperatureRequirementOf(c: Cargo): Option[TemperatureRequirement] =
        let Cargo.Cargo(x) = c;
        x#temperatureRequirement

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
    /// UN/LOCODE の形か。判定は共有カーネルに置く（`Routing` も同じ規則を使う）
    pub def isUnLocode(value: String): Bool =
        SharedCalendarText.isUnLocode(value)

    /// `YYYY-MM-DD` の形をしており、暦として存在する日付か（共有カーネルへ委譲）
    pub def isIsoDate(value: String): Bool =
        SharedCalendarText.isIsoDate(value)

    /// 暦として存在する日付か（共有カーネルへ委譲）
    pub def isValidDate(year: Int32, month: Int32, day: Int32): Bool =
        SharedCalendarText.isValidDate(year, month, day)

    /// うるう年か（共有カーネルへ委譲）
    pub def isLeapYear(year: Int32): Bool =
        SharedCalendarText.isLeapYear(year)

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

    // ---- 一覧の絞り込み条件（IT8・TS14） ----

    ///
    /// 予約一覧の絞り込み条件。
    ///
    /// 3 つの軸を **1 つの値にまとめる**。引数を並べて渡すと、絞り込みを
    /// 足すたびにポート・アダプタ・スタブの引数列を同時に直すことになり、
    /// どこか 1 つが古い順序のまま通ってしまう。
    ///
    /// `shipperIds` の意味を **3 値で区別する**のが要点である。
    ///
    /// | 値 | 意味 |
    /// | :--- | :--- |
    /// | `None` | 荷主名で絞らない |
    /// | `Some(Nil)` | 名前で絞った結果、**該当する荷主が 1 件も無い** |
    /// | `Some(ids)` | この荷主 ID のいずれか |
    ///
    /// `Some(Nil)` を `None` と同一視すると、存在しない荷主名で全件が出る。
    /// 営業は他社の予約を電話口で読み上げることになる。
    ///
    pub enum CargoQuery({
        keyword = String,
        status = StatusFilter,
        shipperIds = Option[List[String]]
    })

    pub def cargoQuery(keyword: String, status: StatusFilter,
                       shipperIds: Option[List[String]]): CargoQuery =
        CargoQuery.CargoQuery({ keyword = keyword, status = status, shipperIds = shipperIds })

    pub def queryKeywordOf(q: CargoQuery): String =
        let CargoQuery.CargoQuery(x) = q; x#keyword

    pub def queryStatusOf(q: CargoQuery): StatusFilter =
        let CargoQuery.CargoQuery(x) = q; x#status

    pub def queryShipperIdsOf(q: CargoQuery): Option[List[String]] =
        let CargoQuery.CargoQuery(x) = q; x#shipperIds

    ///
    /// 状態での絞り込み条件。
    ///
    /// **3 値で区別する**。荷主 ID の集合（`shipperIds`）と同じ形である。
    ///
    /// | 値 | 意味 |
    /// | :--- | :--- |
    /// | `AnyStatus` | 状態で絞らない（欄が「すべての状態」） |
    /// | `OnlyStatus(s)` | この状態のもの |
    /// | `UnknownStatus(語)` | 知らない状態語が指定された |
    ///
    pub enum StatusFilter with Eq, ToString {
        case AnyStatus
        case OnlyStatus(BookingStatus)
        case UnknownStatus(String)
    }

    ///
    /// 画面から届いた状態語を絞り込み条件に変える。
    ///
    /// **空は「絞らない」、未知の語は「0 件」**（[IT8 計画](../../../../../../docs/development/iteration_plan-8.md)
    /// の壊れ方の観点・軸 1）。**黙って広げない**——利用者が状態を指定したのに
    /// 全件を返すと、絞ったつもりで**その状態ではない予約を対象に作業する**。
    /// 予約一覧から着手する予約を選ぶ運用（`operation.md` の日次手順）では、
    /// これは「まだ引き渡されていない予約に経路を割り当てる」ことになる。
    ///
    /// 0 件にするなら、**なぜ 0 件なのかを画面が言える**必要がある。
    /// そのため語を捨てずに `UnknownStatus` として持ち回る。
    /// 「予約が無い」と「そんな状態は無い」は、利用者が取る次の行動が違う。
    ///
    /// 語彙は永続化値（`PRELIMINARY` 等）と同じものを使う。
    /// 画面用の別語彙を作ると、URL と DB で状態の呼び名が 2 つになる。
    ///
    pub def parseStatusFilter(value: String): StatusFilter =
        let trimmed = String.trim(value);
        if (trimmed == "")
            StatusFilter.AnyStatus
        else
            match statusFromPersisted(trimmed) {
                case Some(s) => StatusFilter.OnlyStatus(s)
                case None    => StatusFilter.UnknownStatus(trimmed)
            }

    /// 状態を列挙する（絞り込みの選択肢を画面が組み立てるため）
    pub def allStatuses(): List[BookingStatus] =
        BookingStatus.Preliminary :: BookingStatus.RouteProposed ::
        BookingStatus.Confirmed :: BookingStatus.TrackingIssued ::
        BookingStatus.InTransit :: BookingStatus.Delivered ::
        BookingStatus.Settled :: BookingStatus.Cancelled :: Nil

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
    ///
    /// 予約の状態を**2 つの軸から導出して**表示する（IT9・US11）。
    ///
    /// `BookingStatus` だけでは「経路設計中」と「経路提案済」を区別できない。
    /// 経路が決まっても `BookingStatus` は `ROUTE_PROPOSED` のままであり、
    /// 変わるのは `RoutingStatus` だからである（着手前の決定 2）。
    ///
    /// **画面ごとに書き直さない**（IT8 の Try T4）。予約一覧・予約詳細・
    /// 経路割り当ての 3 画面が同じ判断を持つと、片方だけ直したときに
    /// **同じ予約が画面によって違う状態に見える**。
    ///
    /// この式が偽になる入力: `ROUTED` を見ずに `statusLabel` だけを使う実装
    /// （経路を割り当てても「経路設計中」のまま出る）。
    ///
    pub def displayStatusOf(c: Cargo): String =
        let base = statusLabel(bookingStatusOf(c));
        if (bookingStatusOf(c) == BookingStatus.RouteProposed and isRouted(c))
            "経路提案済"
        else
            base

    pub def statusLabel(s: BookingStatus): String = match s {
        case BookingStatus.Preliminary    => "仮受付"
        // **「経路提案済」ではない**。この状態へ入るのは経路設計者へ
        // 引き渡した時点（US06）であり、経路はまだ提案されていない。
        // IT4 のレビュー H14 はワイヤーフレームと定義表の食い違いを
        // 定義表を正として解消したが、ユーザーストーリー（US06 受入基準 2）
        // との突合をしていなかった。**上流の要件が正である**
        case BookingStatus.RouteProposed  => "経路設計中"
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

    /// 割り当て済みの旅程（未割り当てなら `None`）
    pub def cargoItineraryOf(c: Cargo): Option[CargoItinerary] =
        let Cargo.Cargo(x) = c; x#cargoItinerary

    /// 配送状況
    pub def deliveryOfCargo(c: Cargo): Delivery =
        let Cargo.Cargo(x) = c; x#delivery

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

    // ---- 制限値 ----

    /// `cargo.description` の桁数と一致させる
    def maxDescriptionLength(): Int32 = 500
}
