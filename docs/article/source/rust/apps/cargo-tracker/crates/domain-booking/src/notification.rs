//! 通知（Notification）のドメイン表現と出力ポート。
//!
//! 予約ライフサイクルの節目（経路設計依頼・荷主への経路通知・追跡番号発行依頼・
//! キャンセル）で送信する通知を表す。本 IT では「送信＝記録」に限定し、実際の
//! メール / SMS 配信はスコープ外とする（`NotificationPort` の実装が記録を担う）。

use crate::value_objects::BookingId;
use shared_kernel::Role;

/// 通知の種別。予約ライフサイクルの節目に対応する。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NotificationType {
    /// 経路設計依頼（US06・営業 → 経路設計者）。
    RouteDesignRequested,
    /// 確定経路の荷主通知（US12・営業 → 荷主）。
    RouteNotifiedToShipper,
    /// 追跡番号発行依頼（US13・営業 → 経路設計者）。
    TrackingIssueRequested,
    /// 予約キャンセル確認（US13・営業 → 荷主）。
    BookingCancelled,
}

impl NotificationType {
    /// 永続化用の文字列表現（SCREAMING_SNAKE_CASE）。
    #[must_use]
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::RouteDesignRequested => "ROUTE_DESIGN_REQUESTED",
            Self::RouteNotifiedToShipper => "ROUTE_NOTIFIED_TO_SHIPPER",
            Self::TrackingIssueRequested => "TRACKING_ISSUE_REQUESTED",
            Self::BookingCancelled => "BOOKING_CANCELLED",
        }
    }
}

/// 送信する通知（値オブジェクト）。宛先・件名・本文を保持する。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Notification {
    booking_id: BookingId,
    notification_type: NotificationType,
    recipient_role: Role,
    recipient_email: String,
    subject: String,
    body: String,
}

impl Notification {
    /// 通知を生成する。
    #[must_use]
    pub fn new(
        booking_id: BookingId,
        notification_type: NotificationType,
        recipient_role: Role,
        recipient_email: impl Into<String>,
        subject: impl Into<String>,
        body: impl Into<String>,
    ) -> Self {
        Self {
            booking_id,
            notification_type,
            recipient_role,
            recipient_email: recipient_email.into(),
            subject: subject.into(),
            body: body.into(),
        }
    }

    /// 対象予約 ID。
    #[must_use]
    pub fn booking_id(&self) -> &BookingId {
        &self.booking_id
    }

    /// 通知種別。
    #[must_use]
    pub fn notification_type(&self) -> NotificationType {
        self.notification_type
    }

    /// 受信者ロール。
    #[must_use]
    pub fn recipient_role(&self) -> Role {
        self.recipient_role
    }

    /// 受信者メールアドレス。
    #[must_use]
    pub fn recipient_email(&self) -> &str {
        &self.recipient_email
    }

    /// 件名。
    #[must_use]
    pub fn subject(&self) -> &str {
        &self.subject
    }

    /// 本文。
    #[must_use]
    pub fn body(&self) -> &str {
        &self.body
    }
}

/// 通知送信の出力ポート。実装はインフラ層（送信＝記録）で行う。
#[async_trait::async_trait]
pub trait NotificationPort: Send + Sync {
    /// 通知を送信（記録）する。
    async fn notify(
        &self,
        notification: &Notification,
    ) -> Result<(), crate::ports::RepositoryError>;
}

/// `Arc<dyn NotificationPort>` へ委譲するブランケット実装（ADR-0003）。
#[async_trait::async_trait]
impl NotificationPort for std::sync::Arc<dyn NotificationPort> {
    async fn notify(
        &self,
        notification: &Notification,
    ) -> Result<(), crate::ports::RepositoryError> {
        (**self).notify(notification).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 通知種別は永続化文字列に変換できる() {
        assert_eq!(
            NotificationType::RouteDesignRequested.as_str(),
            "ROUTE_DESIGN_REQUESTED"
        );
        assert_eq!(
            NotificationType::RouteNotifiedToShipper.as_str(),
            "ROUTE_NOTIFIED_TO_SHIPPER"
        );
        assert_eq!(
            NotificationType::TrackingIssueRequested.as_str(),
            "TRACKING_ISSUE_REQUESTED"
        );
        assert_eq!(
            NotificationType::BookingCancelled.as_str(),
            "BOOKING_CANCELLED"
        );
    }

    #[test]
    fn 通知は宛先と件名本文を保持する() {
        let booking_id = BookingId::parse("BKG-0001").unwrap();
        let n = Notification::new(
            booking_id,
            NotificationType::RouteDesignRequested,
            Role::RouteDesigner,
            "designer@example.com",
            "経路設計依頼",
            "予約 BKG-0001 の経路設計を依頼します。",
        );
        assert_eq!(n.booking_id().as_str(), "BKG-0001");
        assert_eq!(
            n.notification_type(),
            NotificationType::RouteDesignRequested
        );
        assert_eq!(n.recipient_role(), Role::RouteDesigner);
        assert_eq!(n.recipient_email(), "designer@example.com");
        assert_eq!(n.subject(), "経路設計依頼");
        assert!(n.body().contains("BKG-0001"));
    }
}
