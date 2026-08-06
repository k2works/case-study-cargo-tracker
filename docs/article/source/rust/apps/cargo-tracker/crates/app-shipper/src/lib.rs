//! Shipper Context のアプリケーション層。
//!
//! 荷主登録ユースケース（`RegisterShipperCommandService`）と荷主検索クエリ
//! （`FindShipperQueryService`）を提供する。
//! ドメインの出力ポート `ShipperRepository` にのみ依存し、インフラ実装には依存しない。

pub mod query;

pub use query::{FindShipperQueryService, QueryError, ShipperQueryPort, ShipperSummary};

use domain_shipper::{
    Address, ContractNumber, CorporateProfile, DiscountRate, Email, Phone, Shipper, ShipperError,
    ShipperKind, ShipperName, ShipperRepository,
};
use rust_decimal::Decimal;
use shared_kernel::ShipperId;

/// 荷主登録の入力。プレゼンテーション層から受け取る生値を保持する。
#[derive(Debug, Clone)]
pub struct RegisterShipperInput {
    /// 氏名または社名。
    pub name: String,
    /// メールアドレス。
    pub email: String,
    /// 電話番号（任意）。
    pub phone: Option<String>,
    /// 住所（任意）。
    pub address: Option<String>,
    /// 荷主種別。
    pub kind: ShipperKindInput,
}

/// 荷主種別の入力。法人の場合は契約情報を含む。
#[derive(Debug, Clone)]
pub enum ShipperKindInput {
    /// 個人荷主。
    Individual,
    /// 法人荷主（契約番号・割引率）。
    Corporate {
        /// 契約番号。
        contract_number: String,
        /// 割引率（0.0000〜0.3000）。
        discount_rate: Decimal,
    },
}

/// 荷主登録ユースケースのエラー。
#[derive(Debug, thiserror::Error)]
pub enum ShipperServiceError {
    /// 入力値がドメインの不変条件に違反した場合。
    #[error(transparent)]
    Domain(#[from] ShipperError),
    /// 同一メールアドレスの荷主が既に存在する場合。
    #[error("email already registered: {0}")]
    EmailAlreadyExists(String),
    /// 永続化層のエラー。
    #[error("repository error: {0}")]
    Repository(String),
}

impl From<domain_shipper::RepositoryError> for ShipperServiceError {
    fn from(value: domain_shipper::RepositoryError) -> Self {
        Self::Repository(value.to_string())
    }
}

/// 荷主登録コマンドサービス。
pub struct RegisterShipperCommandService<R: ShipperRepository> {
    repository: R,
}

impl<R: ShipperRepository> RegisterShipperCommandService<R> {
    /// サービスを生成する。
    pub fn new(repository: R) -> Self {
        Self { repository }
    }

    /// 荷主を登録し、採番された `ShipperId` を返す。
    ///
    /// # Errors
    ///
    /// - 入力値が不正な場合は `ShipperServiceError::Domain`
    /// - メール重複時は `ShipperServiceError::EmailAlreadyExists`
    /// - 永続化失敗時は `ShipperServiceError::Repository`
    pub async fn register(
        &self,
        input: RegisterShipperInput,
    ) -> Result<ShipperId, ShipperServiceError> {
        let name = ShipperName::new(input.name)?;
        let email = Email::new(input.email)?;
        let phone = input.phone.map(Phone::new).transpose()?;
        let address = input.address.map(Address::new).transpose()?;
        let kind = match input.kind {
            ShipperKindInput::Individual => ShipperKind::Individual,
            ShipperKindInput::Corporate {
                contract_number,
                discount_rate,
            } => {
                let profile = CorporateProfile::new(
                    ContractNumber::new(contract_number)?,
                    DiscountRate::new(discount_rate)?,
                );
                ShipperKind::Corporate(profile)
            }
        };

        if self.repository.exists_by_email(&email).await? {
            return Err(ShipperServiceError::EmailAlreadyExists(
                email.as_str().to_string(),
            ));
        }

        let shipper = Shipper::register(name, email, phone, address, kind);
        let id = shipper.id();
        self.repository.save(&shipper).await?;
        Ok(id)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use domain_shipper::RepositoryError;
    use mockall::mock;

    mock! {
        Repo {}

        #[async_trait::async_trait]
        impl ShipperRepository for Repo {
            async fn save(&self, shipper: &Shipper) -> Result<(), RepositoryError>;
            async fn find_by_id(&self, id: &ShipperId) -> Result<Option<Shipper>, RepositoryError>;
            async fn exists_by_email(&self, email: &Email) -> Result<bool, RepositoryError>;
        }
    }

    fn individual_input() -> RegisterShipperInput {
        RegisterShipperInput {
            name: "山田商事".to_string(),
            email: "yamada@example.com".to_string(),
            phone: None,
            address: None,
            kind: ShipperKindInput::Individual,
        }
    }

    #[tokio::test]
    async fn 新規荷主を登録するとリポジトリに保存される() {
        let mut repo = MockRepo::new();
        repo.expect_exists_by_email()
            .times(1)
            .returning(|_| Ok(false));
        repo.expect_save().times(1).returning(|_| Ok(()));
        let service = RegisterShipperCommandService::new(repo);

        // 登録が成功し、ID が返る
        service
            .register(individual_input())
            .await
            .expect("登録成功");
    }

    #[tokio::test]
    async fn メール重複時はエラーになり保存されない() {
        let mut repo = MockRepo::new();
        repo.expect_exists_by_email()
            .times(1)
            .returning(|_| Ok(true));
        repo.expect_save().times(0);
        let service = RegisterShipperCommandService::new(repo);

        let result = service.register(individual_input()).await;
        assert!(matches!(
            result,
            Err(ShipperServiceError::EmailAlreadyExists(_))
        ));
    }

    #[tokio::test]
    async fn 不正なメールはドメインエラーになり重複確認も行わない() {
        let mut repo = MockRepo::new();
        repo.expect_exists_by_email().times(0);
        repo.expect_save().times(0);
        let service = RegisterShipperCommandService::new(repo);

        let mut input = individual_input();
        input.email = "invalid".to_string();
        let result = service.register(input).await;
        assert!(matches!(result, Err(ShipperServiceError::Domain(_))));
    }

    #[tokio::test]
    async fn 法人荷主を割引率付きで登録できる() {
        let mut repo = MockRepo::new();
        repo.expect_exists_by_email().returning(|_| Ok(false));
        repo.expect_save().times(1).returning(|_| Ok(()));
        let service = RegisterShipperCommandService::new(repo);

        let mut input = individual_input();
        input.kind = ShipperKindInput::Corporate {
            contract_number: "C-001".to_string(),
            discount_rate: Decimal::new(1500, 4),
        };
        assert!(service.register(input).await.is_ok());
    }
}
