//! 経路設計・割り当てフロー（US08/US09）の HTTP レベル統合テスト。

use axum::Router;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use http_body_util::BodyExt;
use infra_persistence::{
    MIGRATOR, SqlxCargoRepository, SqlxCargoSpecProvider, SqlxSelectedRouteRepository,
    SqlxShipperExistenceChecker, SqlxShipperRepository, SqlxUserRepository, SqlxVoyageRepository,
};
use interface_web::{AppState, web_router};
use std::sync::Arc;

use chrono::{DateTime, Utc};
use domain_routing::{
    CargoType as RCargoType, Carrier, CarrierMovement, Schedule, VesselName, Voyage, VoyageNumber,
    VoyageRepository,
};
use shared_kernel::Role;
use sqlx::PgPool;
use testcontainers::ContainerAsync;
use testcontainers::ImageExt;
use testcontainers_modules::postgres::Postgres;
use testcontainers_modules::testcontainers::runners::AsyncRunner;
use tower::ServiceExt;
use tower_sessions::{MemoryStore, SessionManagerLayer};

fn app_state(pool: PgPool) -> AppState {
    AppState {
        shipper_repo: Arc::new(SqlxShipperRepository::new(pool.clone())),
        cargo_repo: Arc::new(SqlxCargoRepository::new(pool.clone())),
        shipper_checker: Arc::new(SqlxShipperExistenceChecker::new(pool.clone())),
        voyage_repo: Arc::new(SqlxVoyageRepository::new(pool.clone())),
        cargo_spec_provider: Arc::new(SqlxCargoSpecProvider::new(pool.clone())),
        selected_route_repo: Arc::new(SqlxSelectedRouteRepository::new(pool.clone())),
        notification_port: Arc::new(infra_persistence::SqlxNotificationRepository::new(
            pool.clone(),
        )),
        selected_route_view: Arc::new(infra_persistence::SqlxSelectedRouteView::new(pool.clone())),
        pool,
    }
}

fn dt(s: &str) -> DateTime<Utc> {
    DateTime::parse_from_rfc3339(s).unwrap().with_timezone(&Utc)
}

/// 直行航海 JPOSA→USLAX（GENERAL）を seed する。
async fn seed_voyage(pool: &PgPool) {
    let leg = CarrierMovement::new(
        shared_kernel::Location::new("JPOSA").unwrap(),
        shared_kernel::Location::new("USLAX").unwrap(),
        dt("2026-04-01T18:00:00Z"),
        dt("2026-04-14T08:00:00Z"),
    )
    .unwrap();
    let voyage = Voyage::register(
        VoyageNumber::new("V0001").unwrap(),
        VesselName::new("SAKURA MARU").unwrap(),
        Carrier::new("Nippon Line").unwrap(),
        vec![RCargoType::General],
        Schedule::new(vec![leg]).unwrap(),
    );
    SqlxVoyageRepository::new(pool.clone())
        .save(&voyage)
        .await
        .expect("seed voyage");
}

/// cargo 行を SQL で直接 seed し、既知の booking_id を返す（SqlxCargoSpecProvider の検証）。
async fn seed_cargo(pool: &PgPool, booking_id: &str) {
    let shipper_id = uuid::Uuid::new_v4();
    sqlx::query(
        r"INSERT INTO shipper (id, shipper_code, shipper_type, name, email)
          VALUES ($1, $2, 'INDIVIDUAL', '荷主', $3)",
    )
    .bind(shipper_id)
    .bind(format!("SHP-{}", &shipper_id.simple().to_string()[..8]))
    .bind(format!("{booking_id}@example.com"))
    .execute(pool)
    .await
    .expect("seed shipper");

    sqlx::query(
        r"INSERT INTO cargo
            (booking_id, shipper_id, cargo_type, weight, origin_unlocode,
             destination_unlocode, arrival_deadline, consignee_name, consignee_email, booking_status)
          VALUES ($1, $2, 'GENERAL', 1000, 'JPOSA', 'USLAX', DATE '2026-04-20',
                  'LA Trading', 'consignee@example.com', 'ROUTE_DESIGNING')",
    )
    .bind(booking_id)
    .bind(shipper_id)
    .execute(pool)
    .await
    .expect("seed cargo");
}

/// 目的地に到達できない cargo（USLAX 行きに対し目的地 DEHAM）を seed する。
async fn seed_cargo_unreachable(pool: &PgPool, booking_id: &str) {
    let shipper_id = uuid::Uuid::new_v4();
    sqlx::query(
        r"INSERT INTO shipper (id, shipper_code, shipper_type, name, email)
          VALUES ($1, $2, 'INDIVIDUAL', '荷主', $3)",
    )
    .bind(shipper_id)
    .bind(format!("SHP-{}", &shipper_id.simple().to_string()[..8]))
    .bind(format!("{booking_id}@example.com"))
    .execute(pool)
    .await
    .expect("seed shipper");

    sqlx::query(
        r"INSERT INTO cargo
            (booking_id, shipper_id, cargo_type, weight, origin_unlocode,
             destination_unlocode, arrival_deadline, consignee_name, consignee_email, booking_status)
          VALUES ($1, $2, 'GENERAL', 1000, 'JPOSA', 'DEHAM', DATE '2026-04-20',
                  'Hamburg Trading', 'consignee@example.com', 'PRELIMINARY')",
    )
    .bind(booking_id)
    .bind(shipper_id)
    .execute(pool)
    .await
    .expect("seed cargo");
}

async fn setup(role: Role) -> (Router, ContainerAsync<Postgres>) {
    setup_with(role, true).await
}

/// `seed_matching_voyage` が false の場合は cargo の目的地に到達できない状態を作る（経路 0 件）。
async fn setup_with(role: Role, seed_matching_voyage: bool) -> (Router, ContainerAsync<Postgres>) {
    let container = Postgres::default()
        .with_tag("16-alpine")
        .start()
        .await
        .expect("start postgres");
    let port = container.get_host_port_ipv4(5432).await.expect("host port");
    let url = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");
    let pool = PgPool::connect(&url).await.expect("connect");
    MIGRATOR.run(&pool).await.expect("migrate");
    SqlxUserRepository::new(pool.clone())
        .create_user("user", "user@example.com", "pass1234", &[role])
        .await
        .expect("seed user");
    seed_voyage(&pool).await;
    if seed_matching_voyage {
        seed_cargo(&pool, "BKG-0001").await;
    } else {
        seed_cargo_unreachable(&pool, "BKG-0001").await;
    }
    let app = web_router(app_state(pool)).layer(SessionManagerLayer::new(MemoryStore::default()));
    (app, container)
}

async fn login(app: &Router) -> String {
    let resp = app
        .clone()
        .oneshot(
            Request::post("/login")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(Body::from("username=user&password=pass1234"))
                .unwrap(),
        )
        .await
        .unwrap();
    resp.headers()
        .get(header::SET_COOKIE)
        .unwrap()
        .to_str()
        .unwrap()
        .split(';')
        .next()
        .unwrap()
        .to_string()
}

async fn body_string(resp: axum::response::Response) -> String {
    String::from_utf8(
        resp.into_body()
            .collect()
            .await
            .unwrap()
            .to_bytes()
            .to_vec(),
    )
    .unwrap()
}

#[tokio::test]
async fn 経路設計画面は貨物仕様と経路候補を表示する() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app).await;
    let resp = app
        .oneshot(
            Request::get("/bookings/BKG-0001/route")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = body_string(resp).await;
    assert!(html.contains("cargo-spec"));
    assert!(html.contains("JPOSA"));
    assert!(html.contains("USLAX"));
    assert!(html.contains("route-candidates"));
    assert!(html.contains("V0001"));
}

#[tokio::test]
async fn 経路を選択して確定すると予約詳細へリダイレクトする() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app).await;
    let resp = app
        .oneshot(
            Request::post("/bookings/BKG-0001/route/confirm")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from("candidate_index=0"))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    assert_eq!(
        resp.headers().get(header::LOCATION).unwrap(),
        "/bookings/BKG-0001"
    );
}

#[tokio::test]
async fn 経路設計画面は条件調整パネルを表示する() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app).await;
    let resp = app
        .oneshot(
            Request::get("/bookings/BKG-0001/route")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    let html = body_string(resp).await;
    assert!(html.contains("route-adjust"));
    assert!(html.contains("extend_deadline_days"));
}

#[tokio::test]
async fn 条件調整で経路候補が再算出される() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app).await;
    // 期限を 5 日延長して再算出。候補が再提示される（US10 受入 2/3）。
    let resp = app
        .oneshot(
            Request::post("/bookings/BKG-0001/route/adjust")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from("extend_deadline_days=5&cargo_type="))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = body_string(resp).await;
    assert!(html.contains("route-candidates"));
    assert!(html.contains("V0001"));
}

#[tokio::test]
async fn 貨物種別を変更して再算出すると対応航海が絞り込まれる() {
    // BKG-0001 は GENERAL。HAZARDOUS に変更すると対応航海（V0001 は GENERAL のみ）が無く 0 件。
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app).await;
    let resp = app
        .oneshot(
            Request::post("/bookings/BKG-0001/route/adjust")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from("extend_deadline_days=0&cargo_type=HAZARDOUS"))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = body_string(resp).await;
    assert!(html.contains("route-empty"));
}

#[tokio::test]
async fn 存在しない予約の経路設計は404を返す() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app).await;
    let resp = app
        .oneshot(
            Request::get("/bookings/BKG-UNKNOWN/route")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn 期限内に到達可能な経路がない場合は0件通知を表示する() {
    let (app, _c) = setup_with(Role::RouteDesigner, false).await;
    let cookie = login(&app).await;
    let resp = app
        .oneshot(
            Request::get("/bookings/BKG-0001/route")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = body_string(resp).await;
    assert!(html.contains("route-empty"));
    assert!(!html.contains("route-candidates"));
}

#[tokio::test]
async fn 範囲外の候補を確定しようとすると422を返す() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app).await;
    let resp = app
        .oneshot(
            Request::post("/bookings/BKG-0001/route/confirm")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from("candidate_index=99"))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::UNPROCESSABLE_ENTITY);
}

#[tokio::test]
async fn 権限のないロールは経路設計画面にアクセスできない() {
    let (app, _c) = setup(Role::Sales).await;
    let cookie = login(&app).await;
    let resp = app
        .oneshot(
            Request::get("/bookings/BKG-0001/route")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::FORBIDDEN);
}
