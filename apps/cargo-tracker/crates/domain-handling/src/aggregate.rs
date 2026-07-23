//! Handling Context の集約ルート `HandlingActivity`。

use crate::error::HandlingError;
use crate::value_objects::{HandlingLocation, HandlingType, ReceiptConfirmation};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

/// 荷役作業記録の集約ルート。
///
/// 予約参照は文字列 ID で保持し（BC 独立）、引取（Claim）は荷受人確認を必須とする
/// 不変条件をドメインに閉じ込める（US16・UI ガードに依存しない）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct HandlingActivity {
    booking_id: String,
    handling_type: HandlingType,
    completion_time: DateTime<Utc>,
    location: HandlingLocation,
    voyage_number: Option<String>,
    operator_name: Option<String>,
    receipt_confirmation: Option<ReceiptConfirmation>,
}

impl HandlingActivity {
    /// 荷役作業を記録する（US15/US16）。
    ///
    /// 引取（Claim）の場合は荷受人確認 `receipt_confirmation` が必須。受領・積込・荷降しでは不要。
    ///
    /// # Errors
    ///
    /// - 予約 ID が空: `EmptyBookingId`
    /// - 引取なのに荷受人確認が無い: `ReceiptConfirmationRequired`
    pub fn register(
        booking_id: impl Into<String>,
        handling_type: HandlingType,
        completion_time: DateTime<Utc>,
        location: HandlingLocation,
        voyage_number: Option<String>,
        operator_name: Option<String>,
        receipt_confirmation: Option<ReceiptConfirmation>,
    ) -> Result<Self, HandlingError> {
        let booking_id = booking_id.into();
        if booking_id.trim().is_empty() {
            return Err(HandlingError::EmptyBookingId);
        }
        if handling_type.is_claim() && receipt_confirmation.is_none() {
            return Err(HandlingError::ReceiptConfirmationRequired);
        }
        Ok(Self {
            booking_id,
            handling_type,
            completion_time,
            location,
            voyage_number: voyage_number.filter(|v| !v.trim().is_empty()),
            operator_name: operator_name.filter(|v| !v.trim().is_empty()),
            // 引取以外で誤って渡された荷受人確認は保持しない。
            receipt_confirmation: if handling_type.is_claim() {
                receipt_confirmation
            } else {
                None
            },
        })
    }

    /// 予約 ID。
    #[must_use]
    pub fn booking_id(&self) -> &str {
        &self.booking_id
    }

    /// 荷役種別。
    #[must_use]
    pub fn handling_type(&self) -> HandlingType {
        self.handling_type
    }

    /// 作業完了日時。
    #[must_use]
    pub fn completion_time(&self) -> DateTime<Utc> {
        self.completion_time
    }

    /// 作業場所。
    #[must_use]
    pub fn location(&self) -> &HandlingLocation {
        &self.location
    }

    /// 航海番号（任意）。
    #[must_use]
    pub fn voyage_number(&self) -> Option<&str> {
        self.voyage_number.as_deref()
    }

    /// 作業者名（任意）。
    #[must_use]
    pub fn operator_name(&self) -> Option<&str> {
        self.operator_name.as_deref()
    }

    /// 荷受人確認（引取時のみ存在）。
    #[must_use]
    pub fn receipt_confirmation(&self) -> Option<&ReceiptConfirmation> {
        self.receipt_confirmation.as_ref()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn loc() -> HandlingLocation {
        HandlingLocation::new("JPTYO").unwrap()
    }

    fn now() -> DateTime<Utc> {
        DateTime::from_timestamp(1_700_000_000, 0).unwrap()
    }

    #[test]
    fn 受領作業を記録できる() {
        let activity = HandlingActivity::register(
            "BKG-1",
            HandlingType::Receive,
            now(),
            loc(),
            None,
            Some("作業員A".to_string()),
            None,
        )
        .expect("記録できるはず");
        assert_eq!(activity.booking_id(), "BKG-1");
        assert_eq!(activity.handling_type(), HandlingType::Receive);
        assert_eq!(activity.operator_name(), Some("作業員A"));
        assert!(activity.receipt_confirmation().is_none());
    }

    #[test]
    fn 予約idが空だと記録できない() {
        let result =
            HandlingActivity::register("  ", HandlingType::Load, now(), loc(), None, None, None);
        assert_eq!(result, Err(HandlingError::EmptyBookingId));
    }

    #[test]
    fn 引取は荷受人確認がないと記録できない() {
        let result = HandlingActivity::register(
            "BKG-1",
            HandlingType::Claim,
            now(),
            loc(),
            None,
            None,
            None,
        );
        assert_eq!(result, Err(HandlingError::ReceiptConfirmationRequired));
    }

    #[test]
    fn 引取は荷受人確認があれば記録できる() {
        let activity = HandlingActivity::register(
            "BKG-1",
            HandlingType::Claim,
            now(),
            loc(),
            None,
            None,
            Some(ReceiptConfirmation::new("署名: 荷受 太郎").unwrap()),
        )
        .expect("記録できるはず");
        assert!(activity.handling_type().is_claim());
        assert_eq!(
            activity
                .receipt_confirmation()
                .map(ReceiptConfirmation::as_str),
            Some("署名: 荷受 太郎")
        );
    }

    #[test]
    fn 引取以外で渡された荷受人確認は保持されない() {
        let activity = HandlingActivity::register(
            "BKG-1",
            HandlingType::Load,
            now(),
            loc(),
            None,
            None,
            Some(ReceiptConfirmation::new("誤入力").unwrap()),
        )
        .unwrap();
        assert!(activity.receipt_confirmation().is_none());
    }
}
