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
        transportStatus = TransportStatus
    })

    pub enum TrackingError with Eq, ToString {
        case TrackingNumberIsRequired
        case TrackingNumberIsMalformed
        case BookingIdIsRequired
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
                transportStatus = SharedTransportStatus.TransportStatus.NotReceived
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
    pub def applyHandling(a: TrackingActivity, status: TransportStatus): TrackingActivity =
        let TrackingActivity.TrackingActivity(x) = a;
        TrackingActivity.TrackingActivity({ transportStatus = status | x })

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
                transportStatus = s
            }))
        }
}
