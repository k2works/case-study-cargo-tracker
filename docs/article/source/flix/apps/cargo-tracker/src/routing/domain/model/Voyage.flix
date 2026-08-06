///
/// Routing Context のドメインモデル。
///
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md) の
/// 「3. Routing Context」を正典とする。
///
/// **IT6 のスコープ**: `Voyage` 集約・`VoyageNumber`・`Schedule`・
/// `CarrierMovement`・`CargoCapability`。
/// `CargoItinerary`・`Leg` は経路候補算出（IT7）で追加する。
///
/// **効果を一切要求しない**。現在時刻も識別子も引数で受け取る。
///
mod RoutingModel {

    ///
    /// 航海が運べる貨物の種別。
    ///
    /// **`Booking` の `CargoType` と分ける**。前者は「この貨物は何か」、
    /// こちらは「この航海は何を運べるか」であり、文脈が違う。
    /// 同じ列挙を共有すると BC 独立性（`arch-lint` 規約 4）に反する
    /// （IT4 で `ShipperError` と `BookingError` を分けたのと同じ判断）。
    ///
    pub enum CargoCapability with Eq, ToString, Order {
        case General
        case Hazardous
        case Refrigerated
    }

    ///
    /// 航海として成立しない理由。
    ///
    /// **コンテキスト固有型**とし、`BookingError` と共有しない。
    ///
    pub enum VoyageError with Eq, ToString {
        case VoyageNumberIsRequired
        case VoyageNumberIsTooLong
        case VoyageNumberIsMalformed
        case VesselNameIsRequired
        case VesselNameIsTooLong
        case CarrierNameIsRequired
        case CarrierNameIsTooLong
        case ScheduleIsEmpty
        case MovementEndpointsAreSame
        case LocationCodeIsMalformed
        case MovementTimeIsMalformed
        case MovementTimesAreInverted
        case MovementsAreDisconnected
        case MovementsAreOutOfOrder
        case CapabilitiesAreEmpty
    }

    ///
    /// 運送区間。
    ///
    /// 時刻は `YYYY-MM-DDTHH:MM`。**日付だけにしない**のは、
    /// 同じ日の午前と午後を区別できないと区間の順序を判定できないためである。
    ///
    pub enum CarrierMovement({
        departureLocation = String,
        arrivalLocation = String,
        departureTime = String,
        arrivalTime = String,
        seqNumber = Int32
    })

    ///
    /// 航海（集約ルート）。
    ///
    /// `movements` は**時系列順・連結済み・連番付き**であることが保証される。
    /// 生成経路（`registerVoyage` / `reconstruct`）以外から作れない。
    ///
    pub enum Voyage({
        voyageNumber = String,
        vesselName = String,
        carrierName = String,
        capabilities = List[CargoCapability],
        movements = List[CarrierMovement]
    })

    // ---- 生成 ----

    /// 区間を組み立てる（検証は `registerVoyage` が行う）
    pub def movement(departure: String, arrival: String,
                     departureTime: String, arrivalTime: String): CarrierMovement =
        CarrierMovement.CarrierMovement({
            departureLocation = departure, arrivalLocation = arrival,
            departureTime = departureTime, arrivalTime = arrivalTime,
            seqNumber = 0
        })

    ///
    /// 航海スケジュールを登録する（`RegisterVoyageCommand` に対応）。
    ///
    /// **検証の順序に意味がある**。識別子 → 名前 → 区間の個別検証 → 区間の関係
    /// → 対応種別。区間の関係（連結・順序）は個別の検証を通った後でなければ
    /// 判定できない。
    ///
    /// 区間の順序番号は**入力を信じず 1 始まりの連番へ正規化する**。
    /// 保存する値は集約が決める。
    ///
    pub def registerVoyage(voyageNumber: String, vesselName: String, carrierName: String,
                           movements: List[CarrierMovement],
                           capabilities: List[CargoCapability]):
            Result[VoyageError, Voyage] =
        forM (
            n <- requireVoyageNumber(voyageNumber);
            v <- requireVesselName(vesselName);
            c <- requireCarrierName(carrierName);
            ms <- requireMovements(movements);
            caps <- requireCapabilities(capabilities)
        ) yield Voyage.Voyage({
            voyageNumber = n, vesselName = v, carrierName = c,
            capabilities = caps, movements = ms
        })

    ///
    /// 永続化された行から航海を組み立てる。
    ///
    /// 登録時の検証をやり直さない。ルールを厳しくした瞬間に既存データが
    /// 読めなくなることを避ける。
    ///
    pub def reconstruct(voyageNumber: String, vesselName: String, carrierName: String,
                        capabilities: List[CargoCapability],
                        movements: List[CarrierMovement]): Voyage =
        Voyage.Voyage({
            voyageNumber = voyageNumber, vesselName = vesselName, carrierName = carrierName,
            capabilities = capabilities, movements = movements
        })

    /// 永続化された行から区間を組み立てる（検証をやり直さない）
    pub def reconstructMovement(departure: String, arrival: String,
                                departureTime: String, arrivalTime: String,
                                seq: Int32): CarrierMovement =
        CarrierMovement.CarrierMovement({
            departureLocation = departure, arrivalLocation = arrival,
            departureTime = departureTime, arrivalTime = arrivalTime,
            seqNumber = seq
        })

    // ---- 検証 ----

    def requireVoyageNumber(value: String): Result[VoyageError, String] =
        let trimmed = String.trim(value);
        if (trimmed == "")
            Err(VoyageError.VoyageNumberIsRequired)
        else if (String.length(trimmed) > 20)
            // 上限は DB 列（`VARCHAR(20)`）に合わせる。アプリが受け入れた値を
            // DB が拒む状態は「登録したのに保存されていない」という
            // 最も分かりにくい壊れ方になる
            Err(VoyageError.VoyageNumberIsTooLong)
        else if (not isVoyageNumberText(trimmed))
            Err(VoyageError.VoyageNumberIsMalformed)
        else
            Ok(trimmed)

    ///
    /// 航海番号として通る文字種か（大文字英数字とハイフン）。
    ///
    /// **識別子として通る形は集約が決める**。IT6 の当初は、集約が文字種を
    /// 一切見ない一方で通知（`Flash`）が大文字英数字とハイフンだけを通していた。
    /// その結果、小文字や空白を含む番号は**登録に成功するのに完了通知が出ない**
    /// という食い違いが起きていた（IT6 レビュー H2）。
    ///
    /// 航海番号は URL のパスにも入る（`/voyages/{voyageNumber}/edit`）。
    /// `/` や `?` を含む値を通すと、編集リンクがそもそも届かない。
    ///
    /// `Char.isLetter` / `Char.isDigit` は使わない。**全角英数字を通してしまう**
    /// （IT4 で `日本大阪港` が UN/LOCODE として通った原因）。
    ///
    pub def isVoyageNumberText(value: String): Bool =
        SharedCalendarText.isUpperAlnumCode(20, value)

    def requireVesselName(value: String): Result[VoyageError, String] =
        let trimmed = String.trim(value);
        if (trimmed == "") Err(VoyageError.VesselNameIsRequired)
        else if (String.length(trimmed) > 100) Err(VoyageError.VesselNameIsTooLong)
        else Ok(trimmed)

    def requireCarrierName(value: String): Result[VoyageError, String] =
        let trimmed = String.trim(value);
        if (trimmed == "") Err(VoyageError.CarrierNameIsRequired)
        else if (String.length(trimmed) > 100) Err(VoyageError.CarrierNameIsTooLong)
        else Ok(trimmed)

    ///
    /// 対応貨物種別を検証する。
    ///
    /// 0 件だと**何も運べない航海**になり、検索結果に出ても選べない。
    /// 重複は 1 つにまとめる（同じ意味の値を 2 つ保持しても情報が増えない）。
    ///
    def requireCapabilities(values: List[CargoCapability]):
            Result[VoyageError, List[CargoCapability]] =
        let unique = List.distinct(values);
        if (List.isEmpty(unique)) Err(VoyageError.CapabilitiesAreEmpty) else Ok(unique)

    ///
    /// 区間の一覧を検証し、順序番号を正規化する。
    ///
    /// 個別の検証（端点・形式・時刻）を全て通してから、関係（連結・順序）を見る。
    /// 順序が逆だと「連結していない」と「そもそも港コードが不正」を
    /// 利用者が区別できない。
    ///
    def requireMovements(movements: List[CarrierMovement]):
            Result[VoyageError, List[CarrierMovement]] =
        if (List.isEmpty(movements))
            Err(VoyageError.ScheduleIsEmpty)
        else
            forM (
                _ <- checkEachMovement(movements);
                _ <- checkMovementRelations(movements)
            ) yield numberMovements(movements, 1)

    def checkEachMovement(movements: List[CarrierMovement]): Result[VoyageError, Unit] =
        match movements {
            case Nil     => Ok()
            case m :: ms => forM (_ <- checkMovement(m); _ <- checkEachMovement(ms)) yield ()
        }

    def checkMovement(m: CarrierMovement): Result[VoyageError, Unit] =
        let CarrierMovement.CarrierMovement(x) = m;
        let departure = String.toUpperCase(String.trim(x#departureLocation));
        let arrival = String.toUpperCase(String.trim(x#arrivalLocation));
        if (not (SharedCalendarText.isUnLocode(departure) and SharedCalendarText.isUnLocode(arrival)))
            Err(VoyageError.LocationCodeIsMalformed)
        else if (departure == arrival)
            Err(VoyageError.MovementEndpointsAreSame)
        else if (not (SharedCalendarText.isIsoDateTime(String.trim(x#departureTime)) and
                      SharedCalendarText.isIsoDateTime(String.trim(x#arrivalTime))))
            Err(VoyageError.MovementTimeIsMalformed)
        else if (String.trim(x#departureTime) > String.trim(x#arrivalTime))
            // **同一時刻は許す**。境界を閉じると当日便が登録できない
            // （IT5 の到着期限と同じ判断）
            Err(VoyageError.MovementTimesAreInverted)
        else
            Ok()

    ///
    /// 区間どうしの関係を検証する。
    ///
    /// - 連結: 前の到着地 = 次の出発地。連結していない並びは**同じ航海の航程ではない**
    /// - 順序: 前の到着時刻 <= 次の出発時刻。逆転すると到着前に次が出発する
    ///
    /// ISO 8601（`YYYY-MM-DDTHH:MM`）は辞書順と時刻順が一致するため文字列比較でよい。
    ///
    def checkMovementRelations(movements: List[CarrierMovement]): Result[VoyageError, Unit] =
        match movements {
            case a :: b :: rest =>
                let CarrierMovement.CarrierMovement(x) = a;
                let CarrierMovement.CarrierMovement(y) = b;
                if (String.toUpperCase(String.trim(x#arrivalLocation)) !=
                    String.toUpperCase(String.trim(y#departureLocation)))
                    Err(VoyageError.MovementsAreDisconnected)
                else if (String.trim(x#arrivalTime) > String.trim(y#departureTime))
                    Err(VoyageError.MovementsAreOutOfOrder)
                else
                    checkMovementRelations(b :: rest)
            case _ => Ok()
        }

    /// 順序番号を 1 始まりの連番へ振り直す
    def numberMovements(movements: List[CarrierMovement], start: Int32): List[CarrierMovement] =
        match movements {
            case Nil     => Nil
            case m :: ms =>
                let CarrierMovement.CarrierMovement(x) = m;
                CarrierMovement.CarrierMovement({ seqNumber = start | x }) ::
                    numberMovements(ms, start + 1)
        }

    // ---- 参照 ----

    pub def voyageNumberOf(v: Voyage): String =
        let Voyage.Voyage(x) = v;
        x#voyageNumber

    pub def vesselNameOf(v: Voyage): String =
        let Voyage.Voyage(x) = v;
        x#vesselName

    pub def carrierNameOf(v: Voyage): String =
        let Voyage.Voyage(x) = v;
        x#carrierName

    pub def capabilitiesOf(v: Voyage): List[CargoCapability] =
        let Voyage.Voyage(x) = v;
        x#capabilities

    pub def movementsOf(v: Voyage): List[CarrierMovement] =
        let Voyage.Voyage(x) = v;
        x#movements

    pub def departureLocationOf(m: CarrierMovement): String =
        let CarrierMovement.CarrierMovement(x) = m;
        String.toUpperCase(String.trim(x#departureLocation))

    pub def arrivalLocationOf(m: CarrierMovement): String =
        let CarrierMovement.CarrierMovement(x) = m;
        String.toUpperCase(String.trim(x#arrivalLocation))

    pub def departureTimeOf(m: CarrierMovement): String =
        let CarrierMovement.CarrierMovement(x) = m;
        String.trim(x#departureTime)

    pub def arrivalTimeOf(m: CarrierMovement): String =
        let CarrierMovement.CarrierMovement(x) = m;
        String.trim(x#arrivalTime)

    pub def seqNumberOf(m: CarrierMovement): Int32 =
        let CarrierMovement.CarrierMovement(x) = m;
        x#seqNumber

    ///
    /// 航海全体の出発地。
    ///
    /// 検索（US07）は端点で絞る。**区間の一覧から毎回計算させない**ため、
    /// 集約が答える。
    ///
    pub def originOf(v: Voyage): String =
        match List.head(movementsOf(v)) {
            case Some(m) => departureLocationOf(m)
            // 区間が空の航海は作れない（`ScheduleIsEmpty`）。
            // 復元経路が壊れた値を渡した場合のみここへ来る
            case None    => ""
        }

    /// 航海全体の仕向地
    pub def destinationOf(v: Voyage): String =
        match List.last(movementsOf(v)) {
            case Some(m) => arrivalLocationOf(m)
            case None    => ""
        }

    /// 出発時刻（航程の最初の区間）
    pub def departureTimeOfVoyage(v: Voyage): String =
        match List.head(movementsOf(v)) {
            case Some(m) => departureTimeOf(m)
            case None    => ""
        }

    /// 到着時刻（航程の最後の区間）
    pub def arrivalTimeOfVoyage(v: Voyage): String =
        match List.last(movementsOf(v)) {
            case Some(m) => arrivalTimeOf(m)
            case None    => ""
        }

    ///
    /// 寄港地（出発地・仕向地を除く中間の港）。
    ///
    /// 一覧に出すのはこれである（端点は別の列に出る）。
    ///
    pub def portsOfCallOf(v: Voyage): List[String] =
        let ms = movementsOf(v);
        // 最後の区間の到着地は仕向地であり寄港地ではない。
        // `List.dropRight` は Flix に無いため、長さから取る
        ms |> List.take(List.length(ms) - 1) |> List.map(arrivalLocationOf)

    /// 指定した貨物種別を運べるか
    pub def supports(v: Voyage, capability: CargoCapability): Bool =
        List.memberOf(capability, capabilitiesOf(v))

    // ---- 永続化値との対応 ----

    pub def capabilityToPersisted(c: CargoCapability): String = match c {
        case CargoCapability.General      => "GENERAL"
        case CargoCapability.Hazardous    => "HAZARDOUS"
        case CargoCapability.Refrigerated => "REFRIGERATED"
    }

    ///
    /// 永続化値から対応種別を復元する。
    ///
    /// **未知の値を既定値へ倒さない**。危険物対応でない航海が
    /// 「一般貨物対応」として読まれると、逆に危険物対応の航海が候補から漏れる。
    /// どちらも業務上の誤りになる。
    ///
    pub def capabilityFromPersisted(value: String): Option[CargoCapability] =
        match value {
            case "GENERAL"      => Some(CargoCapability.General)
            case "HAZARDOUS"    => Some(CargoCapability.Hazardous)
            case "REFRIGERATED" => Some(CargoCapability.Refrigerated)
            case _              => None
        }

    ///
    /// 対応種別の集合を**区切り文字で囲んだ形**へ符号化する。
    ///
    /// `,GENERAL,REFRIGERATED,` のように前後にも区切りを置く。
    /// 囲まないと `LIKE '%GENERAL%'` が `GENERAL_CARGO` のような値にも
    /// 一致する（将来値が増えたときの誤検出）。検索は `,GENERAL,` を含むかで判定する。
    ///
    pub def capabilitiesToPersisted(capabilities: List[CargoCapability]): String =
        "," + (capabilities |> List.map(capabilityToPersisted) |> List.join(",")) + ","

    ///
    /// 符号化された集合を復元する。
    ///
    /// **読めない値は落とす**。1 つの未知の値で航海全体が読めなくなるより、
    /// 読める分だけを返すほうが業務が続く（一覧の 1 行が消えるより軽い）。
    ///
    pub def capabilitiesFromPersisted(value: String): List[CargoCapability] =
        String.splitOn(substr = ",", value)
            |> List.filterMap(v -> capabilityFromPersisted(String.trim(v)))
            |> List.distinct

    /// 検索で使う「この種別を含む」の照合文字列（`,GENERAL,`）
    pub def capabilityPattern(c: CargoCapability): String =
        "," + capabilityToPersisted(c) + ","

    // ---- 表示 ----

    /// 貨物種別を業務の言葉へ（画面に列挙値を出さない）
    pub def capabilityLabel(c: CargoCapability): String = match c {
        case CargoCapability.General      => "一般貨物"
        case CargoCapability.Hazardous    => "危険物"
        case CargoCapability.Refrigerated => "冷凍・冷蔵貨物"
    }

    ///
    /// 航海の内容から版（指紋）を作る。
    ///
    /// **更新日時を使わない**。秒未満の更新が同じ値になりうるうえ、
    /// 「内容は同じだが保存し直した」場合にも食い違いとして扱ってしまう。
    /// 内容そのものから作れば、**同じ内容なら同じ版**になる。
    ///
    /// # なぜ集約に置くか（IT8・TS14。IT6 レビュー M8）
    ///
    /// IT6 では application 層（`RegisterVoyage`）にあった。
    /// **版は「この集約の全属性」から作る**ものであり、属性を足す判断と
    /// 版に含める判断は同じ場所で下されるべきである。離れていると、
    /// 集約にフィールドを足したときに版の更新を忘れる——そして
    /// **忘れても何も壊れない**。壊れるのは「同時編集で片方の変更が黙って
    /// 消える」ときだけで、そのときには原因を辿れない。
    ///
    /// **航海番号を含める**（IT6 レビュー M8 の後半）。IT6 は含めておらず、
    /// 別の航海の版がたまたま一致すると、取り違えた保存が通りえた。
    /// 現在の呼び出し経路では航海番号で引いた後に照合するため実害は無いが、
    /// **版の一致が「同じ航海の同じ内容」を意味する**形にしておく。
    ///
    /// 衝突（異なる内容が同じ版になる）は理論上ありうるが、
    /// 全項目を連結した文字列のハッシュであり、運航変更の同時編集という
    /// 規模では実際上問題にならない。
    ///
    pub def versionOf(v: Voyage): String =
        let movements = movementsOf(v)
            |> List.map(m ->
                "${departureLocationOf(m)}>${arrivalLocationOf(m)}" +
                "@${departureTimeOf(m)}-${arrivalTimeOf(m)}")
            |> List.join("|");
        let capabilities = capabilitiesToPersisted(capabilitiesOf(v));
        let source = "${voyageNumberOf(v)}${vesselNameOf(v)}${carrierNameOf(v)}" +
                     "${capabilities}${movements}";
        "${Hash.hash(source)}"

    ///
    /// 航海が対応する種別を 1 行で表す（`一般貨物・冷凍・冷蔵貨物`）。
    ///
    /// **区切り文字を 1 箇所で決める**（IT8・TS14。IT6 レビュー M7）。
    /// 航海一覧（`VoyagePages.voyageRow`）と変更内容の差分表示
    /// （`VoyageRoutes.capabilityText`）が同じ組み立てを別々に持っており、
    /// **一方だけ区切りを変えると、同じ値が画面によって違う形で出る**。
    ///
    /// IT5 で「入力は共通化したが表示は重複した」のと同じ形である。
    /// 入力（チェックボックスの選択肢）は `allCapabilities` が、
    /// 出力（1 行の表示）は本関数が担う。
    ///
    pub def capabilitiesText(v: Voyage): String =
        capabilitiesOf(v) |> List.map(capabilityLabel) |> List.join("・")

    /// すべての対応種別（画面の選択肢の順序を 1 箇所で決める）
    pub def allCapabilities(): List[CargoCapability] =
        CargoCapability.General :: CargoCapability.Hazardous ::
        CargoCapability.Refrigerated :: Nil
}
