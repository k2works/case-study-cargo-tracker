///
/// 輸送状態の反映（ACL ポート。US15・IT10）。
///
/// **Handling の語彙だけで定義する**。`TrackingActivity` は現れない。
///
/// 実装は**合成ルートの `acl/`** が与え、Tracking のアプリケーションサービスを
/// 同期的に呼んで**相手の集約を通す**（ADR-0012）。
/// `tracking_activity` テーブルへ直接書く形は採らない——
/// 「NOT_RECEIVED からいきなり CLAIMED へ飛べない」ことを保証できなくなる。
///
/// # 同期である理由
///
/// 設計ドキュメントは当初 `HandlingActivityRegisteredEvent` で反映する形を描き、
/// UI 設計は「最大 30 秒の遅延」まで書いていた。ADR-0012 でこれを未採用とした——
/// イベント基盤が実装に存在せず、**利用者に「30 秒待つ」を覚えさせる理由が無い**。
///
mod HandlingTrackingTransportStatusUpdate {
    use SharedTransportStatus.TransportStatus

    pub enum UpdateResult with Eq, ToString {
        case Updated
        /// 追跡番号に対応する追跡活動が無い
        case TrackingNotFound
    }

    pub eff TrackingTransportStatusUpdate {
        ///
        /// 輸送状態を反映する。
        ///
        /// **状態は Handling が決めて渡す**（`resultingStatusOf`）。荷役の種別と
        /// 輸送状態の対応は荷役の業務知識であり、Tracking の関心ではない。
        ///
        def apply(trackingNumber: String, status: TransportStatus): UpdateResult
    }
}
