//! `SqlxVoyageRepository` の統合テスト（testcontainers + 実 PostgreSQL 16）。

use chrono::{DateTime, Utc};
use domain_routing::{
    CargoType, Carrier, CarrierMovement, Schedule, VesselName, Voyage, VoyageNumber,
    VoyageRepository, VoyageSearchCriteria,
};
use infra_persistence::{MIGRATOR, SqlxVoyageRepository};
use shared_kernel::Location;
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

fn dt(s: &str) -> DateTime<Utc> {
    DateTime::parse_from_rfc3339(s)
        .expect("valid datetime")
        .with_timezone(&Utc)
}

fn voyage(number: &str, origin: &str, dest: &str, cargo_types: Vec<CargoType>) -> Voyage {
    let leg = CarrierMovement::new(
        Location::new(origin).unwrap(),
        Location::new(dest).unwrap(),
        dt("2026-04-01T18:00:00Z"),
        dt("2026-04-14T08:00:00Z"),
    )
    .unwrap();
    Voyage::register(
        VoyageNumber::new(number).unwrap(),
        VesselName::new("SAKURA MARU").unwrap(),
        Carrier::new("Nippon Line").unwrap(),
        cargo_types,
        Schedule::new(vec![leg]).unwrap(),
    )
}

#[tokio::test]
async fn 航海を保存して航海番号で検索できる() {
    let (pool, _c) = setup().await;
    let repo = SqlxVoyageRepository::new(pool);
    let v = voyage(
        "V0042",
        "JPOSA",
        "USLAX",
        vec![CargoType::General, CargoType::Hazardous],
    );

    repo.save(&v).await.expect("save");
    let found = repo
        .find_by_voyage_number(&VoyageNumber::new("V0042").unwrap())
        .await
        .expect("find")
        .expect("present");

    assert_eq!(found.voyage_number().as_str(), "V0042");
    assert_eq!(found.origin().code(), "JPOSA");
    assert_eq!(found.destination().code(), "USLAX");
    assert!(found.supports(CargoType::Hazardous));
    assert_eq!(found.vessel_name().as_str(), "SAKURA MARU");
}

#[tokio::test]
async fn 存在しない航海番号はnoneを返す() {
    let (pool, _c) = setup().await;
    let repo = SqlxVoyageRepository::new(pool);
    let found = repo
        .find_by_voyage_number(&VoyageNumber::new("NOPE99").unwrap())
        .await
        .expect("find");
    assert!(found.is_none());
}

#[tokio::test]
async fn 同一航海番号でsaveすると上書き更新される() {
    let (pool, _c) = setup().await;
    let repo = SqlxVoyageRepository::new(pool);
    repo.save(&voyage("V0100", "JPOSA", "USLAX", vec![CargoType::General]))
        .await
        .expect("save1");

    // 出発地・貨物種別を変えて再保存（同一航海番号）
    let updated = voyage("V0100", "JPYOK", "DEHAM", vec![CargoType::Refrigerated]);
    repo.save(&updated).await.expect("save2");

    let found = repo
        .find_by_voyage_number(&VoyageNumber::new("V0100").unwrap())
        .await
        .expect("find")
        .expect("present");
    assert_eq!(found.origin().code(), "JPYOK");
    assert!(found.supports(CargoType::Refrigerated));
    assert!(!found.supports(CargoType::General));

    // 重複行が残っていないこと（全件 1 件）
    let all = repo.find_all().await.expect("all");
    assert_eq!(all.len(), 1);
}

#[tokio::test]
async fn 検索条件で航海を絞り込める() {
    let (pool, _c) = setup().await;
    let repo = SqlxVoyageRepository::new(pool);
    repo.save(&voyage("V0001", "JPOSA", "USLAX", vec![CargoType::General]))
        .await
        .unwrap();
    repo.save(&voyage(
        "V0002",
        "JPYOK",
        "DEHAM",
        vec![CargoType::Hazardous],
    ))
    .await
    .unwrap();

    let criteria = VoyageSearchCriteria {
        origin: Some(Location::new("JPOSA").unwrap()),
        destination: None,
        cargo_type: None,
    };
    let result = repo.search(&criteria).await.expect("search");
    assert_eq!(result.len(), 1);
    assert_eq!(result[0].voyage_number().as_str(), "V0001");

    // 危険物対応の絞り込み
    let hazard = VoyageSearchCriteria {
        origin: None,
        destination: None,
        cargo_type: Some(CargoType::Hazardous),
    };
    let result = repo.search(&hazard).await.expect("search");
    assert_eq!(result.len(), 1);
    assert_eq!(result[0].voyage_number().as_str(), "V0002");
}

#[tokio::test]
async fn 検索は到着地と複合条件でsql絞り込みできる() {
    let (pool, _c) = setup().await;
    let repo = SqlxVoyageRepository::new(pool);
    repo.save(&voyage("V0001", "JPOSA", "USLAX", vec![CargoType::General]))
        .await
        .unwrap();
    repo.save(&voyage(
        "V0002",
        "JPYOK",
        "DEHAM",
        vec![CargoType::Hazardous],
    ))
    .await
    .unwrap();
    repo.save(&voyage(
        "V0003",
        "JPOSA",
        "USLAX",
        vec![CargoType::General, CargoType::Refrigerated],
    ))
    .await
    .unwrap();

    // 到着地単独（USLAX 行きは V0001・V0003）
    let by_dest = VoyageSearchCriteria {
        origin: None,
        destination: Some(Location::new("USLAX").unwrap()),
        cargo_type: None,
    };
    let mut result = repo.search(&by_dest).await.expect("search");
    result.sort_by(|a, b| a.voyage_number().as_str().cmp(b.voyage_number().as_str()));
    assert_eq!(result.len(), 2);
    assert_eq!(result[0].voyage_number().as_str(), "V0001");
    assert_eq!(result[1].voyage_number().as_str(), "V0003");

    // 複合条件（出発 JPOSA・到着 USLAX・冷凍対応 = V0003 のみ）
    let combined = VoyageSearchCriteria {
        origin: Some(Location::new("JPOSA").unwrap()),
        destination: Some(Location::new("USLAX").unwrap()),
        cargo_type: Some(CargoType::Refrigerated),
    };
    let result = repo.search(&combined).await.expect("search");
    assert_eq!(result.len(), 1);
    assert_eq!(result[0].voyage_number().as_str(), "V0003");
}

#[tokio::test]
async fn 検索は該当なしのとき空を返す() {
    let (pool, _c) = setup().await;
    let repo = SqlxVoyageRepository::new(pool);
    repo.save(&voyage("V0001", "JPOSA", "USLAX", vec![CargoType::General]))
        .await
        .unwrap();
    let criteria = VoyageSearchCriteria {
        origin: Some(Location::new("JPKIX").unwrap()),
        destination: None,
        cargo_type: None,
    };
    let result = repo.search(&criteria).await.expect("search");
    assert!(result.is_empty());
}
