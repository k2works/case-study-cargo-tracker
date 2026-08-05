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
    pub enum CargoSnapshot({
        bookingId = String,
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
                     now: String): Result[HandlingError, HandlingActivity] =
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
    pub def resultingStatusOf(t: HandlingType): SharedTransportStatus.TransportStatus =
        match t {
            case HandlingType.Receive => SharedTransportStatus.TransportStatus.Received
            case HandlingType.Load    => SharedTransportStatus.TransportStatus.Loaded
            case HandlingType.Unload  => SharedTransportStatus.TransportStatus.Unloaded
        }

    // ---- 種別の変換 ----

    /// 画面・永続化の値から種別へ。**未知の値は `None`**（既定値へ倒さない）
    pub def typeFromPersisted(value: String): Option[HandlingType] = match value {
        case "RECEIVE" => Some(HandlingType.Receive)
        case "LOAD"    => Some(HandlingType.Load)
        case "UNLOAD"  => Some(HandlingType.Unload)
        case _         => None
    }

    pub def typeToPersisted(t: HandlingType): String = match t {
        case HandlingType.Receive => "RECEIVE"
        case HandlingType.Load    => "LOAD"
        case HandlingType.Unload  => "UNLOAD"
    }

    /// 画面に出す表示名（**列挙値を出さない**）
    pub def typeLabel(t: HandlingType): String = match t {
        case HandlingType.Receive => "受領"
        case HandlingType.Load    => "積込"
        case HandlingType.Unload  => "荷降し"
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

    pub def cargoSnapshot(bookingId: String, origin: String, destination: String,
                          itineraryLegs: List[LegSnapshot]): CargoSnapshot =
        CargoSnapshot.CargoSnapshot({
            bookingId = bookingId, origin = origin, destination = destination,
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
                        validity: HandlingValidity): HandlingActivity =
        HandlingActivity.HandlingActivity({
            trackingNumber = trackingNumber, handlingType = handlingType,
            location = location, eventCompletionTime = eventCompletionTime,
            voyageNumber = voyageNumber, operatorName = operatorName,
            validity = validity
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
    pub def operatorNameOf(a: HandlingActivity): String =
        let HandlingActivity.HandlingActivity(x) = a; x#operatorName
    pub def validityOf(a: HandlingActivity): HandlingValidity =
        let HandlingActivity.HandlingActivity(x) = a; x#validity

    pub def bookingIdOf(s: CargoSnapshot): String =
        let CargoSnapshot.CargoSnapshot(x) = s; x#bookingId
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
