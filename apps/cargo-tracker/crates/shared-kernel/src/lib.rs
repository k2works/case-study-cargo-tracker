//! 共有カーネル: Bounded Context 間で共有する値オブジェクト・trait を提供する。

use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// UN/LOCODE（5 文字の英大文字）で表す場所の識別子。
#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct Location(String);

/// 共有カーネルのエラー型。
#[derive(Debug, thiserror::Error)]
pub enum SharedKernelError {
    /// UN/LOCODE の形式が不正な場合。
    #[error("invalid UN/LOCODE: {0}")]
    InvalidUnLocode(String),
    /// 荷主 ID（UUID）の形式が不正な場合。
    #[error("invalid shipper id: {0}")]
    InvalidShipperId(String),
    /// 未知のロール名を受け取った場合。
    #[error("unknown role: {0}")]
    UnknownRole(String),
}

/// 荷主識別子（UUID ベース）。Booking / Shipper Context 間で共有する。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct ShipperId(Uuid);

impl ShipperId {
    /// 新しい荷主 ID を採番する。
    #[must_use]
    pub fn generate() -> Self {
        Self(Uuid::new_v4())
    }

    /// 既存の UUID から `ShipperId` を構成する。
    #[must_use]
    pub fn from_uuid(id: Uuid) -> Self {
        Self(id)
    }

    /// UUID 文字列から `ShipperId` をパースする。
    ///
    /// # Errors
    ///
    /// UUID として解釈できない場合は `SharedKernelError::InvalidShipperId` を返す。
    pub fn parse(value: &str) -> Result<Self, SharedKernelError> {
        Uuid::parse_str(value)
            .map(Self)
            .map_err(|_| SharedKernelError::InvalidShipperId(value.to_string()))
    }

    /// 内部の UUID を返す。
    #[must_use]
    pub fn value(&self) -> Uuid {
        self.0
    }
}

impl std::fmt::Display for ShipperId {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

/// システム利用者のロール。axum-login の RBAC・ナビゲーション出し分けに使用する。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum Role {
    /// 営業担当者。
    Sales,
    /// 荷主。
    Shipper,
    /// 荷受人。
    Consignee,
    /// 追跡管理者。
    Tracker,
    /// 荷役作業員。
    Handler,
    /// 経路設計者。
    RouteDesigner,
    /// 経理担当者。
    Billing,
    /// システム管理者。
    Admin,
}

impl Role {
    /// `ROLE_` プレフィックス付きの文字列表現を返す（DB / テンプレート表示に使用）。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Sales => "ROLE_SALES",
            Self::Shipper => "ROLE_SHIPPER",
            Self::Consignee => "ROLE_CONSIGNEE",
            Self::Tracker => "ROLE_TRACKER",
            Self::Handler => "ROLE_HANDLER",
            Self::RouteDesigner => "ROLE_ROUTE_DESIGNER",
            Self::Billing => "ROLE_BILLING",
            Self::Admin => "ROLE_ADMIN",
        }
    }

    /// `ROLE_` プレフィックス付きの文字列から `Role` をパースする。
    ///
    /// # Errors
    ///
    /// 未知のロール名の場合は `SharedKernelError::UnknownRole` を返す。
    pub fn parse(value: &str) -> Result<Self, SharedKernelError> {
        match value {
            "ROLE_SALES" => Ok(Self::Sales),
            "ROLE_SHIPPER" => Ok(Self::Shipper),
            "ROLE_CONSIGNEE" => Ok(Self::Consignee),
            "ROLE_TRACKER" => Ok(Self::Tracker),
            "ROLE_HANDLER" => Ok(Self::Handler),
            "ROLE_ROUTE_DESIGNER" => Ok(Self::RouteDesigner),
            "ROLE_BILLING" => Ok(Self::Billing),
            "ROLE_ADMIN" => Ok(Self::Admin),
            other => Err(SharedKernelError::UnknownRole(other.to_string())),
        }
    }
}

impl Location {
    /// UN/LOCODE 文字列から `Location` を生成する。
    ///
    /// # Errors
    ///
    /// 5 文字の英大文字でない場合は `SharedKernelError::InvalidUnLocode` を返す。
    pub fn new(code: &str) -> Result<Self, SharedKernelError> {
        if code.len() == 5 && code.chars().all(|c| c.is_ascii_uppercase()) {
            Ok(Self(code.to_string()))
        } else {
            Err(SharedKernelError::InvalidUnLocode(code.to_string()))
        }
    }

    /// UN/LOCODE 文字列を返す。
    #[must_use]
    pub fn code(&self) -> &str {
        &self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 正しい_un_locode_から_location_を生成できる() {
        let location = Location::new("JPTYO").expect("valid UN/LOCODE");
        assert_eq!(location.code(), "JPTYO");
    }

    #[test]
    fn 不正な_un_locode_はエラーになる() {
        assert!(Location::new("tokyo").is_err());
        assert!(Location::new("JP").is_err());
    }

    #[test]
    fn shipper_id_は生成した_uuid_を保持する() {
        let id = ShipperId::generate();
        assert_eq!(ShipperId::from_uuid(id.value()), id);
    }

    #[test]
    fn shipper_id_は_uuid_文字列からパースできる() {
        let id = ShipperId::generate();
        let parsed = ShipperId::parse(&id.to_string()).expect("valid uuid");
        assert_eq!(parsed, id);
    }

    #[test]
    fn 不正な_shipper_id_文字列はエラーになる() {
        assert!(ShipperId::parse("not-a-uuid").is_err());
    }

    #[test]
    fn role_は文字列表現と相互変換できる() {
        for role in [
            Role::Sales,
            Role::Shipper,
            Role::Consignee,
            Role::Tracker,
            Role::Handler,
            Role::RouteDesigner,
            Role::Billing,
            Role::Admin,
        ] {
            assert_eq!(Role::parse(role.as_str()).expect("known role"), role);
        }
    }

    #[test]
    fn 未知のロール名はエラーになる() {
        assert!(Role::parse("ROLE_UNKNOWN").is_err());
    }
}
