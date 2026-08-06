///
/// Estimation Context のドメインモデル（US01・IT8）。
///
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md) の
/// 「7. Estimation Context」を正典とする。
///
/// # コンテキスト固有型を持つ
///
/// 設計ドキュメントは `CargoType` を「Booking Context と共通」と書いているが、
/// **型そのものを共有しない**（`arch-lint` 規約 4）。Routing が
/// `CargoCapability` を、Booking が `CargoType` を別々に持つのと同じ形である
/// （[アーキテクチャ規約](../../../../../../docs/design/arch_lint_rules.md)の規約 4）。
///
/// 値（`GENERAL` / `HAZARDOUS` / `REFRIGERATED`）が一致するのは、
/// **同じ業務語彙を指しているから**であって、同じ型だからではない。
/// 見積の貨物種別は運賃の割増を決め、予約の貨物種別は申告情報の要否を決める——
/// **決めるものが違えば、いずれ値も分かれる**。
///
/// # 効果を要求しない
///
/// 現在時刻も識別子も引数で受け取る（既存の集約と同じ規約）。
///
mod EstimationModel {
    use SharedMoney.Money

    ///
    /// 見積の貨物種別。
    ///
    /// `CargoType` ではなく `CargoKind` と名付けたのは、**Booking の
    /// `CargoType` と読み違えないため**である。同名の別型が 2 つあると、
    /// `use` の書き間違いでコンパイルが通ってしまう組み合わせが生まれる。
    ///
    pub enum CargoKind with Eq, ToString {
        case General
        case Hazardous
        case Refrigerated
    }

    ///
    /// 見積の状態。
    ///
    /// `Expired` は本 IT では**遷移させない**。見積の有効期限は業務上
    /// 決まっていない（要件に無い）。値だけ定義するのは、
    /// パターンマッチの網羅性検査を効かせるためである。
    ///
    pub enum EstimateStatus with Eq, ToString {
        case Created
        case Expired
    }

    /// 見積として成立しない理由（**コンテキスト固有型**）
    pub enum EstimateError with Eq, ToString {
        case OriginIsRequired
        case DestinationIsRequired
        case OriginAndDestinationAreSame
        case OriginIsMalformed
        case DestinationIsMalformed
        case ArrivalDeadlineIsRequired
        case ArrivalDeadlineIsMalformed
        case ArrivalDeadlineIsInPast
        case WeightIsNotPositive
        case CargoKindIsMalformed
    }

    ///
    /// ルート候補（値オブジェクト）。
    ///
    /// **経由港は 0 個以上**である。直行便は空リストになる。
    /// `Option[String]` にしない——寄港地が 2 つある候補を表現できない。
    /// データモデルの `transit_port` は 1 列だが、**列に合わせてモデルを削らない**
    /// （`route_candidate` は候補ごとに 1 行であり、経由港は連結して入れる）。
    ///
    pub enum RouteCandidate({
        voyageNumbers = List[String],
        transitPorts = List[String],
        transitDays = Int32,
        estimatedCost = Money
    })

    pub def routeCandidate(voyageNumbers: List[String], transitPorts: List[String],
                           transitDays: Int32, estimatedCost: Money): RouteCandidate =
        RouteCandidate.RouteCandidate({
            voyageNumbers = voyageNumbers, transitPorts = transitPorts,
            transitDays = transitDays, estimatedCost = estimatedCost
        })

    pub def candidateVoyageNumbersOf(c: RouteCandidate): List[String] =
        let RouteCandidate.RouteCandidate(x) = c; x#voyageNumbers

    pub def candidateTransitPortsOf(c: RouteCandidate): List[String] =
        let RouteCandidate.RouteCandidate(x) = c; x#transitPorts

    pub def candidateTransitDaysOf(c: RouteCandidate): Int32 =
        let RouteCandidate.RouteCandidate(x) = c; x#transitDays

    pub def candidateCostOf(c: RouteCandidate): Money =
        let RouteCandidate.RouteCandidate(x) = c; x#estimatedCost

    ///
    /// 見積（集約ルート）。
    ///
    /// 重量は**グラム**で持つ（Booking の `weightGrams` と同じ表現。
    /// DB 列は `NUMERIC(10,3)` のキログラムであり、境界で換算する）。
    ///
    pub enum Estimate({
        estimateId = String,
        origin = String,
        destination = String,
        arrivalDeadline = String,
        cargoKind = CargoKind,
        weightGrams = Int64,
        candidates = List[RouteCandidate],
        status = EstimateStatus
    })

    ///
    /// 見積を作る。
    ///
    /// **候補は空で作る**。候補の算出には航海の一覧が要り、それは
    /// アプリケーション層の仕事である（ドメインは効果を要求しない）。
    /// 算出後に `replaceCandidates` で入れる。
    ///
    /// `today` を受け取るのは、**過去の期限で見積を作らせない**ためである。
    /// 今日がいつかを知るのはアプリケーション層の責務である。
    ///
    pub def createEstimate(estimateId: String, origin: String, destination: String,
                           arrivalDeadline: String, cargoKind: CargoKind,
                           weightGrams: Int64, today: String):
            Result[EstimateError, Estimate] =
        let o = String.toUpperCase(String.trim(origin));
        let d = String.toUpperCase(String.trim(destination));
        let deadline = String.trim(arrivalDeadline);
        if (o == "")
            Err(EstimateError.OriginIsRequired)
        else if (d == "")
            Err(EstimateError.DestinationIsRequired)
        // **どちらの欄が誤っているかを分けて返す**（IT8 レビュー・technical-writer）。
        // まとめて返すと、仕向地を誤った利用者は**正しく入力した出発地の欄に
        // 赤字が出る**——どちらを直せばよいか分からない
        else if (not isLocationCode(o))
            Err(EstimateError.OriginIsMalformed)
        else if (not isLocationCode(d))
            Err(EstimateError.DestinationIsMalformed)
        else if (o == d)
            Err(EstimateError.OriginAndDestinationAreSame)
        else if (deadline == "")
            Err(EstimateError.ArrivalDeadlineIsRequired)
        else if (not SharedCalendarText.isIsoDate(deadline))
            Err(EstimateError.ArrivalDeadlineIsMalformed)
        else if (deadline < today)
            // **当日は通す**。今日中に着く便がありうる（IT7 の期限判定と同じ境界）
            Err(EstimateError.ArrivalDeadlineIsInPast)
        else if (weightGrams <= 0i64)
            Err(EstimateError.WeightIsNotPositive)
        else
            Ok(Estimate.Estimate({
                estimateId = estimateId, origin = o, destination = d,
                arrivalDeadline = deadline, cargoKind = cargoKind,
                weightGrams = weightGrams, candidates = Nil,
                status = EstimateStatus.Created
            }))

    ///
    /// UN/LOCODE として成立する形か（英大文字 5 文字）。
    ///
    /// `Char.isLetter` を使わない。**全角文字を通す**（IT4 で `日本大阪港` が
    /// UN/LOCODE として通った原因と同じ）。
    ///
    pub def isLocationCode(value: String): Bool =
        String.length(value) == 5 and
        value |> String.toList |> List.forAll(c -> c >= 'A' and c <= 'Z')

    ///
    /// 候補を入れ替える。
    ///
    /// **足すのではなく置き換える**。再算出は「その時点の航海で引き直す」
    /// ことであり、古い候補が混ざると、もう存在しない便を提示しうる。
    ///
    pub def replaceCandidates(e: Estimate, candidates: List[RouteCandidate]): Estimate =
        let Estimate.Estimate(x) = e;
        Estimate.Estimate({ candidates = candidates | x })

    /// 永続化された行から復元する（登録時の検証をやり直さない）
    pub def reconstruct(estimateId: String, origin: String, destination: String,
                        arrivalDeadline: String, cargoKind: CargoKind,
                        weightGrams: Int64, candidates: List[RouteCandidate],
                        status: EstimateStatus): Estimate =
        Estimate.Estimate({
            estimateId = estimateId, origin = origin, destination = destination,
            arrivalDeadline = arrivalDeadline, cargoKind = cargoKind,
            weightGrams = weightGrams, candidates = candidates, status = status
        })

    // ---- 参照 ----

    pub def estimateIdOf(e: Estimate): String = let Estimate.Estimate(x) = e; x#estimateId
    pub def originOf(e: Estimate): String = let Estimate.Estimate(x) = e; x#origin
    pub def destinationOf(e: Estimate): String = let Estimate.Estimate(x) = e; x#destination
    pub def arrivalDeadlineOf(e: Estimate): String =
        let Estimate.Estimate(x) = e; x#arrivalDeadline
    pub def cargoKindOf(e: Estimate): CargoKind = let Estimate.Estimate(x) = e; x#cargoKind
    pub def weightGramsOf(e: Estimate): Int64 = let Estimate.Estimate(x) = e; x#weightGrams
    pub def candidatesOf(e: Estimate): List[RouteCandidate] =
        let Estimate.Estimate(x) = e; x#candidates
    pub def statusOf(e: Estimate): EstimateStatus = let Estimate.Estimate(x) = e; x#status

    ///
    /// 重量をキログラム表記にする（IT8 レビュー・利用者代表の指摘）。
    ///
    /// 内部はグラムの整数である。**端数はゼロ詰めしてから連結する**——
    /// しないと `1050 g` が `1.50 kg` になり、実際の 1.05 kg より
    /// 45% 重い値が荷主に伝わる。見積の重量が違えば運賃も違う。
    ///
    /// 末尾の 0 は落とす（`1500 g` は `1.5 kg`。`1.500 kg` とは書かない）。
    ///
    /// **画面ではなくドメインに置く**。桁の扱いは業務上の意味を持ち、
    /// 画面ごとに書き直すと片方だけ間違える（IT6 レビュー M7 と同じ形）。
    ///
    pub def weightText(weightGrams: Int64): String =
        let kg = weightGrams / 1000i64;
        let fraction = Int64.remainder(weightGrams, 1000i64);
        if (fraction == 0i64)
            "${SharedMoney.groupDigits(kg)} kg"
        else {
            // 3 桁へゼロ詰めしてから末尾の 0 を落とす
            let padded = if (fraction < 10i64) "00${fraction}"
                         else if (fraction < 100i64) "0${fraction}"
                         else "${fraction}";
            "${SharedMoney.groupDigits(kg)}.${trimTrailingZeros(padded)} kg"
        }

    def trimTrailingZeros(value: String): String =
        if (String.endsWith(suffix = "0", value) and String.length(value) > 1)
            trimTrailingZeros(String.slice(start = 0, end = String.length(value) - 1, value))
        else
            value

    // ---- 識別子 ----

    ///
    /// 見積番号を採番する（`EST-` + UUID の先頭 8 桁）。
    ///
    /// 予約番号（`BK-`）・荷主コード（`SHP-`）と同じ形にする。
    /// **利用者が電話口で読み上げる**識別子であり、UUID をそのまま使えない。
    ///
    /// **採番した値をそのまま集約の識別子にする**。表示のときだけ変換する形に
    /// すると、URL には UUID が現れ、利用者が読み上げる番号と一致しなくなる。
    ///
    pub def generateEstimateId(rawId: String): String =
        let head = match String.indexOf(substr = "-", rawId) {
            case Some(i) => String.slice(start = 0, end = i, rawId)
            case None    => rawId
        };
        "EST-" + String.toUpperCase(head)

    // ---- 永続化値との対応 ----

    pub def cargoKindToPersisted(k: CargoKind): String = match k {
        case CargoKind.General      => "GENERAL"
        case CargoKind.Hazardous    => "HAZARDOUS"
        case CargoKind.Refrigerated => "REFRIGERATED"
    }

    /// 未知の値は `None`。既定値へ倒すと危険物が一般貨物として見積もられる
    pub def cargoKindFromPersisted(value: String): Option[CargoKind] = match value {
        case "GENERAL"      => Some(CargoKind.General)
        case "HAZARDOUS"    => Some(CargoKind.Hazardous)
        case "REFRIGERATED" => Some(CargoKind.Refrigerated)
        case _              => None
    }

    pub def statusToPersisted(s: EstimateStatus): String = match s {
        case EstimateStatus.Created => "CREATED"
        case EstimateStatus.Expired => "EXPIRED"
    }

    pub def statusFromPersisted(value: String): Option[EstimateStatus] = match value {
        case "CREATED" => Some(EstimateStatus.Created)
        case "EXPIRED" => Some(EstimateStatus.Expired)
        case _         => None
    }

    /// 画面に列挙値を出さない
    pub def cargoKindLabel(k: CargoKind): String = match k {
        case CargoKind.General      => "一般貨物"
        case CargoKind.Hazardous    => "危険物"
        case CargoKind.Refrigerated => "冷凍・冷蔵貨物"
    }

    pub def statusLabel(s: EstimateStatus): String = match s {
        case EstimateStatus.Created => "作成済"
        case EstimateStatus.Expired => "期限切れ"
    }

    /// 選択肢の順序を 1 箇所で決める
    pub def allCargoKinds(): List[CargoKind] =
        CargoKind.General :: CargoKind.Hazardous :: CargoKind.Refrigerated :: Nil

    /// 利用者の言葉へ翻訳する
    pub def errorLabel(e: EstimateError): String = match e {
        case EstimateError.OriginIsRequired            => "出発地を選択してください"
        case EstimateError.DestinationIsRequired       => "目的地を選択してください"
        case EstimateError.OriginAndDestinationAreSame => "出発地と目的地には別の港を選んでください"
        case EstimateError.OriginIsMalformed           => "取り扱えない出発地が指定されました"
        case EstimateError.DestinationIsMalformed      => "取り扱えない目的地が指定されました"
        case EstimateError.ArrivalDeadlineIsRequired   => "希望期限を入力してください"
        case EstimateError.ArrivalDeadlineIsMalformed  => "希望期限は YYYY-MM-DD の形式で入力してください"
        case EstimateError.ArrivalDeadlineIsInPast     => "希望期限には今日以降の日付を入力してください"
        case EstimateError.WeightIsNotPositive         => "重量には 0 より大きい値を入力してください"
        case EstimateError.CargoKindIsMalformed        => "貨物種別を選択してください"
    }
}
