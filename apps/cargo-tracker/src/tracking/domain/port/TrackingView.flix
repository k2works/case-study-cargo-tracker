///
/// Tracking Context の Read Model（CQRS のクエリ側の戻り値型）。
///
/// 集約ではなく、画面表示に最適化したフラットな構造を持つ。
/// 画面関数はこの型のみを受け取り、集約を直接扱わない
/// （docs/design/architecture_frontend.md の画面関数の設計原則）。
///
mod TrackingView {

    /// 追跡イベント 1 件（画面表示用）
    pub enum TrackingEventView({
        eventTime = String,
        eventType = String,
        locationName = String,
        locationUnlocode = String
    })

    /// 追跡詳細（画面表示用）
    pub enum TrackingDetailView({
        trackingNumber = String,
        transportStatus = String,
        estimatedArrival = String,
        events = List[TrackingEventView]
    })

    /// 追跡番号
    pub def trackingNumber(v: TrackingDetailView): String =
        let TrackingDetailView.TrackingDetailView(d) = v;
        d#trackingNumber

    /// 輸送状態（TransportStatus の永続化値）
    pub def transportStatus(v: TrackingDetailView): String =
        let TrackingDetailView.TrackingDetailView(d) = v;
        d#transportStatus

    ///
    /// 推定到着日。
    ///
    /// 経路が未確定の貨物では到着予定が定まらないため、未設定を空文字で表す
    /// （表示上の「未定」への変換は画面関数が担う）。
    ///
    pub def estimatedArrival(v: TrackingDetailView): String =
        let TrackingDetailView.TrackingDetailView(d) = v;
        d#estimatedArrival

    /// イベント履歴（新しい順）
    pub def events(v: TrackingDetailView): List[TrackingEventView] =
        let TrackingDetailView.TrackingDetailView(d) = v;
        d#events

    /// イベントの発生日時
    pub def eventTime(e: TrackingEventView): String =
        let TrackingEventView.TrackingEventView(x) = e;
        x#eventTime

    /// イベントの種別
    pub def eventType(e: TrackingEventView): String =
        let TrackingEventView.TrackingEventView(x) = e;
        x#eventType

    /// イベントの発生場所名
    pub def locationName(e: TrackingEventView): String =
        let TrackingEventView.TrackingEventView(x) = e;
        x#locationName

    /// イベントの発生場所の UN/LOCODE
    pub def locationUnlocode(e: TrackingEventView): String =
        let TrackingEventView.TrackingEventView(x) = e;
        x#locationUnlocode

    ///
    /// 詳細ビューを組み立てる。
    ///
    /// 場所は最新イベントから画面側で導出するため、ビューには持たせない。
    /// 同じ情報を Read Model と画面の二箇所で組み立てると、片方だけ直したときに
    /// 表示が食い違う（IT2 レビュー指摘）。
    ///
    pub def detail(num: String, st: String, eta: String,
                   evs: List[TrackingEventView]): TrackingDetailView =
        TrackingDetailView.TrackingDetailView({
            trackingNumber = num, transportStatus = st,
            estimatedArrival = eta, events = evs
        })

    /// イベントビューを組み立てる
    pub def event(time: String, kind: String, loc: String, unlocode: String): TrackingEventView =
        TrackingEventView.TrackingEventView({
            eventTime = time, eventType = kind,
            locationName = loc, locationUnlocode = unlocode
        })

    ///
    /// 未解決の例外の要約（US19・IT12）。
    ///
    /// **荷主に見せるのは「何が起きたか」と「いつ着くか」だけ**である。
    /// エスカレーションの有無は社内の運用であり、荷主の関心ではない。
    ///
    pub enum TrackingExceptionView({
        exceptionType = String,
        occurredAt = String,
        description = String,
        resolutionNotes = String,
        // **解決済みか**（IT12 クローズ時レビュー）。載せていなかったため、
        // 解決しても荷主の画面に「発生しています」が残り続けていた
        resolved = Bool,
        // 要エスカレーションか。**保存するだけで誰も読まない列を作らない**
        escalated = Bool
    })

    pub def exceptionTypeOf(e: TrackingExceptionView): String =
        let TrackingExceptionView.TrackingExceptionView(x) = e; x#exceptionType
    pub def exceptionOccurredAt(e: TrackingExceptionView): String =
        let TrackingExceptionView.TrackingExceptionView(x) = e; x#occurredAt
    pub def exceptionDescription(e: TrackingExceptionView): String =
        let TrackingExceptionView.TrackingExceptionView(x) = e; x#description
    pub def exceptionResolutionNotes(e: TrackingExceptionView): String =
        let TrackingExceptionView.TrackingExceptionView(x) = e; x#resolutionNotes
    pub def exceptionIsResolved(e: TrackingExceptionView): Bool =
        let TrackingExceptionView.TrackingExceptionView(x) = e; x#resolved
    pub def exceptionIsEscalated(e: TrackingExceptionView): Bool =
        let TrackingExceptionView.TrackingExceptionView(x) = e; x#escalated

    pub def exceptionView(exceptionType: String, occurredAt: String,
                          description: String, resolutionNotes: String,
                          resolved: Bool, escalated: Bool): TrackingExceptionView =
        TrackingExceptionView.TrackingExceptionView({
            exceptionType = exceptionType, occurredAt = occurredAt,
            description = description, resolutionNotes = resolutionNotes,
            resolved = resolved, escalated = escalated
        })
}
