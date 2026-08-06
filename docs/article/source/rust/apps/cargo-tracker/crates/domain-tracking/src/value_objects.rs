//! Tracking Context の値オブジェクトと列挙型。

use crate::error::TrackingError;
use serde::{Deserialize, Serialize};
use shared_kernel::Location;

/// 追跡番号（追跡活動を一意に識別する）。
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct TrackingNumber(String);

impl TrackingNumber {
    /// 追跡番号を生成する（`TRK-` プレフィックス + UUID）。
    #[must_use]
    pub fn generate() -> Self {
        Self(format!("TRK-{}", uuid::Uuid::new_v4().simple()))
    }

    /// 文字列から追跡番号を復元・検証する。
    ///
    /// # Errors
    ///
    /// 空文字列の場合は `TrackingError::EmptyTrackingNumber` を返す。
    pub fn parse(value: impl Into<String>) -> Result<Self, TrackingError> {
        let value = value.into();
        if value.trim().is_empty() {
            return Err(TrackingError::EmptyTrackingNumber);
        }
        Ok(Self(value))
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 追跡が参照する予約 ID（Booking Context への参照。文字列 ID で BC 独立を保つ）。
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct TrackingBookingId(String);

impl TrackingBookingId {
    /// 予約 ID を検証して生成する。
    ///
    /// # Errors
    ///
    /// 空文字列の場合は `TrackingError::EmptyBookingId` を返す。
    pub fn parse(value: impl Into<String>) -> Result<Self, TrackingError> {
        let value = value.into();
        if value.trim().is_empty() {
            return Err(TrackingError::EmptyBookingId);
        }
        Ok(Self(value))
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 追跡イベントの発生位置（共有カーネル `Location` を包む）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TrackingLocation(Location);

impl TrackingLocation {
    /// UN/LOCODE 文字列から追跡位置を生成する。
    ///
    /// # Errors
    ///
    /// UN/LOCODE 形式が不正な場合は `TrackingError::InvalidLocation` を返す。
    pub fn new(un_locode: &str) -> Result<Self, TrackingError> {
        Location::new(un_locode)
            .map(Self)
            .map_err(|_| TrackingError::InvalidLocation(un_locode.to_string()))
    }

    /// UN/LOCODE 文字列を返す。
    #[must_use]
    pub fn un_locode(&self) -> &str {
        self.0.code()
    }
}

/// 追跡が参照する航海番号（Routing Context 固有型を共有せず文字列で保持）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TrackingVoyageNumber(String);

impl TrackingVoyageNumber {
    /// 航海番号を生成する。空文字列（トリム後）の場合は `None` を返す。
    #[must_use]
    pub fn new(value: impl Into<String>) -> Option<Self> {
        let value = value.into();
        if value.trim().is_empty() {
            None
        } else {
            Some(Self(value))
        }
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

/// 貨物の輸送状態（追跡状態・9 値）。`shared_kernel` の TransportStatus に対応する。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TrackingStatus {
    /// 受領待ち（追跡番号発行直後の初期状態）。
    NotReceived,
    /// 受領済。
    Received,
    /// 積込済。
    Loaded,
    /// 搭載中（出港済）。
    OnboardCarrier,
    /// 荷降し済。
    Unloaded,
    /// 引取待ち（入港済）。
    AwaitingClaim,
    /// 引取済（配送完了）。
    Claimed,
    /// 例外発生中。
    Exception,
    /// 不明。
    Unknown,
}

impl TrackingStatus {
    /// 永続化用の文字列表現（SCREAMING_SNAKE_CASE）。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::NotReceived => "NOT_RECEIVED",
            Self::Received => "RECEIVED",
            Self::Loaded => "LOADED",
            Self::OnboardCarrier => "ONBOARD_CARRIER",
            Self::Unloaded => "UNLOADED",
            Self::AwaitingClaim => "AWAITING_CLAIM",
            Self::Claimed => "CLAIMED",
            Self::Exception => "EXCEPTION",
            Self::Unknown => "UNKNOWN",
        }
    }

    /// 文字列から復元する。未知の値は `Unknown` にフォールバックする。
    #[must_use]
    pub fn from_str_or_unknown(value: &str) -> Self {
        match value {
            "NOT_RECEIVED" => Self::NotReceived,
            "RECEIVED" => Self::Received,
            "LOADED" => Self::Loaded,
            "ONBOARD_CARRIER" => Self::OnboardCarrier,
            "UNLOADED" => Self::Unloaded,
            "AWAITING_CLAIM" => Self::AwaitingClaim,
            "CLAIMED" => Self::Claimed,
            "EXCEPTION" => Self::Exception,
            _ => Self::Unknown,
        }
    }

    /// 画面表示用の日本語ラベル（ui_design.md のステータス正典に準拠）。
    #[must_use]
    pub fn label(&self) -> &'static str {
        match self {
            Self::NotReceived => "受領待ち",
            Self::Received => "受領済",
            Self::Loaded => "積込済",
            Self::OnboardCarrier => "搭載中",
            Self::Unloaded => "荷降し済",
            Self::AwaitingClaim => "引取待ち",
            Self::Claimed => "引取済",
            Self::Exception => "例外発生",
            Self::Unknown => "不明",
        }
    }

    /// 配送完了（引取済）か。精算処理の開始条件（US16）。
    #[must_use]
    pub fn is_delivered(&self) -> bool {
        matches!(self, Self::Claimed)
    }
}

/// 追跡例外の種別。IT6 は `Delay`（遅延）のみ。IT7 で `Damage`（破損）/`Lost`（紛失）を導入。
/// `CustomsHold`（通関保留）は範囲外（将来）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ExceptionType {
    /// 遅延（US19）。
    Delay,
    /// 破損（US20）。
    Damage,
    /// 紛失（US20）。緊急フラグ・管理職 escalation の対象。
    Lost,
}

impl ExceptionType {
    /// 永続化用の文字列表現。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Delay => "DELAY",
            Self::Damage => "DAMAGE",
            Self::Lost => "LOST",
        }
    }

    /// 文字列から復元する。未知の値は `None`。
    #[must_use]
    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "DELAY" => Some(Self::Delay),
            "DAMAGE" => Some(Self::Damage),
            "LOST" => Some(Self::Lost),
            _ => None,
        }
    }

    /// 画面表示用の日本語ラベル。
    #[must_use]
    pub fn label(&self) -> &'static str {
        match self {
            Self::Delay => "遅延",
            Self::Damage => "破損",
            Self::Lost => "紛失",
        }
    }

    /// 管理職への escalation を要する種別か（US20・紛失のみ緊急扱い）。
    #[must_use]
    pub fn requires_escalation(&self) -> bool {
        matches!(self, Self::Lost)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 追跡番号は_trk_プレフィックスで生成される() {
        let number = TrackingNumber::generate();
        assert!(number.as_str().starts_with("TRK-"));
    }

    #[test]
    fn 追跡番号は空だとエラー() {
        assert_eq!(
            TrackingNumber::parse("  "),
            Err(TrackingError::EmptyTrackingNumber)
        );
        assert!(TrackingNumber::parse("TRK-1").is_ok());
    }

    #[test]
    fn 予約idは空だとエラー() {
        assert_eq!(
            TrackingBookingId::parse(""),
            Err(TrackingError::EmptyBookingId)
        );
        assert!(TrackingBookingId::parse("BKG-1").is_ok());
    }

    #[test]
    fn 例外種別は破損紛失を含めて永続化文字列と往復できる() {
        for ty in [
            ExceptionType::Delay,
            ExceptionType::Damage,
            ExceptionType::Lost,
        ] {
            assert_eq!(ExceptionType::parse(ty.as_str()), Some(ty));
        }
        assert_eq!(ExceptionType::parse("DAMAGE"), Some(ExceptionType::Damage));
        assert_eq!(ExceptionType::parse("LOST"), Some(ExceptionType::Lost));
        assert_eq!(ExceptionType::parse("UNKNOWN"), None);
    }

    #[test]
    fn 紛失のみエスカレーション対象となる() {
        assert!(ExceptionType::Lost.requires_escalation());
        assert!(!ExceptionType::Damage.requires_escalation());
        assert!(!ExceptionType::Delay.requires_escalation());
    }

    #[test]
    fn 追跡位置は不正なunlocodeだとエラー() {
        assert!(TrackingLocation::new("bad").is_err());
        assert_eq!(TrackingLocation::new("USNYC").unwrap().un_locode(), "USNYC");
    }

    #[test]
    fn 状態は文字列と相互変換できる() {
        assert_eq!(TrackingStatus::NotReceived.as_str(), "NOT_RECEIVED");
        assert_eq!(
            TrackingStatus::from_str_or_unknown("CLAIMED"),
            TrackingStatus::Claimed
        );
        assert_eq!(
            TrackingStatus::from_str_or_unknown("??"),
            TrackingStatus::Unknown
        );
    }

    #[test]
    fn 引取済は配送完了を表す() {
        assert!(TrackingStatus::Claimed.is_delivered());
        assert!(!TrackingStatus::Unloaded.is_delivered());
    }
}
