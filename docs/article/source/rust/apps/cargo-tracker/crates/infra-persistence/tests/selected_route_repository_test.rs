//! `SqlxSelectedRouteRepository` の統合テスト（testcontainers + 実 PostgreSQL 16）。

use chrono::{DateTime, Utc};
use domain_routing::{
    CargoType, Carrier, CarrierMovement, RouteCandidate, RouteLeg, Schedule,
    SelectedRouteRepository, VesselName, Voyage, VoyageNumber,
};
use infra_persistence::{MIGRATOR, SqlxSelectedRouteRepository};
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
    DateTime::parse_from_rfc3339(s).unwrap().with_timezone(&Utc)
}

/// JPOSA→USLAX の直行経路候補を作る。
fn candidate() -> RouteCandidate {
    let leg = CarrierMovement::new(
        Location::new("JPOSA").unwrap(),
        Location::new("USLAX").unwrap(),
        dt("2026-04-01T18:00:00Z"),
        dt("2026-04-14T08:00:00Z"),
    )
    .unwrap();
    let voyage = Voyage::register(
        VoyageNumber::new("V0001").unwrap(),
        VesselName::new("SAKURA").unwrap(),
        Carrier::new("Nippon").unwrap(),
        vec![CargoType::General],
        Schedule::new(vec![leg]).unwrap(),
    );
    RouteCandidate::new(vec![RouteLeg::from_voyage(&voyage)]).unwrap()
}

#[tokio::test]
async fn 確定経路を保存して存在確認できる() {
    let (pool, _c) = setup().await;
    let repo = SqlxSelectedRouteRepository::new(pool);
    assert!(!repo.exists("BKG-0001").await.unwrap());

    repo.save("BKG-0001", &candidate()).await.expect("save");
    assert!(repo.exists("BKG-0001").await.unwrap());
}

#[tokio::test]
async fn 同一予約の再確定は上書きされ区間が重複しない() {
    let (pool, _c) = setup().await;
    let repo = SqlxSelectedRouteRepository::new(pool.clone());
    repo.save("BKG-0002", &candidate()).await.expect("save1");
    repo.save("BKG-0002", &candidate()).await.expect("save2");

    // 区間は洗い替えされ 1 区間のみ（重複しない）
    let count: i64 =
        sqlx::query_scalar("SELECT COUNT(*) FROM selected_route_leg srl JOIN selected_route sr ON srl.selected_route_id = sr.id WHERE sr.booking_id = $1")
            .bind("BKG-0002")
            .fetch_one(&pool)
            .await
            .unwrap();
    assert_eq!(count, 1);
}
