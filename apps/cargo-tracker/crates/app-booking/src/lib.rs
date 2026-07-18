//! Booking Context のアプリケーション層。
//!
//! 貨物予約登録ユースケース（`BookCargoCommandService`）を提供する。
//! 荷主の存在確認は `ShipperExistenceChecker` ACL ポート経由で行い、Shipper Context を直接参照しない。

use domain_booking::{
    BookCargoCommand, BookingError, BookingId, Cargo, CargoRepository, ShipperExistenceChecker,
};

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
