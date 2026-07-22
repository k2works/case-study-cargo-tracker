//! `SqlxCargoRepository` / `SqlxShipperExistenceChecker` の統合テスト（testcontainers）。

use chrono::NaiveDate;
use domain_booking::{
    BookCargoCommand, Cargo, CargoRepository, CargoType, Consignee, HazardousDeclaration,
    RouteSpecification, ShipperExistenceChecker, Weight,
};
use domain_shipper::{Email, Shipper, ShipperKind, ShipperName, ShipperRepository};
use infra_persistence::{
    MIGRATOR, SqlxCargoRepository, SqlxShipperExistenceChecker, SqlxShipperRepository,
};
use rust_decimal::Decimal;
use shared_kernel::{Location, ShipperId};
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

async fn persist_shipper(pool: &PgPool) -> ShipperId {
    let repo = SqlxShipperRepository::new(pool.clone());
    let shipper = Shipper::register(
        ShipperName::new("山田商事").unwrap(),
        Email::new("yamada@example.com").unwrap(),
        None,
        None,
        ShipperKind::Individual,
    );
    let id = shipper.id();
    repo.save(&shipper).await.expect("save shipper");
    id
}

fn command(shipper_id: ShipperId, cargo_type: CargoType) -> BookCargoCommand {
    BookCargoCommand {
        shipper_id,
        route_specification: RouteSpecification::new(
            Location::new("JPOSA").unwrap(),
            Location::new("USLAX").unwrap(),
            NaiveDate::from_ymd_opt(2026, 4, 15).unwrap(),
        )
        .unwrap(),
        consignee: Consignee::new("LA Trading", "contact@la.example").unwrap(),
        cargo_type,
        weight: Weight::new(Decimal::new(1200, 0)).unwrap(),
        dimensions: None,
        quantity: None,
        description: None,
        hazardous_declaration: None,
        temperature_requirement: None,
    }
}

#[tokio::test]
async fn 貨物予約を保存して予約番号で検索できる() {
    let (pool, _c) = setup().await;
    let shipper_id = persist_shipper(&pool).await;
    let repo = SqlxCargoRepository::new(pool.clone());
    let cargo = Cargo::book(command(shipper_id, CargoType::General)).unwrap();
    let booking_id = cargo.booking_id().clone();

    repo.save(&cargo).await.expect("save cargo");
    let found = repo
        .find_by_booking_id(&booking_id)
        .await
        .expect("find")
        .expect("present");

    assert_eq!(found.booking_id(), &booking_id);
    assert_eq!(found.route_specification().origin().code(), "JPOSA");
    assert_eq!(found.shipper_id(), shipper_id);
}

#[tokio::test]
async fn 危険物申告付きの予約を永続化して復元できる() {
    let (pool, _c) = setup().await;
    let shipper_id = persist_shipper(&pool).await;
    let repo = SqlxCargoRepository::new(pool.clone());
    let mut cmd = command(shipper_id, CargoType::Hazardous);
    cmd.hazardous_declaration = Some(HazardousDeclaration::new("3", "UN1203", "Gasoline").unwrap());
    let cargo = Cargo::book(cmd).unwrap();
    let booking_id = cargo.booking_id().clone();

    repo.save(&cargo).await.expect("save");
    let found = repo
        .find_by_booking_id(&booking_id)
        .await
        .expect("find")
        .expect("present");

    let decl = found.hazardous_declaration().expect("declaration present");
    assert_eq!(decl.un_number(), "UN1203");
}

#[tokio::test]
async fn 状態遷移後の保存で予約状態が更新される() {
    use domain_booking::BookingStatus;

    let (pool, _c) = setup().await;
    let shipper_id = persist_shipper(&pool).await;
    let repo = SqlxCargoRepository::new(pool.clone());
    let mut cargo = Cargo::book(command(shipper_id, CargoType::General)).unwrap();
    let booking_id = cargo.booking_id().clone();
    repo.save(&cargo).await.expect("save cargo");

    // 経路設計依頼 → 経路提案 → 確定 と遷移し、都度 upsert で状態が反映される。
    cargo.request_route_design().unwrap();
    repo.save(&cargo).await.expect("update to route_designing");
    assert_eq!(
        repo.find_by_booking_id(&booking_id)
            .await
            .unwrap()
            .unwrap()
            .status(),
        BookingStatus::RouteDesigning
    );

    cargo.propose_route().unwrap();
    repo.save(&cargo).await.expect("update to route_proposed");
    cargo.confirm().unwrap();
    repo.save(&cargo).await.expect("update to confirmed");
    assert_eq!(
        repo.find_by_booking_id(&booking_id)
            .await
            .unwrap()
            .unwrap()
            .status(),
        BookingStatus::Confirmed
    );
}

#[tokio::test]
async fn shipper_existence_checker_は存在有無を判定する() {
    let (pool, _c) = setup().await;
    let shipper_id = persist_shipper(&pool).await;
    let checker = SqlxShipperExistenceChecker::new(pool.clone());

    assert!(checker.exists(&shipper_id).await.expect("exists"));
    assert!(
        !checker
            .exists(&ShipperId::generate())
            .await
            .expect("exists")
    );
}
