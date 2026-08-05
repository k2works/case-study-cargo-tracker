///
/// 追跡活動（Tracking Context の集約ルート）。
///
/// IT1-IT9 の Tracking は**読み取りだけ**だった（公開追跡・US03）。
/// US14 で初めて書き込み側が要る。
///
/// # 状態は保持する。履歴から導出しない
///
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md)は当初
/// `currentStatus()` でイベント履歴から輸送状態を**導出する**形で描いていた。
/// 本 IT はこれを是正し、`transportStatus` を保持する。
///
/// 導出型は「発生前の状態を永続化せず履歴から再導出する」形であり、
/// **ユニットテストが緑でもクロスリクエストで誤復帰する**。
/// DB（`V1__init.sql`）も最初から `transport_status` 列を持っており、
/// 実装は既に永続化型だった——設計だけが導出型のまま残っていた。
///
/// # 集約は効果を要求しない
///
/// 追跡番号も現在時刻も**引数で受け取る**。採番はアプリケーション層の仕事であり、
/// 集約が知るのは「与えられた番号が形式として妥当か」だけである。
///
mod TrackingModel {
    use SharedTransportStatus.TransportStatus

    ///
    /// 追跡活動。
    ///
    /// `bookingId` は Booking Context の予約番号である。**BC を越える参照**だが、
    /// 追跡は「どの予約の貨物か」を知らなければ成立しない。
    /// 保持するのは識別子だけで、予約の内容（荷主・貨物種別）は持たない——
    /// 持てる形にすると、いずれ「ついでに荷主名も」と越境が始まる。
    ///
    pub enum TrackingActivity({
        trackingNumber = String,
        bookingId = String,
        transportStatus = TransportStatus,
        pendingEvents = List[HandlingEvent]
    })

    ///
    /// 追跡タイムラインに載る出来事（IT11・H8）。
    ///
    /// # なぜ Tracking が持つのか
    ///
    /// 荷役の記録（`handling_activity`）は **Handling の業務記録**であり、
    /// 妥当性の判定や担当者を持つ。荷主が見るのは「いつ・どこで・何が起きたか」だけで、
    /// 経路逸脱の判定は見せない。**同じ出来事の別の側面**であり、
    /// 一方をもう一方の表示用に流用すると、荷役の内部事情が荷主の画面に漏れる。
    ///
    /// IT10 では `tracking_handling_event` に**誰も書いていなかった**。
    /// テーブルはあり、公開追跡はそれを読み、荷役は別のテーブルへ書いていた。
    ///
    /// # 種別が String である理由
    ///
    /// `HandlingType` は Handling Context の型であり、Tracking から参照できない
    /// （`arch-lint` 規約 4）。**翻訳は ACL アダプタが行う**。
    /// `transportStatus` が共有カーネルの型なのと同じで、境界を越えるのは値だけである。
    ///
    pub enum HandlingEvent({
        eventType = String,
        eventTime = String,
        location = String,
        voyageNumber = Option[String]
    })

    pub def eventTypeOf(e: HandlingEvent): String =
        let HandlingEvent.HandlingEvent(x) = e; x#eventType

    pub def eventTimeOf(e: HandlingEvent): String =
        let HandlingEvent.HandlingEvent(x) = e; x#eventTime

    pub def eventLocationOf(e: HandlingEvent): String =
        let HandlingEvent.HandlingEvent(x) = e; x#location

    pub def eventVoyageNumberOf(e: HandlingEvent): Option[String] =
        let HandlingEvent.HandlingEvent(x) = e; x#voyageNumber

    pub def event(eventType: String, eventTime: String, location: String,
                  voyageNumber: Option[String]): HandlingEvent =
        HandlingEvent.HandlingEvent({
            eventType = eventType,
            eventTime = eventTime,
            location = location,
            voyageNumber = voyageNumber
        })

    pub enum TrackingError with Eq, ToString {
        case TrackingNumberIsRequired
        case TrackingNumberIsMalformed
        case BookingIdIsRequired
        // 手動更新（US17・IT11）
        case StatusWouldRewind
        case OccurredAtIsRequired
        case OccurredAtIsMalformed
        case LocationIsRequired
        case StatusIsUnknown
    }

    ///
    /// 追跡番号を発行する（US14 受入基準 1・3）。
    ///
    /// **輸送状態は `NOT_RECEIVED`（受領待ち）で始まる**。荷役はまだ何も起きていない。
    /// 既定値を `UNKNOWN` にしないのは、「まだ受け取っていない」と
    /// 「状態が読めない」が業務上まったく違うためである。
    ///
    pub def issue(trackingNumber: String, bookingId: String):
            Result[TrackingError, TrackingActivity] =
        if (String.trim(trackingNumber) == "")
            Err(TrackingError.TrackingNumberIsRequired)
        else if (not isWellFormed(trackingNumber))
            Err(TrackingError.TrackingNumberIsMalformed)
        else if (String.trim(bookingId) == "")
            Err(TrackingError.BookingIdIsRequired)
        else
            Ok(TrackingActivity.TrackingActivity({
                trackingNumber = trackingNumber,
                bookingId = bookingId,
                transportStatus = SharedTransportStatus.TransportStatus.NotReceived,
                pendingEvents = Nil
            }))

    ///
    /// 荷役の結果を輸送状態へ反映する（US15・IT10）。
    ///
    /// # 遷移の妥当性を今は見ない
    ///
    /// 荷役は**起きた事実の記録**であり、「受領していないのに積み込まれた」は
    /// 業務として実際に起こる（受領の記録を忘れた等）。ここで拒むと
    /// 現場が記録できなくなる——記録を優先し、ずれは荷役の妥当性判定
    /// （`HandlingModel.isValidFor`）が拾う。
    ///
    /// # それでも集約を通す理由
    ///
    /// 規則が無いことと、規則を書く場所が無いことは違う。
    /// IT10 のレビューで、Handling → Tracking の書き込みが
    /// `updateTransportStatus(trackingNumber, status)` という**列単位のポート**を
    /// 経由しており、集約を一度も通っていないことが分かった。
    /// [ADR-0012](../../../../../../docs/adr/ADR-0012-cross-context-writes-go-through-the-target-aggregate.md)
    /// が却下した選択肢 B（直接 SQL）と保証されるものが同じ状態だった。
    ///
    /// **US16（引取）で `CLAIMED` を足すとき、規則を書く場所がここに要る**。
    /// 関門の位置を先に正す。
    ///
    /// **出来事を記録し、その結果として状態が変わる**。
    ///
    /// 順序が逆ではない。IT10 は「状態を変える」だけで出来事を残さなかったため、
    /// 荷主にはバッジが変わるだけで**いつ・どこで積まれたかが出なかった**（H8）。
    ///
    /// 積むのは末尾でなく先頭で構わない——並び順は SQL の `ORDER BY` が決める。
    ///
    /// **`status` が `None` なら状態を据え置く**（IT11・ADR-0014 決定 2）。
    /// 通関のように「出来事は起きたが輸送の段階は進まない」ものがある。
    pub def applyHandling(a: TrackingActivity, status: Option[TransportStatus],
                          occurred: HandlingEvent): TrackingActivity =
        let TrackingActivity.TrackingActivity(x) = a;
        TrackingActivity.TrackingActivity({
            transportStatus = Option.getWithDefault(x#transportStatus, status),
            pendingEvents = occurred :: x#pendingEvents | x })

    ///
    /// 追跡管理者が手で状態を更新する（US17）。
    ///
    /// # なぜ荷役と別の入口なのか
    ///
    /// 出港・入港は**荷役作業ではない**。積込を記録した後、船が出るまでに
    /// 時間があり、その間の貨物は「積み込み済」のままである。
    /// 荷役だけでは輸送の実態を表せない——US17 の「なぜ」がここにある。
    ///
    /// # 巻き戻しを拒む
    ///
    /// 荷役の反映（`applyHandling`）は遷移の妥当性を見ない。
    /// **起きた事実の記録だから**であり、現場が記録できなくなるほうが害が大きい。
    ///
    /// 手動更新は違う。**人が選択肢から選んで打つ**ものであり、
    /// 打ち間違いで「受取済」へ戻すと、荷主には貨物が港へ帰ったように見える。
    /// 記録の忠実さを守る理由が無い側では、拒むほうが正しい。
    ///
    /// **時刻の逆行も拒む**。後から古い出来事を入れると、
    /// タイムラインの最新行が実態と食い違う。
    ///
    pub def updateManually(a: TrackingActivity, status: TransportStatus,
                           location: String, occurredAt: String,
                           latestEventTime: Option[String]):
            Result[TrackingError, TrackingActivity] =
        if (String.trim(location) == "")
            Err(TrackingError.LocationIsRequired)
        else if (String.trim(occurredAt) == "")
            Err(TrackingError.OccurredAtIsRequired)
        else if (not SharedCalendarText.isIsoDateTime(occurredAt))
            Err(TrackingError.OccurredAtIsMalformed)
        else if (isRewind(transportStatusOf(a), status))
            Err(TrackingError.StatusWouldRewind)
        else if (isBefore(occurredAt, latestEventTime))
            Err(TrackingError.OccurredAtIsMalformed)
        else
            Ok(applyHandling(a, Some(status),
                             event(SharedTransportStatus.toPersisted(status),
                                   occurredAt, String.toUpperCase(String.trim(location)),
                                   None)))

    ///
    /// 段階が前に戻るか。
    ///
    /// **順序は輸送の進み方そのもの**である。`EXCEPTION` と `UNKNOWN` は
    /// 段階を持たない——例外はどの段階でも起こりうるため、順序の外に置く。
    ///
    def isRewind(current: TransportStatus, next: TransportStatus): Bool =
        match (stageOf(current), stageOf(next)) {
            case (Some(c), Some(n)) => n < c
            case _                  => false
        }

    def stageOf(s: TransportStatus): Option[Int32] = match s {
        case TransportStatus.NotReceived    => Some(0)
        case TransportStatus.Received       => Some(1)
        case TransportStatus.Loaded         => Some(2)
        case TransportStatus.OnboardCarrier => Some(3)
        case TransportStatus.Unloaded       => Some(4)
        case TransportStatus.AwaitingClaim  => Some(5)
        case TransportStatus.Claimed        => Some(6)
        // 例外はどの段階でも起こりうる。順序の外に置く
        case TransportStatus.Exception      => None
        case TransportStatus.Unknown        => None
    }

    /// 直近の出来事より前か。**文字列比較で足りる**（ISO 8601 は辞書順＝時刻順）
    def isBefore(occurredAt: String, latest: Option[String]): Bool =
        match latest {
            case None    => false
            case Some(t) => occurredAt < t
        }

    /// まだ書かれていない出来事。永続化した側が空にする
    pub def pendingEventsOf(a: TrackingActivity): List[HandlingEvent] =
        let TrackingActivity.TrackingActivity(x) = a; x#pendingEvents

    ///
    /// 追跡番号の形式（`TRK-YYYYMMDD-NNNN`）。
    ///
    /// 形式は [UI 設計](../../../../../../docs/design/ui_design.md)が定めている。
    /// **日付を含むため、同一日内の連番が採番の競合点**になる——
    /// これは US14 の成功基準 2 が守るところである。
    ///
    /// 桁数と区切りだけを見る。日付として実在するかまでは見ない——
    /// 採番するのは自分自身であり、外から任意の値が来る経路が無い。
    /// **外から来るようになったら検査を足す**（現時点で足すと、
    /// 守っていない前提を守っているように見せてしまう）。
    ///
    pub def isWellFormed(value: String): Bool =
        String.length(value) == 17 and
        String.startsWith(prefix = "TRK-", value) and
        String.charAt(12, value) == '-' and
        allDigits(String.slice(start = 4, end = 12, value)) and
        allDigits(String.slice(start = 13, end = 17, value))

    def allDigits(value: String): Bool =
        String.length(value) > 0 and
        String.forAll(c -> Char.isDigit(c), value)

    ///
    /// 追跡番号を組み立てる（`TRK-YYYYMMDD-NNNN`）。
    ///
    /// 日付は `YYYY-MM-DD` で受け取り、ハイフンを落とす。
    /// **連番は 4 桁でゼロ詰めする**。桁が揃っていないと、
    /// 文字列として並べたときに順序が狂う（`TRK-...-10` が `TRK-...-2` より前に来る）。
    ///
    pub def formatNumber(isoDate: String, sequence: Int32): String =
        let compact = String.replace(src = "-", dst = "", isoDate);
        "TRK-" + compact + "-" + padTo4(sequence)

    def padTo4(n: Int32): String =
        let text = Int32.toString(n);
        let short = String.length(text);
        if (short >= 4) text
        else String.repeat(4 - short, "0") + text

    // ---- 参照 ----

    pub def trackingNumberOf(a: TrackingActivity): String =
        let TrackingActivity.TrackingActivity(x) = a; x#trackingNumber

    pub def bookingIdOf(a: TrackingActivity): String =
        let TrackingActivity.TrackingActivity(x) = a; x#bookingId

    pub def transportStatusOf(a: TrackingActivity): TransportStatus =
        let TrackingActivity.TrackingActivity(x) = a; x#transportStatus

    ///
    /// 永続化された行から復元する。
    ///
    /// 未知の輸送状態は `None` を返す。**既定値へ倒さない**——
    /// 「受領待ち」に倒すと、輸送中の貨物が未受領に見える。
    ///
    pub def reconstruct(trackingNumber: String, bookingId: String,
                        persistedStatus: String): Option[TrackingActivity] =
        match SharedTransportStatus.fromPersisted(persistedStatus) {
            case None    => None
            case Some(s) => Some(TrackingActivity.TrackingActivity({
                trackingNumber = trackingNumber,
                bookingId = bookingId,
                transportStatus = s,
                pendingEvents = Nil
            }))
        }
}
