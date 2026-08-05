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
        // **例外の発生前の状態**（US19・IT12）。解決時の復帰先である。
        // **カラムに永続化する**——履歴から再導出すると、ユニットテストが
        // 緑でもクロスリクエストで誤復帰する（記録済みの教訓）
        statusBeforeException = Option[TransportStatus],
        pendingEvents = List[HandlingEvent],
        // 未書き込みの例外（発生／解決）。永続化した側が空にする
        pendingException = Option[ExceptionRecord]
    })

    ///
    /// 例外の記録（US19・IT12）。
    ///
    /// 発生と解決を**同じ型で表す**。`resolvedAt` が入っていれば解決である——
    /// 別の型にすると「発生していない解決」が型として作れてしまう。
    ///
    pub enum ExceptionRecord({
        exceptionType = ExceptionType,
        occurredAt = String,
        location = String,
        description = String,
        escalated = Bool,
        resolvedAt = Option[String],
        resolutionNotes = Option[String],
        revisedArrival = Option[String]
    })

    pub def exceptionTypeOfRecord(r: ExceptionRecord): ExceptionType =
        let ExceptionRecord.ExceptionRecord(x) = r; x#exceptionType
    pub def exceptionOccurredAtOf(r: ExceptionRecord): String =
        let ExceptionRecord.ExceptionRecord(x) = r; x#occurredAt
    pub def exceptionLocationOf(r: ExceptionRecord): String =
        let ExceptionRecord.ExceptionRecord(x) = r; x#location
    pub def exceptionDescriptionOf(r: ExceptionRecord): String =
        let ExceptionRecord.ExceptionRecord(x) = r; x#description
    pub def exceptionEscalatedOf(r: ExceptionRecord): Bool =
        let ExceptionRecord.ExceptionRecord(x) = r; x#escalated
    pub def exceptionResolvedAtOf(r: ExceptionRecord): Option[String] =
        let ExceptionRecord.ExceptionRecord(x) = r; x#resolvedAt
    pub def exceptionResolutionNotesOf(r: ExceptionRecord): Option[String] =
        let ExceptionRecord.ExceptionRecord(x) = r; x#resolutionNotes
    pub def exceptionRevisedArrivalOf(r: ExceptionRecord): Option[String] =
        let ExceptionRecord.ExceptionRecord(x) = r; x#revisedArrival

    pub def pendingExceptionOf(a: TrackingActivity): Option[ExceptionRecord] =
        let TrackingActivity.TrackingActivity(x) = a; x#pendingException

    ///
    /// 追跡例外（US19・IT12）。
    ///
    /// 本リリースで扱うのは **`DELAY` のみ**である。
    /// `DAMAGE` / `LOST` / `CUSTOMS_HOLD` は US20（将来リリース）。
    /// **実装しない値を列挙に置かない**——選べるのに動かない選択肢を出すと、
    /// 利用者は壊れていると受け取る。
    ///
    pub enum ExceptionType with Eq, ToString {
        case Delay
    }

    pub def exceptionTypeFromPersisted(value: String): Option[ExceptionType] =
        match value {
            case "DELAY" => Some(ExceptionType.Delay)
            case _       => None
        }

    pub def exceptionTypeToPersisted(t: ExceptionType): String =
        match t {
            case ExceptionType.Delay => "DELAY"
        }

    /// 画面に出す表示名（**列挙値を出さない**）
    pub def exceptionTypeLabel(t: ExceptionType): String =
        match t {
            case ExceptionType.Delay => "遅延"
        }

    ///
    /// エスカレーションの閾値（時間）。
    ///
    /// [テスト戦略](../../../../../../docs/design/test_strategy.md) 8.2 と
    /// ドメインモデルのビジネスルールが 48 時間と定めている。
    ///
    pub def escalationThresholdHours(): Int32 = 48

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
        // **逆行は形式不正と分ける**（IT11 レビュー）。畳むと、書式は合っているのに
        // 「実在する日時を」と案内され、利用者は何を直せばよいか分からない
        case OccurredAtWouldRewind
        case LocationIsRequired
        case LocationCodeIsMalformed
        // 例外（US19・IT12）
        case DescriptionIsRequired
        case NoActiveException
        // 段階を持たない状態（EXCEPTION / UNKNOWN）は手で付けられない
        case StatusIsNotSelectable
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
                statusBeforeException = None,
                pendingEvents = Nil,
                pendingException = None
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
        // **場所を大文字化してから検証する**（IT11 レビュー）。荷役登録は同じ IT で
        // マスタ照合まで入れたのに、こちらは空文字しか見ておらず、
        // `TOKYO` と打つと外部キー違反で 500 になっていた——同じ穴を片側だけ塞いでいた
        let place = String.toUpperCase(String.trim(location));
        if (place == "")
            Err(TrackingError.LocationIsRequired)
        else if (not SharedCalendarText.isUnLocode(place))
            Err(TrackingError.LocationCodeIsMalformed)
        else if (String.trim(occurredAt) == "")
            Err(TrackingError.OccurredAtIsRequired)
        else if (not SharedCalendarText.isIsoDateTime(occurredAt))
            Err(TrackingError.OccurredAtIsMalformed)
        else if (Option.isEmpty(stageOf(status)))
            // **段階を持たない状態は手で付けられない**（IT11 クローズ時レビュー）。
            // `EXCEPTION` を踏み台にすると `CLAIMED → EXCEPTION → NOT_RECEIVED` の
            // 2 手で巻き戻せる——順序の外にある値を経由すると順序の判定が効かない。
            //
            // 例外は理由と対応の記録を伴う業務であり（US19/US20）、
            // 状態だけを付け替える操作にしてはいけない。画面も出していないが、
            // **選択肢は誰でも書き換えられる**ため集約で拒む
            Err(TrackingError.StatusIsNotSelectable)
        else if (isRewind(transportStatusOf(a), status))
            Err(TrackingError.StatusWouldRewind)
        else if (isBefore(occurredAt, latestEventTime))
            Err(TrackingError.OccurredAtWouldRewind)
        else
            Ok(applyHandling(a, Some(status),
                             event(SharedTransportStatus.toPersisted(status),
                                   occurredAt, place, None)))

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

    ///
    /// 直近の出来事より前か。
    ///
    /// **比較の前に書式を揃える**（IT11 レビュー）。画面から来るのは
    /// `2026-08-02T09:00`（区切りが `T`・16 文字）、DB から来るのは
    /// `2026-08-02 09:00:00`（区切りが空白・19 文字）である。
    /// そのまま辞書順で比べると 10 文字目が `T`(0x54) と ` `(0x20) の比較になり、
    /// **同じ日付なら必ず「前ではない」と判定される**——
    /// 「ISO 8601 は辞書順＝時刻順」は、2 値の書式が同じときにだけ成り立つ。
    ///
    /// 揃えたあとは辞書順で足りる。
    ///
    def isBefore(occurredAt: String, latest: Option[String]): Bool =
        match latest {
            case None    => false
            case Some(t) =>
                SharedCalendarText.toSqlTimestamp(occurredAt)
                    < SharedCalendarText.toSqlTimestamp(t)
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
                        persistedStatus: String,
                        persistedStatusBefore: Option[String]):
            Option[TrackingActivity] =
        match SharedTransportStatus.fromPersisted(persistedStatus) {
            case None    => None
            case Some(s) => Some(TrackingActivity.TrackingActivity({
                trackingNumber = trackingNumber,
                bookingId = bookingId,
                transportStatus = s,
                // 未知の値は `None`（既定値へ倒さない）。復帰先が読めなければ
                // **復帰させない**ほうがよい——誤った状態へ戻すより害が小さい
                statusBeforeException =
                    persistedStatusBefore
                        |> Option.flatMap(SharedTransportStatus.fromPersisted),
                pendingEvents = Nil,
                pendingException = None
            }))
        }

    // ---- 例外（US19・IT12） ----

    ///
    /// 例外を記録する（US19 受入基準 1・2）。
    ///
    /// **発生前の状態を退避する**（観点 1）。解決時の復帰先であり、
    /// 履歴から再導出しない——ユニットテストが緑でもクロスリクエストで誤復帰する。
    ///
    /// **すでに例外中なら退避を上書きしない**。2 度目の例外で `EXCEPTION` を
    /// 退避すると、解決しても例外のままになる。
    ///
    /// 出来事としても残す（荷主のタイムラインに出る。H8 と同じ形）。
    ///
    pub def raiseException(a: TrackingActivity, exceptionType: ExceptionType,
                           location: String, occurredAt: String,
                           description: String, escalated: Bool):
            Result[TrackingError, TrackingActivity] =
        let place = String.toUpperCase(String.trim(location));
        if (place == "")
            Err(TrackingError.LocationIsRequired)
        else if (not SharedCalendarText.isUnLocode(place))
            Err(TrackingError.LocationCodeIsMalformed)
        else if (String.trim(occurredAt) == "")
            Err(TrackingError.OccurredAtIsRequired)
        else if (not SharedCalendarText.isIsoDateTime(occurredAt))
            Err(TrackingError.OccurredAtIsMalformed)
        else if (String.trim(description) == "")
            // **理由の無い例外は業務にならない**。荷主が知りたいのは
            // 「異常あり」ではなく「何が起きたか」である
            Err(TrackingError.DescriptionIsRequired)
        else
            let TrackingActivity.TrackingActivity(x) = a;
            let before = match x#statusBeforeException {
                case Some(_) => x#statusBeforeException
                case None    => Some(x#transportStatus)
            };
            Ok(TrackingActivity.TrackingActivity({
                transportStatus = SharedTransportStatus.TransportStatus.Exception,
                statusBeforeException = before,
                pendingException = Some(ExceptionRecord.ExceptionRecord({
                    exceptionType = exceptionType, occurredAt = occurredAt,
                    location = place, description = String.trim(description),
                    escalated = escalated, resolvedAt = None,
                    resolutionNotes = None, revisedArrival = None
                })) | x }))

    ///
    /// 例外を解決する（US19 受入基準 5）。
    ///
    /// **発生前の状態へ戻す**。退避が無い（例外中でない）なら拒む——
    /// 解決するものが無いのに「解決しました」と出すと、
    /// 追跡管理者は対応済みだと誤解する。
    ///
    pub def resolveException(a: TrackingActivity, resolvedAt: String,
                             resolutionNotes: Option[String],
                             revisedArrival: Option[String]):
            Result[TrackingError, TrackingActivity] =
        let TrackingActivity.TrackingActivity(x) = a;
        match x#statusBeforeException {
            case None    => Err(TrackingError.NoActiveException)
            case Some(before) =>
                if (not SharedCalendarText.isIsoDateTime(resolvedAt))
                    Err(TrackingError.OccurredAtIsMalformed)
                else
                    Ok(TrackingActivity.TrackingActivity({
                        transportStatus = before,
                        statusBeforeException = None,
                        pendingException = Some(ExceptionRecord.ExceptionRecord({
                            exceptionType = ExceptionType.Delay,
                            occurredAt = resolvedAt, location = "",
                            description = "", escalated = false,
                            resolvedAt = Some(resolvedAt),
                            resolutionNotes = resolutionNotes,
                            revisedArrival = revisedArrival
                        })) | x }))
        }

    ///
    /// 対応内容だけを載せる（US19 受入基準 4）。
    ///
    /// **状態は変えない**。到着予定の改定は「遅延が続いているが見込みが立った」
    /// ことであり、解決ではない。
    ///
    pub def withResolutionNotes(a: TrackingActivity, notes: Option[String],
                                revisedArrival: Option[String]): TrackingActivity =
        let TrackingActivity.TrackingActivity(x) = a;
        TrackingActivity.TrackingActivity({
            pendingException = Some(ExceptionRecord.ExceptionRecord({
                exceptionType = ExceptionType.Delay, occurredAt = "",
                location = "", description = "", escalated = false,
                // **解決日時は入れない**。未解決のまま対応内容だけを更新する
                resolvedAt = Some(""), resolutionNotes = notes,
                revisedArrival = revisedArrival
            })) | x })

    /// 例外の発生中か
    pub def hasActiveException(a: TrackingActivity): Bool =
        let TrackingActivity.TrackingActivity(x) = a;
        not Option.isEmpty(x#statusBeforeException)

    pub def statusBeforeExceptionOf(a: TrackingActivity): Option[TransportStatus] =
        let TrackingActivity.TrackingActivity(x) = a; x#statusBeforeException

    ///
    /// エスカレーションすべきか（US19 の追加ルール）。
    ///
    /// **閾値ちょうどは含めない**。48 時間「を超える」遅延が対象である
    /// （[テスト戦略](../../../../../../docs/design/test_strategy.md) 8.2 が
    /// 閾値ちょうど・未満・超過の 3 ケースを求めている）。
    ///
    /// 時刻は**引数で受け取る**。集約は現在時刻を取りに行かない。
    ///
    pub def shouldEscalate(occurredAt: String, now: String): Bool =
        match (SharedCalendarText.toEpochHours(occurredAt),
               SharedCalendarText.toEpochHours(now)) {
            // `from` / `to` は Flix の予約語のため使えない
            case (Some(since), Some(until)) =>
                until - since > escalationThresholdHours()
            case _ => false
        }
}
