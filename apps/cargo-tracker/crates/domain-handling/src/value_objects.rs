//! Handling Context の値オブジェクトと列挙型。

use crate::error::HandlingError;
use serde::{Deserialize, Serialize};
use shared_kernel::Location;

/// 荷役作業種別。通関（Customs）は IT6 スコープのため本 IT では扱わない。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum HandlingType {
    /// 受領。
    Receive,
    /// 積込。
    Load,
    /// 荷降し。
    Unload,
    /// 引取（配送完了）。
    Claim,
}

impl HandlingType {
    /// 永続化用の文字列表現（SCREAMING_SNAKE_CASE）。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Receive => "RECEIVE",
            Self::Load => "LOAD",
            Self::Unload => "UNLOAD",
            Self::Claim => "CLAIM",
        }
    }

    /// 文字列から復元する。未知の値は `None`。
    #[must_use]
    pub fn parse_type(value: &str) -> Option<Self> {
        match value {
            "RECEIVE" => Some(Self::Receive),
            "LOAD" => Some(Self::Load),
            "UNLOAD" => Some(Self::Unload),
            "CLAIM" => Some(Self::Claim),
            _ => None,
        }
    }

    /// 画面表示用の日本語ラベル。
    #[must_use]
    pub fn label(&self) -> &'static str {
        match self {
            Self::Receive => "受領",
            Self::Load => "積込",
            Self::Unload => "荷降し",
            Self::Claim => "引取",
        }
    }

    /// この荷役作業が結果としてもたらす輸送状態（追跡反映用・SCREAMING_SNAKE_CASE）。
    ///
    /// `domain-tracking` に依存しないため文字列で返し、app 層で `TrackingStatus` に変換する。
    #[must_use]
    pub fn resulting_transport_status(&self) -> &'static str {
        match self {
            Self::Receive => "RECEIVED",
            Self::Load => "LOADED",
            Self::Unload => "UNLOADED",
            Self::Claim => "CLAIMED",
        }
    }

    /// 引取（配送完了）作業か。
    #[must_use]
    pub fn is_claim(&self) -> bool {
        matches!(self, Self::Claim)
    }
}

/// 荷役作業の場所（共有カーネル `Location` を包む）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct HandlingLocation(Location);

impl HandlingLocation {
    /// UN/LOCODE 文字列から作業場所を生成する。
    ///
    /// # Errors
    ///
    /// UN/LOCODE 形式が不正な場合は `HandlingError::InvalidLocation` を返す。
    pub fn new(un_locode: &str) -> Result<Self, HandlingError> {
        Location::new(un_locode)
            .map(Self)
            .map_err(|_| HandlingError::InvalidLocation(un_locode.to_string()))
    }

    /// UN/LOCODE 文字列を返す。
    #[must_use]
    pub fn un_locode(&self) -> &str {
        self.0.code()
    }
}

/// 荷受人確認（署名または確認コード。US16 の引取記録に必須）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ReceiptConfirmation(String);

impl ReceiptConfirmation {
    /// 荷受人確認を生成する（空は不可）。
    ///
    /// # Errors
    ///
    /// 空文字列の場合は `HandlingError::EmptyReceiptConfirmation` を返す。
    pub fn new(value: impl Into<String>) -> Result<Self, HandlingError> {
        let value = value.into();
        if value.trim().is_empty() {
            return Err(HandlingError::EmptyReceiptConfirmation);
        }
        Ok(Self(value))
    }

    /// 文字列表現を返す。
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 荷役種別は輸送状態に対応する() {
        assert_eq!(
            HandlingType::Receive.resulting_transport_status(),
            "RECEIVED"
        );
        assert_eq!(HandlingType::Load.resulting_transport_status(), "LOADED");
        assert_eq!(
            HandlingType::Unload.resulting_transport_status(),
            "UNLOADED"
        );
        assert_eq!(HandlingType::Claim.resulting_transport_status(), "CLAIMED");
    }

    #[test]
    fn 荷役種別は文字列と相互変換できる() {
        assert_eq!(HandlingType::parse_type("CLAIM"), Some(HandlingType::Claim));
        assert_eq!(HandlingType::parse_type("??"), None);
        assert_eq!(HandlingType::Load.as_str(), "LOAD");
    }

    #[test]
    fn 作業場所は不正なunlocodeだとエラー() {
        assert!(HandlingLocation::new("bad").is_err());
        assert_eq!(HandlingLocation::new("JPTYO").unwrap().un_locode(), "JPTYO");
    }

    #[test]
    fn 荷受人確認は空だとエラー() {
        assert_eq!(
            ReceiptConfirmation::new("  "),
            Err(HandlingError::EmptyReceiptConfirmation)
        );
        assert_eq!(ReceiptConfirmation::new("署名").unwrap().as_str(), "署名");
    }
}
