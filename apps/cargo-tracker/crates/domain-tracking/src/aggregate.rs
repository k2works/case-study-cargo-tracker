//! Tracking Context の集約ルート `TrackingActivity`。

use crate::value_objects::{
    TrackingBookingId, TrackingLocation, TrackingNumber, TrackingStatus, TrackingVoyageNumber,
};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

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

    /// 現在の輸送状態をイベント列から純粋に導出する。
    ///
    /// イベントが無ければ「受領待ち」。末尾イベントの状態を採用する
    /// （例外イベントは IT6 で導入。本 IT ではイベント末尾がそのまま現在状態）。
    #[must_use]
    pub fn current_status(&self) -> TrackingStatus {
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
}
