//! Tracking Context の集約ルート `TrackingActivity`。

use crate::value_objects::{
    ExceptionType, TrackingBookingId, TrackingLocation, TrackingNumber, TrackingStatus,
    TrackingVoyageNumber,
};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

/// 追跡例外イベント（遅延・損傷等。US19 は遅延のみ）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TrackingExceptionEvent {
    exception_type: ExceptionType,
    location: TrackingLocation,
    occurred_at: DateTime<Utc>,
    description: Option<String>,
    escalation_flag: bool,
    resolved_at: Option<DateTime<Utc>>,
    resolution_notes: Option<String>,
}

impl TrackingExceptionEvent {
    /// 未解決の例外イベントを生成する（US19 登録）。
    #[must_use]
    pub fn new(
        exception_type: ExceptionType,
        location: TrackingLocation,
        occurred_at: DateTime<Utc>,
        description: Option<String>,
    ) -> Self {
        Self {
            exception_type,
            location,
            occurred_at,
            description,
            escalation_flag: false,
            resolved_at: None,
            resolution_notes: None,
        }
    }

    /// 永続化済みデータから再構築する。
    #[must_use]
    pub fn reconstruct(
        exception_type: ExceptionType,
        location: TrackingLocation,
        occurred_at: DateTime<Utc>,
        description: Option<String>,
        escalation_flag: bool,
        resolved_at: Option<DateTime<Utc>>,
        resolution_notes: Option<String>,
    ) -> Self {
        Self {
            exception_type,
            location,
            occurred_at,
            description,
            escalation_flag,
            resolved_at,
            resolution_notes,
        }
    }

    /// 例外種別。
    #[must_use]
    pub fn exception_type(&self) -> ExceptionType {
        self.exception_type
    }

    /// 発生位置。
    #[must_use]
    pub fn location(&self) -> &TrackingLocation {
        &self.location
    }

    /// 発生日時。
    #[must_use]
    pub fn occurred_at(&self) -> DateTime<Utc> {
        self.occurred_at
    }

    /// 内容。
    #[must_use]
    pub fn description(&self) -> Option<&str> {
        self.description.as_deref()
    }

    /// エスカレーションフラグ。
    #[must_use]
    pub fn escalation_flag(&self) -> bool {
        self.escalation_flag
    }

    /// 解決日時（未解決なら `None`）。
    #[must_use]
    pub fn resolved_at(&self) -> Option<DateTime<Utc>> {
        self.resolved_at
    }

    /// 対応内容（対応報告・US19）。
    #[must_use]
    pub fn resolution_notes(&self) -> Option<&str> {
        self.resolution_notes.as_deref()
    }

    /// 未解決か。
    #[must_use]
    pub fn is_active(&self) -> bool {
        self.resolved_at.is_none()
    }

    /// 例外を解決する（対応日時・対応内容を記録）。
    fn resolve(&mut self, resolved_at: DateTime<Utc>, resolution_notes: String) {
        self.resolved_at = Some(resolved_at);
        self.resolution_notes = Some(resolution_notes);
    }
}

/// 追跡イベント（時系列で記録される追跡の出来事）。
///
/// 荷役記録（US15/US16）・手動更新（US17）のいずれも、結果としての輸送状態 `status` を
/// 保持する。`TrackingActivity::current_status` はイベント列の末尾から純粋に導出する。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TrackingActivityEvent {
    status: TrackingStatus,
    location: TrackingLocation,
    event_time: DateTime<Utc>,
    voyage_number: Option<TrackingVoyageNumber>,
}

impl TrackingActivityEvent {
    /// 追跡イベントを生成する。
    #[must_use]
    pub fn new(
        status: TrackingStatus,
        location: TrackingLocation,
        event_time: DateTime<Utc>,
        voyage_number: Option<TrackingVoyageNumber>,
    ) -> Self {
        Self {
            status,
            location,
            event_time,
            voyage_number,
        }
    }

    /// 結果としての輸送状態。
    #[must_use]
    pub fn status(&self) -> TrackingStatus {
        self.status
    }

    /// 発生位置。
    #[must_use]
    pub fn location(&self) -> &TrackingLocation {
        &self.location
    }

    /// 発生日時。
    #[must_use]
    pub fn event_time(&self) -> DateTime<Utc> {
        self.event_time
    }

    /// 航海番号（任意）。
    #[must_use]
    pub fn voyage_number(&self) -> Option<&TrackingVoyageNumber> {
        self.voyage_number.as_ref()
    }
}

/// 追跡活動集約ルート。追跡番号・予約参照・イベント列を保持する。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TrackingActivity {
    tracking_number: TrackingNumber,
    booking_id: TrackingBookingId,
    events: Vec<TrackingActivityEvent>,
    exceptions: Vec<TrackingExceptionEvent>,
}

impl TrackingActivity {
    /// 追跡番号を発行して追跡活動を新規生成する（US14）。
    ///
    /// 初期状態は「受領待ち（NotReceived）」で、イベントは未記録。
    #[must_use]
    pub fn issue(tracking_number: TrackingNumber, booking_id: TrackingBookingId) -> Self {
        Self {
            tracking_number,
            booking_id,
            events: Vec::new(),
            exceptions: Vec::new(),
        }
    }

    /// 永続化済みデータから再構築する。
    #[must_use]
    pub fn reconstruct(
        tracking_number: TrackingNumber,
        booking_id: TrackingBookingId,
        events: Vec<TrackingActivityEvent>,
    ) -> Self {
        Self {
            tracking_number,
            booking_id,
            events,
            exceptions: Vec::new(),
        }
    }

    /// 例外イベントも含めて永続化済みデータから再構築する（US19）。
    #[must_use]
    pub fn reconstruct_with_exceptions(
        tracking_number: TrackingNumber,
        booking_id: TrackingBookingId,
        events: Vec<TrackingActivityEvent>,
        exceptions: Vec<TrackingExceptionEvent>,
    ) -> Self {
        Self {
            tracking_number,
            booking_id,
            events,
            exceptions,
        }
    }

    /// 追跡番号。
    #[must_use]
    pub fn tracking_number(&self) -> &TrackingNumber {
        &self.tracking_number
    }

    /// 参照している予約 ID。
    #[must_use]
    pub fn booking_id(&self) -> &TrackingBookingId {
        &self.booking_id
    }

    /// 記録済みイベント列（時系列）。
    #[must_use]
    pub fn events(&self) -> &[TrackingActivityEvent] {
        &self.events
    }

    /// 追跡イベントを追記する（荷役反映 US15/US16・手動更新 US17 共通）。
    pub fn record_event(&mut self, event: TrackingActivityEvent) {
        self.events.push(event);
    }

    /// 記録済み例外イベント列。
    #[must_use]
    pub fn exceptions(&self) -> &[TrackingExceptionEvent] {
        &self.exceptions
    }

    /// 例外イベントを追記する（US19 遅延例外登録）。
    pub fn add_exception(&mut self, exception: TrackingExceptionEvent) {
        self.exceptions.push(exception);
    }

    /// 未解決の例外があるか（US19）。
    #[must_use]
    pub fn has_active_exception(&self) -> bool {
        self.exceptions
            .iter()
            .any(TrackingExceptionEvent::is_active)
    }

    /// 指定インデックスの例外を解決し、対応内容を記録する（US19 対応報告）。
    ///
    /// # Errors
    ///
    /// インデックスが範囲外の場合は `false` を返す（対象なし）。
    pub fn resolve_exception(
        &mut self,
        index: usize,
        resolved_at: DateTime<Utc>,
        resolution_notes: impl Into<String>,
    ) -> bool {
        let Some(ex) = self.exceptions.get_mut(index) else {
            return false;
        };
        ex.resolve(resolved_at, resolution_notes.into());
        true
    }

    /// 現在の輸送状態をイベント列・例外から純粋に導出する（ADR-0006 拡張）。
    ///
    /// 未解決の例外があれば `Exception`。無ければイベント末尾の状態（イベントが無ければ受領待ち）。
    #[must_use]
    pub fn current_status(&self) -> TrackingStatus {
        if self.has_active_exception() {
            return TrackingStatus::Exception;
        }
        self.events
            .last()
            .map_or(TrackingStatus::NotReceived, TrackingActivityEvent::status)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tn() -> TrackingNumber {
        TrackingNumber::parse("TRK-TEST-1").unwrap()
    }

    fn bid() -> TrackingBookingId {
        TrackingBookingId::parse("BKG-1").unwrap()
    }

    fn loc() -> TrackingLocation {
        TrackingLocation::new("JPTYO").unwrap()
    }

    fn now() -> DateTime<Utc> {
        DateTime::from_timestamp(1_700_000_000, 0).unwrap()
    }

    #[test]
    fn 追跡番号発行で受領待ち状態の追跡が生成される() {
        let activity = TrackingActivity::issue(tn(), bid());
        assert_eq!(activity.current_status(), TrackingStatus::NotReceived);
        assert!(activity.events().is_empty());
        assert_eq!(activity.tracking_number().as_str(), "TRK-TEST-1");
        assert_eq!(activity.booking_id().as_str(), "BKG-1");
    }

    #[test]
    fn 荷役イベント追記で現在状態が更新される() {
        let mut activity = TrackingActivity::issue(tn(), bid());
        activity.record_event(TrackingActivityEvent::new(
            TrackingStatus::Received,
            loc(),
            now(),
            None,
        ));
        assert_eq!(activity.current_status(), TrackingStatus::Received);

        activity.record_event(TrackingActivityEvent::new(
            TrackingStatus::Loaded,
            loc(),
            now(),
            TrackingVoyageNumber::new("V001"),
        ));
        assert_eq!(activity.current_status(), TrackingStatus::Loaded);
        assert_eq!(activity.events().len(), 2);
    }

    #[test]
    fn 引取イベントで配送完了状態になる() {
        let mut activity = TrackingActivity::issue(tn(), bid());
        activity.record_event(TrackingActivityEvent::new(
            TrackingStatus::Claimed,
            loc(),
            now(),
            None,
        ));
        assert!(activity.current_status().is_delivered());
    }

    #[test]
    fn 遅延例外を追加すると現在状態が例外発生になる() {
        use crate::value_objects::ExceptionType;
        let mut activity = TrackingActivity::issue(tn(), bid());
        activity.record_event(TrackingActivityEvent::new(
            TrackingStatus::Loaded,
            loc(),
            now(),
            None,
        ));
        activity.add_exception(TrackingExceptionEvent::new(
            ExceptionType::Delay,
            loc(),
            now(),
            Some("台風による遅延".to_string()),
        ));
        assert!(activity.has_active_exception());
        assert_eq!(activity.current_status(), TrackingStatus::Exception);
    }

    #[test]
    fn 例外を解決すると現在状態が例外前に復帰し対応内容が記録される() {
        use crate::value_objects::ExceptionType;
        let mut activity = TrackingActivity::issue(tn(), bid());
        activity.record_event(TrackingActivityEvent::new(
            TrackingStatus::Loaded,
            loc(),
            now(),
            None,
        ));
        activity.add_exception(TrackingExceptionEvent::new(
            ExceptionType::Delay,
            loc(),
            now(),
            None,
        ));
        assert!(activity.resolve_exception(0, now(), "代替便を手配"));
        assert!(!activity.has_active_exception());
        // 例外解決後はイベント末尾（Loaded）に復帰する。
        assert_eq!(activity.current_status(), TrackingStatus::Loaded);
        assert_eq!(
            activity.exceptions()[0].resolution_notes(),
            Some("代替便を手配")
        );
    }

    #[test]
    fn 範囲外インデックスの例外解決は失敗する() {
        let mut activity = TrackingActivity::issue(tn(), bid());
        assert!(!activity.resolve_exception(0, now(), "x"));
    }
}
