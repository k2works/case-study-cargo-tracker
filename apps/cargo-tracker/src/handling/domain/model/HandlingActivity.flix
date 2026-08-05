///
/// 荷役作業（Handling Context の集約ルート。US15・IT10）。
///
/// # この BC は本イテレーションで立ち上げる
///
/// 終盤（IT8-IT12）のアプローチはアウトサイドインだが、
/// **新しい Bounded Context のドメインは中盤の規律で組む**
/// （[開発戦略](../../../../../../docs/development/development_strategy.md)の但し書き。
/// IT8 の Estimation と同じ形）。効果を要求せず、現在時刻は引数で受け取る。
///
/// # 識別は追跡番号で行う
///
/// 設計ドキュメントは `CargoBookingId` を識別子としていたが、
/// **US15 の受入基準 1 は「追跡番号の入力で貨物を特定できる」**と定めている。
/// 荷役作業員が現場で手にしているのは貨物に貼られた追跡番号であり、予約番号ではない。
/// 利用者が持っていない識別子を要求する設計は IT4 で一度踏んでいる
/// （[ADR-0012](../../../../../../docs/adr/ADR-0012-cross-context-writes-go-through-the-target-aggregate.md)）。
///
mod HandlingModel {

    ///
    /// 荷役の種別。
    ///
    /// 設計の `HandlingType` は 5 値（RECEIVE / LOAD / UNLOAD / CUSTOMS / CLAIM）だが、
    /// **本 IT は 3 値に絞る**。CLAIM（引取）は US16・IT11、CUSTOMS（通関）は
    /// `customs_declaration` ごと US16 まで作らない。
    /// 値を先に置かないのは、**遷移させない値があると「実装済みに見える未実装」**に
    /// なるためである（`TransportStatus` は網羅性検査のために置いたが、
    /// こちらは画面の選択肢に直結する）。
    ///
    pub enum HandlingType with Eq, ToString {
        case Receive
        case Load
        case Unload
        // 通関（IT11・US16。ADR-0014）
        case Customs
        // 引取（IT11・US16）
        case Claim
    }

    ///
    /// 航海番号が要るか（設計のデシジョンテーブル）。
    ///
    /// 積込・荷降しは「どの便に載せたか」が分からないと、
    /// 予定の経路と突き合わせられない。受領は港に着いただけであり便を伴わない。
    ///
    pub def requiresVoyageNumber(t: HandlingType): Bool = match t {
        case HandlingType.Receive => false
        case HandlingType.Load    => true
        case HandlingType.Unload  => true
        // 通関も引取も便を伴わない。港で行う手続きである
        case HandlingType.Customs => false
        case HandlingType.Claim   => false
    }

    ///
    /// 荷役の妥当性。
    ///
    /// 設計は `isValidFor` の戻り値を `boolean` としていたが、
    /// **同じデシジョンテーブルが RECEIVE を「警告」、LOAD/UNLOAD を
    /// 「MISROUTED」と書き分けている**——真偽値では表現できない。
    ///
    /// `Warned` は記録を受け付けたうえで注意を出す。`Misrouted` は
    /// **貨物が予定と違う船に乗った**ことを意味し、経路の作り直しが要る。
    ///
    pub enum HandlingValidity with Eq, ToString {
        case Valid
        case Warned
        case Misrouted
    }

    ///
    /// 貨物の写し（ACL で取得した検証材料）。
    ///
    /// **Booking の `Cargo` そのものではない**。荷役の妥当性判定に要る値だけを持つ。
    /// 荷主・貨物種別・金額は持たない——持てる形にすると、いずれ越境が始まる。
    ///
    /// **予約番号は持たない**（IT11・M13）。Handling が貨物を特定するのは
    /// 追跡番号であり（ADR-0012 決定 4）、予約番号は Booking の語彙である。
    /// 写しに入れておくと、いずれ「ついでに予約番号で」という呼び出しが生まれる。
    pub enum CargoSnapshot({
        origin = String,
        destination = String,
        itineraryLegs = List[LegSnapshot]
    })

    pub enum LegSnapshot({
        loadLocation = String,
        unloadLocation = String,
        voyageNumber = String
    })

    pub enum HandlingError with Eq, ToString {
        case TrackingNumberIsRequired
        case LocationIsRequired
        case LocationCodeIsMalformed
        case CompletionTimeIsRequired
        case CompletionTimeIsMalformed
        case CompletionTimeIsInFuture
        case VoyageNumberIsRequired
        case VoyageNumberIsNotApplicable
        case HandlingTypeIsMalformed
        // 引取なのに荷受人確認が無い（IT11・US16）
        case ConsigneeConfirmationIsRequired
        // 引取以外に荷受人確認が付いている
        case ConsigneeConfirmationIsNotApplicable
    }

    ///
    /// 荷役作業。
    ///
    /// `voyageNumber` は種別によって要否が変わる（`requiresVoyageNumber`）。
    /// **`Option` にするのは要否が種別で決まるから**であり、
    /// 「まだ入力していない」を表すためではない。
    ///
    pub enum HandlingActivity({
        trackingNumber = String,
        handlingType = HandlingType,
        location = String,
        eventCompletionTime = String,
        voyageNumber = Option[String],
        operatorName = String,
        // 荷受人確認（引取のときだけ値を持つ。IT11・US16）
        consigneeConfirmation = Option[String],
        validity = HandlingValidity
    })

    ///
    /// 荷役作業を記録する。
    ///
    /// **妥当性は別に判定する**（`isValidFor`）。ここで検証するのは
    /// 「入力として成立しているか」だけである——港が予定と違うことは
    /// 入力の誤りではなく**業務上起こりうる事実**であり、記録は受け付ける。
    ///
    /// **未来の日時を拒む**。荷役は起きた事実の記録であり、
    /// これから起きることを記録すると、追跡画面が実態より先に進む。
    ///
    /// **担当者を必ず記録する**（IT11・M10）。`operator_name` 列は IT10 から
    /// 定義されていたが一度も書かれず、常に NULL だった。誰が記録したかが
    /// 分からないと、経路逸脱の記録を見た経路設計者が誰に確認すればよいか分からない。
    /// **定義されているのに常に NULL が一番読みにくい**——書くか落とすかを決める。
    pub def register(trackingNumber: String, handlingType: HandlingType,
                     location: String, completionTime: String,
                     voyageNumber: Option[String], operatorName: String,
                     consigneeConfirmation: Option[String],
                     now: String): Result[HandlingError, HandlingActivity] =
        let confirmation = consigneeConfirmation |> Option.flatMap(v ->
            if (String.trim(v) == "") None else Some(String.trim(v)));
        let voyage = voyageNumber |> Option.flatMap(v ->
            if (String.trim(v) == "") None else Some(String.trim(v)));
        // **大文字化してから検証する**。`isUnLocode` は大文字だけを通す仕様であり、
        // Booking・Estimation・Routing の 3 BC はいずれも正規化している。
        // Handling だけ `trim` のみだと、`jposa` と入力した現場が拒まれる
        let place = String.toUpperCase(String.trim(location));
        if (String.trim(trackingNumber) == "")
            Err(HandlingError.TrackingNumberIsRequired)
        else if (place == "")
            Err(HandlingError.LocationIsRequired)
        else if (not SharedCalendarText.isUnLocode(place))
            Err(HandlingError.LocationCodeIsMalformed)
        else if (String.trim(completionTime) == "")
            Err(HandlingError.CompletionTimeIsRequired)
        // **規則は共有カーネルが持つ**（`SharedCalendarText.isIsoDateTime`）。
        // IT10 のレビューで、ここに書き写した緩い判定（時刻部を検証しない）が
        // `2026-08-01T99:99` を通し、`CAST(? AS TIMESTAMP)` で 500 になることが
        // 分かった。書き写すと片方だけ緩む——コメントがそう書いていたのに
        // 実装が書き写していた
        else if (not SharedCalendarText.isIsoDateTime(completionTime))
            Err(HandlingError.CompletionTimeIsMalformed)
        else if (SharedCalendarText.dateOf(completionTime) > now)
            Err(HandlingError.CompletionTimeIsInFuture)
        else if (requiresVoyageNumber(handlingType) and Option.isEmpty(voyage))
            Err(HandlingError.VoyageNumberIsRequired)
        else if (handlingType == HandlingType.Claim and Option.isEmpty(confirmation))
            // **証跡の無い引取は成立しない**（US16 受入基準 1・2・ADR-0014 決定 3）。
            // 引取は所有権の移転であり、「後で入れる」余地を作らない
            Err(HandlingError.ConsigneeConfirmationIsRequired)
        else if (handlingType != HandlingType.Claim and not Option.isEmpty(confirmation))
            // 引取以外に確認コードを付けさせない。付けられると
            // 「誰かが受け取った」と読めるが、荷受人へ渡していない
            Err(HandlingError.ConsigneeConfirmationIsNotApplicable)
        else if (not requiresVoyageNumber(handlingType) and not Option.isEmpty(voyage))
            // **受領に航海番号を付けさせない**。付けられると
            // 「この便で受け取った」という意味に読めるが、受領は便を伴わない
            Err(HandlingError.VoyageNumberIsNotApplicable)
        else
            Ok(HandlingActivity.HandlingActivity({
                trackingNumber = String.trim(trackingNumber),
                handlingType = handlingType,
                location = place,
                eventCompletionTime = completionTime,
                voyageNumber = voyage,
                operatorName = String.trim(operatorName),
                consigneeConfirmation = confirmation,
                // 妥当性は貨物の写しと突き合わせて決まる。**判定前は `Valid` に倒さない**——
                // 判定していないことと「妥当だった」ことは違う。
                // `withValidity` を通していない活動は保存しない（`RegisterHandling` が保証）
                validity = HandlingValidity.Valid
            }))

    /// 判定結果を載せた活動を返す
    pub def withValidity(a: HandlingActivity, v: HandlingValidity): HandlingActivity =
        let HandlingActivity.HandlingActivity(x) = a;
        HandlingActivity.HandlingActivity({ validity = v | x })

    ///
    /// 予定の経路と突き合わせて妥当性を判定する（US15 受入基準 7）。
    ///
    /// 設計のデシジョンテーブルをそのまま実装する。
    ///
    /// | 種別 | 場所の照合先 | 不一致のとき |
    /// | :--- | :--- | :--- |
    /// | RECEIVE | 出発港（`RouteSpecification.origin`） | **警告**（業務は続けられる） |
    /// | LOAD | 旅程の積込港（同じ航海番号の区間） | **MISROUTED** |
    /// | UNLOAD | 旅程の荷降港（同じ航海番号の区間） | **MISROUTED** |
    ///
    /// **受領の不一致を MISROUTED にしない**。集荷場所が予定と違うのは
    /// 「別の倉庫で受け取った」程度のことが多く、経路そのものは生きている。
    /// 一方 LOAD/UNLOAD の不一致は**貨物が違う船に乗った**ことを意味する。
    ///
    /// **旅程が無い場合**（経路未割り当てで荷役が始まった）は照合先が無い。
    /// `Warned` に倒す——`Misrouted` にすると「経路が違う」と読めるが、
    /// 経路そのものが無い。
    ///
    pub def isValidFor(a: HandlingActivity, snapshot: CargoSnapshot): HandlingValidity =
        let place = locationOf(a);
        match handlingTypeOf(a) {
            case HandlingType.Receive =>
                if (place == originOf(snapshot)) HandlingValidity.Valid
                else HandlingValidity.Warned
            case HandlingType.Load =>
                matchesLeg(a, snapshot, legLoadLocationOf)
            case HandlingType.Unload =>
                matchesLeg(a, snapshot, legUnloadLocationOf)
            case HandlingType.Customs =>
                // 通関は仕向港で行う。違っても業務は続けられる（別の税関で
                // 通した等）ため**警告に留める**——経路そのものは生きている
                if (place == destinationOf(snapshot)) HandlingValidity.Valid
                else HandlingValidity.Warned
            case HandlingType.Claim =>
                // 設計のデシジョンテーブル CLAIM 行。**不一致は警告**——
                // 荷受人が別の場所で受け取ることは業務上ありうる
                if (place == destinationOf(snapshot)) HandlingValidity.Valid
                else HandlingValidity.Warned
        }

    ///
    /// 同じ航海番号の区間を探し、指定された側の港と一致するかを見る。
    ///
    /// **航海番号で区間を特定してから港を比べる**。港だけで比べると、
    /// 同じ港を 2 度通る旅程（積替で戻る）で誤って一致する。
    ///
    def matchesLeg(a: HandlingActivity, snapshot: CargoSnapshot,
                   portOf: LegSnapshot -> String): HandlingValidity =
        let legs = itineraryLegsOf(snapshot);
        if (List.isEmpty(legs))
            HandlingValidity.Warned
        else
            let voyage = voyageNumberOf(a) |> Option.getWithDefault("");
            match List.find(l -> legVoyageNumberOf(l) == voyage, legs) {
                // **予定に無い便に載せた**——旅程のどの区間でもない
                case None    => HandlingValidity.Misrouted
                case Some(l) =>
                    if (portOf(l) == locationOf(a)) HandlingValidity.Valid
                    else HandlingValidity.Misrouted
            }

    ///
    /// この荷役が結果としてもたらす輸送状態（US15 受入基準 4）。
    ///
    /// **状態は保持する。履歴から導出しない**——記録済みの教訓であり、
    /// `TrackingActivity` を永続化型に是正したのと同じ理由である。
    /// ここが返すのは「次に書くべき状態」であって、状態そのものではない。
    ///
    /// **`None` は「状態を変えない」である**（IT11・ADR-0014 決定 2）。
    /// 現在の状態を書き戻す形にしない——同じ値の書き戻しは競合を隠す。
    ///
    /// **最終目的港での荷降しは引取待ちにする**（US16）。これが無いと
    /// 「引取できる貨物」を画面で選べず、引取の導線が成立しない。
    pub def resultingStatusFor(a: HandlingActivity, snapshot: CargoSnapshot):
            Option[SharedTransportStatus.TransportStatus] =
        match handlingTypeOf(a) {
            case HandlingType.Receive =>
                Some(SharedTransportStatus.TransportStatus.Received)
            case HandlingType.Load =>
                Some(SharedTransportStatus.TransportStatus.Loaded)
            case HandlingType.Unload =>
                if (locationOf(a) == destinationOf(snapshot))
                    Some(SharedTransportStatus.TransportStatus.AwaitingClaim)
                else
                    Some(SharedTransportStatus.TransportStatus.Unloaded)
            // 通関は貨物の位置も輸送の段階も変えない
            case HandlingType.Customs => None
            case HandlingType.Claim =>
                Some(SharedTransportStatus.TransportStatus.Claimed)
        }

    // ---- 種別の変換 ----

    /// 画面・永続化の値から種別へ。**未知の値は `None`**（既定値へ倒さない）
    pub def typeFromPersisted(value: String): Option[HandlingType] = match value {
        case "RECEIVE" => Some(HandlingType.Receive)
        case "LOAD"    => Some(HandlingType.Load)
        case "UNLOAD"  => Some(HandlingType.Unload)
        case "CUSTOMS" => Some(HandlingType.Customs)
        case "CLAIM"   => Some(HandlingType.Claim)
        case _         => None
    }

    pub def typeToPersisted(t: HandlingType): String = match t {
        case HandlingType.Receive => "RECEIVE"
        case HandlingType.Load    => "LOAD"
        case HandlingType.Unload  => "UNLOAD"
        case HandlingType.Customs => "CUSTOMS"
        case HandlingType.Claim   => "CLAIM"
    }

    /// 画面に出す表示名（**列挙値を出さない**）
    pub def typeLabel(t: HandlingType): String = match t {
        case HandlingType.Receive => "受領"
        case HandlingType.Load    => "積込"
        case HandlingType.Unload  => "荷降し"
        case HandlingType.Customs => "通関"
        case HandlingType.Claim   => "引取"
    }

    pub def validityFromPersisted(value: String): Option[HandlingValidity] = match value {
        case "VALID"     => Some(HandlingValidity.Valid)
        case "WARNED"    => Some(HandlingValidity.Warned)
        case "MISROUTED" => Some(HandlingValidity.Misrouted)
        case _           => None
    }

    pub def validityToPersisted(v: HandlingValidity): String = match v {
        case HandlingValidity.Valid     => "VALID"
        case HandlingValidity.Warned    => "WARNED"
        case HandlingValidity.Misrouted => "MISROUTED"
    }

    //
    // 注意文（「場所に誤りがないか確認してください」等）は**ここに置かない**
    // （IT11・M15）。IT10 は集約と `Flash` が同じ趣旨の文言を二重に持ち、
    // **集約側は一度も使われていなかった**——片方だけ直しても誰も気付かない。
    //
    // 画面に出す言葉は表示の関心であり、正典は `SharedHttpFlash.handlingMessage`。
    // 集約が持つのは判定（`HandlingValidity`）までである。
    //

    // ---- 生成 ----

    pub def cargoSnapshot(origin: String, destination: String,
                          itineraryLegs: List[LegSnapshot]): CargoSnapshot =
        CargoSnapshot.CargoSnapshot({
            origin = origin, destination = destination,
            itineraryLegs = itineraryLegs
        })

    pub def legSnapshot(loadLocation: String, unloadLocation: String,
                        voyageNumber: String): LegSnapshot =
        LegSnapshot.LegSnapshot({
            loadLocation = loadLocation, unloadLocation = unloadLocation,
            voyageNumber = voyageNumber
        })

    /// 永続化された行から復元する
    pub def reconstruct(trackingNumber: String, handlingType: HandlingType,
                        location: String, eventCompletionTime: String,
                        voyageNumber: Option[String], operatorName: String,
                        consigneeConfirmation: Option[String],
                        validity: HandlingValidity): HandlingActivity =
        HandlingActivity.HandlingActivity({
            trackingNumber = trackingNumber, handlingType = handlingType,
            location = location, eventCompletionTime = eventCompletionTime,
            voyageNumber = voyageNumber, operatorName = operatorName,
            consigneeConfirmation = consigneeConfirmation, validity = validity
        })

    // ---- 参照 ----

    pub def trackingNumberOf(a: HandlingActivity): String =
        let HandlingActivity.HandlingActivity(x) = a; x#trackingNumber
    pub def handlingTypeOf(a: HandlingActivity): HandlingType =
        let HandlingActivity.HandlingActivity(x) = a; x#handlingType
    pub def locationOf(a: HandlingActivity): String =
        let HandlingActivity.HandlingActivity(x) = a; x#location
    pub def eventCompletionTimeOf(a: HandlingActivity): String =
        let HandlingActivity.HandlingActivity(x) = a; x#eventCompletionTime
    pub def voyageNumberOf(a: HandlingActivity): Option[String] =
        let HandlingActivity.HandlingActivity(x) = a; x#voyageNumber
    pub def consigneeConfirmationOf(a: HandlingActivity): Option[String] =
        let HandlingActivity.HandlingActivity(x) = a; x#consigneeConfirmation
    pub def operatorNameOf(a: HandlingActivity): String =
        let HandlingActivity.HandlingActivity(x) = a; x#operatorName
    pub def validityOf(a: HandlingActivity): HandlingValidity =
        let HandlingActivity.HandlingActivity(x) = a; x#validity

    pub def originOf(s: CargoSnapshot): String =
        let CargoSnapshot.CargoSnapshot(x) = s; x#origin
    pub def destinationOf(s: CargoSnapshot): String =
        let CargoSnapshot.CargoSnapshot(x) = s; x#destination
    pub def itineraryLegsOf(s: CargoSnapshot): List[LegSnapshot] =
        let CargoSnapshot.CargoSnapshot(x) = s; x#itineraryLegs

    pub def legLoadLocationOf(l: LegSnapshot): String =
        let LegSnapshot.LegSnapshot(x) = l; x#loadLocation
    pub def legUnloadLocationOf(l: LegSnapshot): String =
        let LegSnapshot.LegSnapshot(x) = l; x#unloadLocation
    pub def legVoyageNumberOf(l: LegSnapshot): String =
        let LegSnapshot.LegSnapshot(x) = l; x#voyageNumber
}
