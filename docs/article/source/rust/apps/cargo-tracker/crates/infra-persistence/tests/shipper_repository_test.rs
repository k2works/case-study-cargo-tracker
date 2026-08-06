//! `SqlxShipperRepository` の統合テスト（testcontainers + 実 PostgreSQL 16）。

use domain_shipper::{
    ContractNumber, CorporateProfile, DiscountRate, Email, Shipper, ShipperKind, ShipperName,
    ShipperRepository,
};
use infra_persistence::{MIGRATOR, SqlxShipperRepository};
use rust_decimal::Decimal;
use sqlx::PgPool;
use testcontainers::ContainerAsync;
use testcontainers::ImageExt;
use testcontainers_modules::postgres::Postgres;
use testcontainers_modules::testcontainers::runners::AsyncRunner;

async fn setup() -> (PgPool, ContainerAsync<Postgres>) {
    let container = Postgres::default()
        .with_tag("16-alpine")
        .start()
        .await
        .expect("start postgres");
    let port = container.get_host_port_ipv4(5432).await.expect("host port");
    let url = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");
    let pool = PgPool::connect(&url).await.expect("connect");
    MIGRATOR.run(&pool).await.expect("migrate");
    (pool, container)
}

fn individual(email: &str) -> Shipper {
    Shipper::register(
        ShipperName::new("山田商事").unwrap(),
        Email::new(email).unwrap(),
        None,
        None,
        ShipperKind::Individual,
    )
}

#[tokio::test]
async fn 荷主を保存して_id_で検索できる() {
    let (pool, _c) = setup().await;
    let repo = SqlxShipperRepository::new(pool);
    let shipper = individual("yamada@example.com");
    let id = shipper.id();

    repo.save(&shipper).await.expect("save");
    let found = repo.find_by_id(&id).await.expect("find").expect("present");

    assert_eq!(found.id(), id);
    assert_eq!(found.email().as_str(), "yamada@example.com");
    assert!(!found.is_corporate());
}

#[tokio::test]
async fn 法人荷主は契約情報付きで永続化される() {
    let (pool, _c) = setup().await;
    let repo = SqlxShipperRepository::new(pool);
    let profile = CorporateProfile::new(
        ContractNumber::new("C-001").unwrap(),
        DiscountRate::new(Decimal::new(1500, 4)).unwrap(),
    );
    let shipper = Shipper::register(
        ShipperName::new("グローバル物流株式会社").unwrap(),
        Email::new("corp@example.com").unwrap(),
        None,
        None,
        ShipperKind::Corporate(profile),
    );
    let id = shipper.id();

    repo.save(&shipper).await.expect("save");
    let found = repo.find_by_id(&id).await.expect("find").expect("present");

    match found.kind() {
        ShipperKind::Corporate(p) => {
            assert_eq!(p.discount_rate().value(), Decimal::new(1500, 4));
            assert_eq!(p.contract_number().as_str(), "C-001");
        }
        ShipperKind::Individual => panic!("法人のはず"),
    }
}

#[tokio::test]
async fn exists_by_email_は登録済みメールを検出する() {
    let (pool, _c) = setup().await;
    let repo = SqlxShipperRepository::new(pool);
    let email = Email::new("dup@example.com").unwrap();

    assert!(!repo.exists_by_email(&email).await.expect("exists"));
    repo.save(&individual("dup@example.com"))
        .await
        .expect("save");
    assert!(repo.exists_by_email(&email).await.expect("exists"));
}
