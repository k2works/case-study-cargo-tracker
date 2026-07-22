//! Booking Context のアプリケーション層。
//!
//! 貨物予約登録ユースケース（`BookCargoCommandService`）を提供する。
//! 荷主の存在確認は `ShipperExistenceChecker` ACL ポート経由で行い、Shipper Context を直接参照しない。

use domain_booking::{
    BookCargoCommand, BookingError, BookingId, Cargo, CargoRepository, Notification,
    NotificationPort, NotificationType, SelectedRouteView, ShipperExistenceChecker,
};
use shared_kernel::Role;

/// 貨物予約登録ユースケースのエラー。
#[derive(Debug, thiserror::Error)]
pub enum BookingServiceError {
    /// 指定された荷主が存在しない場合。
    #[error("shipper not found")]
    ShipperNotFound,
    /// ドメインの不変条件に違反した場合。
    #[error(transparent)]
    Domain(#[from] BookingError),
    /// 永続化層のエラー。
    #[error("repository error: {0}")]
    Repository(String),
    /// ACL（荷主存在確認）のエラー。
    #[error("acl error: {0}")]
    Acl(String),
    /// 対象予約が存在しない場合。
    #[error("booking not found: {0}")]
    NotFound(String),
}

impl From<domain_booking::RepositoryError> for BookingServiceError {
    fn from(value: domain_booking::RepositoryError) -> Self {
        Self::Repository(value.to_string())
    }
}

impl From<domain_booking::AclError> for BookingServiceError {
    fn from(value: domain_booking::AclError) -> Self {
        Self::Acl(value.to_string())
    }
}

/// 貨物予約登録コマンドサービス。
pub struct BookCargoCommandService<R: CargoRepository, C: ShipperExistenceChecker> {
    repository: R,
    shipper_checker: C,
}

impl<R: CargoRepository, C: ShipperExistenceChecker> BookCargoCommandService<R, C> {
    /// サービスを生成する。
    pub fn new(repository: R, shipper_checker: C) -> Self {
        Self {
            repository,
            shipper_checker,
        }
    }

    /// 貨物予約を登録し、採番された `BookingId` を返す。
    ///
    /// # Errors
    ///
    /// - 荷主が存在しない場合は `BookingServiceError::ShipperNotFound`
    /// - ドメイン不変条件違反は `BookingServiceError::Domain`
    /// - 永続化 / ACL 失敗はそれぞれ `Repository` / `Acl`
    pub async fn book(&self, command: BookCargoCommand) -> Result<BookingId, BookingServiceError> {
        if !self.shipper_checker.exists(&command.shipper_id).await? {
            return Err(BookingServiceError::ShipperNotFound);
        }

        let cargo = Cargo::book(command)?;
        let booking_id = cargo.booking_id().clone();
        self.repository.save(&cargo).await?;
        Ok(booking_id)
    }
}

/// 予約ライフサイクル（状態遷移＋通知）ユースケース（US06/US11/US12/US13）。
///
/// 貨物リポジトリで予約を読み込み、`Cargo` 集約の状態遷移メソッドを呼び、保存する。
/// 遷移に伴う通知は `NotificationPort` で記録する。確定経路の読み取りは
/// `SelectedRouteView`（Routing への逆方向 ACL）を通じてのみ行い、Routing を直接参照しない。
pub struct BookingLifecycleService<R, N, V>
where
    R: CargoRepository,
    N: NotificationPort,
    V: SelectedRouteView,
{
    repository: R,
    notifications: N,
    selected_routes: V,
}

impl<R, N, V> BookingLifecycleService<R, N, V>
where
    R: CargoRepository,
    N: NotificationPort,
    V: SelectedRouteView,
{
    /// サービスを生成する。
    pub fn new(repository: R, notifications: N, selected_routes: V) -> Self {
        Self {
            repository,
            notifications,
            selected_routes,
        }
    }

    async fn load(&self, booking_id: &str) -> Result<Cargo, BookingServiceError> {
        let id = BookingId::parse(booking_id)
            .map_err(|_| BookingServiceError::NotFound(booking_id.to_string()))?;
        self.repository
            .find_by_booking_id(&id)
            .await?
            .ok_or_else(|| BookingServiceError::NotFound(booking_id.to_string()))
    }

    /// 経路設計を依頼する（US06・`Preliminary → RouteDesigning`＋経路設計者通知）。
    ///
    /// # Errors
    ///
    /// 予約が無い場合は `NotFound`、不正遷移は `Domain`、永続化・通知失敗は `Repository`。
    pub async fn request_route_design(&self, booking_id: &str) -> Result<(), BookingServiceError> {
        let mut cargo = self.load(booking_id).await?;
        cargo.request_route_design()?;
        self.repository.save(&cargo).await?;
        let notification = Notification::new(
            cargo.booking_id().clone(),
            NotificationType::RouteDesignRequested,
            Role::RouteDesigner,
            "route-designer@cargotracker.example",
            "経路設計依頼",
            format!(
                "予約 {} の経路設計を依頼します。",
                cargo.booking_id().as_str()
            ),
        );
        self.notifications.notify(&notification).await?;
        Ok(())
    }

    /// 確定経路を予約に紐付ける（US11・`RouteDesigning → RouteProposed`）。
    ///
    /// # Errors
    ///
    /// 予約が無い場合は `NotFound`、不正遷移は `Domain`、永続化失敗は `Repository`。
    pub async fn propose_route(&self, booking_id: &str) -> Result<(), BookingServiceError> {
        let mut cargo = self.load(booking_id).await?;
        cargo.propose_route()?;
        self.repository.save(&cargo).await?;
        Ok(())
    }

    /// 確定経路を荷主に通知する（US12・状態は変えず通知を記録）。
    ///
    /// 通知内容は確定経路の要約（経由港・所要日数・到着予定日）から組み立てる。料金概算は
    /// 費用データ未整備のため「-」とする（計画注記）。
    ///
    /// # Errors
    ///
    /// 予約が無い / 確定経路が無い場合は `NotFound`、ACL 失敗は `Acl`、通知失敗は `Repository`。
    pub async fn notify_route_to_shipper(
        &self,
        booking_id: &str,
    ) -> Result<(), BookingServiceError> {
        let cargo = self.load(booking_id).await?;
        // US12 の不変条件: 経路提案中の予約のみ荷主通知できる（UI ガードに依存しない）。
        if cargo.status() != domain_booking::BookingStatus::RouteProposed {
            return Err(BookingServiceError::Domain(
                BookingError::InvalidStatusTransition {
                    from: cargo.status().as_str(),
                    action: "notify_route_to_shipper",
                },
            ));
        }
        let summary = self
            .selected_routes
            .find_by_booking(booking_id)
            .await?
            .ok_or_else(|| BookingServiceError::NotFound(booking_id.to_string()))?;
        let body = format!(
            "確定経路をご案内します。経由港: {} / 所要日数: {} 日 / 到着予定日: {} / 料金概算: -",
            summary.transit_ports.join("→"),
            summary.transit_days,
            summary.expected_arrival,
        );
        let notification = Notification::new(
            cargo.booking_id().clone(),
            NotificationType::RouteNotifiedToShipper,
            Role::Shipper,
            cargo.consignee().contact().to_string(),
            "確定経路のご案内",
            body,
        );
        self.notifications.notify(&notification).await?;
        Ok(())
    }

    /// 予約を確定する（US13・`RouteProposed → Confirmed`＋追跡番号発行依頼通知）。
    ///
    /// # Errors
    ///
    /// 予約が無い場合は `NotFound`、不正遷移は `Domain`、永続化・通知失敗は `Repository`。
    pub async fn confirm(&self, booking_id: &str) -> Result<(), BookingServiceError> {
        let mut cargo = self.load(booking_id).await?;
        cargo.confirm()?;
        self.repository.save(&cargo).await?;
        let notification = Notification::new(
            cargo.booking_id().clone(),
            NotificationType::TrackingIssueRequested,
            Role::RouteDesigner,
            "route-designer@cargotracker.example",
            "追跡番号発行依頼",
            format!(
                "予約 {} が確定しました。追跡番号の発行を依頼します。",
                cargo.booking_id().as_str()
            ),
        );
        self.notifications.notify(&notification).await?;
        Ok(())
    }

    /// ルート変更のため経路設計中へ差し戻す（US13・`RouteProposed → RouteDesigning`）。
    ///
    /// # Errors
    ///
    /// 予約が無い場合は `NotFound`、不正遷移は `Domain`、永続化失敗は `Repository`。
    pub async fn revert_to_route_designing(
        &self,
        booking_id: &str,
    ) -> Result<(), BookingServiceError> {
        let mut cargo = self.load(booking_id).await?;
        cargo.revert_to_route_designing()?;
        self.repository.save(&cargo).await?;
        Ok(())
    }

    /// 予約をキャンセルする（US13・`→ Cancelled`＋荷主へキャンセル確認通知）。
    ///
    /// # Errors
    ///
    /// 予約が無い場合は `NotFound`、不正遷移は `Domain`、永続化・通知失敗は `Repository`。
    pub async fn cancel(&self, booking_id: &str) -> Result<(), BookingServiceError> {
        let mut cargo = self.load(booking_id).await?;
        cargo.cancel()?;
        self.repository.save(&cargo).await?;
        let notification = Notification::new(
            cargo.booking_id().clone(),
            NotificationType::BookingCancelled,
            Role::Shipper,
            cargo.consignee().contact().to_string(),
            "予約キャンセルのご確認",
            format!(
                "予約 {} をキャンセルしました。",
                cargo.booking_id().as_str()
            ),
        );
        self.notifications.notify(&notification).await?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::NaiveDate;
    use domain_booking::{
        AclError, CargoType, Consignee, RepositoryError, RouteSpecification, Weight,
    };
    use mockall::mock;
    use rust_decimal::Decimal;
    use shared_kernel::{Location, ShipperId};

    mock! {
        Repo {}
        #[async_trait::async_trait]
        impl CargoRepository for Repo {
            async fn save(&self, cargo: &Cargo) -> Result<(), RepositoryError>;
            async fn find_by_booking_id(&self, id: &BookingId) -> Result<Option<Cargo>, RepositoryError>;
        }
    }

    mock! {
        Checker {}
        #[async_trait::async_trait]
        impl ShipperExistenceChecker for Checker {
            async fn exists(&self, shipper_id: &ShipperId) -> Result<bool, AclError>;
        }
    }

    mock! {
        Notifier {}
        #[async_trait::async_trait]
        impl NotificationPort for Notifier {
            async fn notify(&self, notification: &Notification) -> Result<(), RepositoryError>;
        }
    }

    mock! {
        RouteView {}
        #[async_trait::async_trait]
        impl SelectedRouteView for RouteView {
            async fn find_by_booking(
                &self,
                booking_id: &str,
            ) -> Result<Option<domain_booking::SelectedRouteSummary>, AclError>;
        }
    }

    fn preliminary_cargo() -> Cargo {
        Cargo::book(command()).unwrap()
    }

    #[tokio::test]
    async fn 経路設計依頼で経路設計中に遷移し通知が記録される() {
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id()
            .returning(|_| Ok(Some(preliminary_cargo())));
        repo.expect_save()
            .withf(|c| c.status() == domain_booking::BookingStatus::RouteDesigning)
            .times(1)
            .returning(|_| Ok(()));
        let mut notifier = MockNotifier::new();
        notifier
            .expect_notify()
            .withf(|n| n.notification_type() == NotificationType::RouteDesignRequested)
            .times(1)
            .returning(|_| Ok(()));
        let view = MockRouteView::new();
        let service = BookingLifecycleService::new(repo, notifier, view);

        service.request_route_design("BKG-0001").await.expect("ok");
    }

    #[tokio::test]
    async fn 存在しない予約の依頼はnotfoundになる() {
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id().returning(|_| Ok(None));
        let service = BookingLifecycleService::new(repo, MockNotifier::new(), MockRouteView::new());
        let err = service.request_route_design("BKG-9999").await.unwrap_err();
        assert!(matches!(err, BookingServiceError::NotFound(_)));
    }

    #[tokio::test]
    async fn 不正な状態からの確定はドメインエラーになり保存されない() {
        // 仮受付から直接確定は不可。
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id()
            .returning(|_| Ok(Some(preliminary_cargo())));
        repo.expect_save().times(0);
        let mut notifier = MockNotifier::new();
        notifier.expect_notify().times(0);
        let service = BookingLifecycleService::new(repo, notifier, MockRouteView::new());
        let err = service.confirm("BKG-0001").await.unwrap_err();
        assert!(matches!(err, BookingServiceError::Domain(_)));
    }

    #[tokio::test]
    async fn 荷主通知は確定経路要約から組み立てて記録される() {
        use domain_booking::SelectedRouteSummary;
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id().returning(|_| {
            // 経路提案中の予約を用意する。
            let mut c = preliminary_cargo();
            c.request_route_design().unwrap();
            c.propose_route().unwrap();
            Ok(Some(c))
        });
        let mut notifier = MockNotifier::new();
        notifier
            .expect_notify()
            .withf(|n| {
                n.notification_type() == NotificationType::RouteNotifiedToShipper
                    && n.body().contains("JPOSA→SGSIN→DEHAM")
                    && n.body().contains("28")
            })
            .times(1)
            .returning(|_| Ok(()));
        let mut view = MockRouteView::new();
        view.expect_find_by_booking().returning(|_| {
            Ok(Some(SelectedRouteSummary {
                voyage_numbers: vec!["V0002".into(), "V0003".into()],
                transit_ports: vec!["JPOSA".into(), "SGSIN".into(), "DEHAM".into()],
                transit_days: 28,
                expected_arrival: "2026-05-30".into(),
            }))
        });
        let service = BookingLifecycleService::new(repo, notifier, view);
        service
            .notify_route_to_shipper("BKG-0001")
            .await
            .expect("ok");
    }

    #[tokio::test]
    async fn 経路提案中でない予約の荷主通知は拒否され通知されない() {
        // 仮受付のまま荷主通知しようとすると不正状態でドメインエラー。
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id()
            .returning(|_| Ok(Some(preliminary_cargo())));
        let mut notifier = MockNotifier::new();
        notifier.expect_notify().times(0);
        let mut view = MockRouteView::new();
        view.expect_find_by_booking().times(0);
        let service = BookingLifecycleService::new(repo, notifier, view);
        let err = service
            .notify_route_to_shipper("BKG-0001")
            .await
            .unwrap_err();
        assert!(matches!(err, BookingServiceError::Domain(_)));
    }

    #[tokio::test]
    async fn キャンセルで通知が記録される() {
        let mut repo = MockRepo::new();
        repo.expect_find_by_booking_id()
            .returning(|_| Ok(Some(preliminary_cargo())));
        repo.expect_save()
            .withf(|c| c.status() == domain_booking::BookingStatus::Cancelled)
            .times(1)
            .returning(|_| Ok(()));
        let mut notifier = MockNotifier::new();
        notifier
            .expect_notify()
            .withf(|n| n.notification_type() == NotificationType::BookingCancelled)
            .times(1)
            .returning(|_| Ok(()));
        let service = BookingLifecycleService::new(repo, notifier, MockRouteView::new());
        service.cancel("BKG-0001").await.expect("ok");
    }

    fn command() -> BookCargoCommand {
        BookCargoCommand {
            shipper_id: ShipperId::generate(),
            route_specification: RouteSpecification::new(
                Location::new("JPOSA").unwrap(),
                Location::new("USLAX").unwrap(),
                NaiveDate::from_ymd_opt(2026, 4, 15).unwrap(),
            )
            .unwrap(),
            consignee: Consignee::new("LA Trading", "contact@la.example").unwrap(),
            cargo_type: CargoType::General,
            weight: Weight::new(Decimal::new(1200, 0)).unwrap(),
            dimensions: None,
            quantity: None,
            description: None,
            hazardous_declaration: None,
            temperature_requirement: None,
        }
    }

    #[tokio::test]
    async fn 荷主が存在すれば予約が登録される() {
        let mut checker = MockChecker::new();
        checker.expect_exists().times(1).returning(|_| Ok(true));
        let mut repo = MockRepo::new();
        repo.expect_save().times(1).returning(|_| Ok(()));
        let service = BookCargoCommandService::new(repo, checker);

        service.book(command()).await.expect("予約登録成功");
    }

    #[tokio::test]
    async fn 荷主が存在しない場合はエラーで保存されない() {
        let mut checker = MockChecker::new();
        checker.expect_exists().times(1).returning(|_| Ok(false));
        let mut repo = MockRepo::new();
        repo.expect_save().times(0);
        let service = BookCargoCommandService::new(repo, checker);

        let result = service.book(command()).await;
        assert!(matches!(result, Err(BookingServiceError::ShipperNotFound)));
    }

    #[tokio::test]
    async fn 危険物申告欠落はドメインエラーになる() {
        let mut checker = MockChecker::new();
        checker.expect_exists().returning(|_| Ok(true));
        let mut repo = MockRepo::new();
        repo.expect_save().times(0);
        let service = BookCargoCommandService::new(repo, checker);

        let mut cmd = command();
        cmd.cargo_type = CargoType::Hazardous;
        let result = service.book(cmd).await;
        assert!(matches!(result, Err(BookingServiceError::Domain(_))));
    }
}
