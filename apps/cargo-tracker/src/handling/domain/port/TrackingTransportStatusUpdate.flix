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
    use HandlingModel.HandlingActivity
    use SharedTransportStatus.TransportStatus

    pub enum UpdateResult with Eq, ToString {
        case Updated
        /// 追跡番号に対応する追跡活動が無い
        case TrackingNotFound
    }

    pub eff TrackingTransportStatusUpdate {
        ///
        /// 荷役の記録を反映する。
        ///
        /// **状態は Handling が決めて渡す**（`resultingStatusFor`）。荷役の種別と
        /// 輸送状態の対応は荷役の業務知識であり、Tracking の関心ではない。
        ///
        /// **`None` は「状態を変えない」である**（IT11・ADR-0014 決定 2）。
        /// 通関は出来事として残るが輸送の段階を進めない。
        /// 現在の状態を書き戻す形にすると、書き戻しの競合を隠す。
        ///
        /// **集約を引数に取る**（IT10 ふりかえり Try T4）。`(番号, 状態)` の
        /// 2 値を受ける形は、**ポートの型そのものが集約の迂回を合法にする**。
        /// あわせて、荷主のタイムラインに出す「いつ・どこで」を運ぶ経路が
        /// この形でしか通らない——IT10 は 2 値だったため出来事が落ちていた（H8）。
        ///
        def apply(activity: HandlingActivity, status: Option[TransportStatus]):
                UpdateResult
    }
}
