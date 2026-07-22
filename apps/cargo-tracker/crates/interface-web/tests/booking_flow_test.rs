//! 貨物予約登録フローの HTTP レベル統合テスト（testcontainers + tower oneshot）。

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

/// composition root 相当のテスト用 AppState 構築（ADR-0003）。
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
use shared_kernel::{Role, ShipperId};
use sqlx::PgPool;
use testcontainers::ContainerAsync;
use testcontainers::ImageExt;
use testcontainers_modules::postgres::Postgres;
use testcontainers_modules::testcontainers::runners::AsyncRunner;
use tower::ServiceExt;
use tower_sessions::{MemoryStore, SessionManagerLayer};

use domain_shipper::{Email, Shipper, ShipperKind, ShipperName, ShipperRepository};

async fn setup() -> (Router, ShipperId, ContainerAsync<Postgres>) {
    let (app, shipper_id, _pool, container) = setup_full().await;
    (app, shipper_id, container)
}

async fn setup_full() -> (Router, ShipperId, PgPool, ContainerAsync<Postgres>) {
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
        .create_user("sales", "sales@example.com", "pass1234", &[Role::Sales])
        .await
        .expect("seed user");
    let shipper = Shipper::register(
        ShipperName::new("山田商事").unwrap(),
        Email::new("yamada@example.com").unwrap(),
        None,
        None,
        ShipperKind::Individual,
    );
    let shipper_id = shipper.id();
    SqlxShipperRepository::new(pool.clone())
        .save(&shipper)
        .await
        .expect("seed shipper");
    let app =
        web_router(app_state(pool.clone())).layer(SessionManagerLayer::new(MemoryStore::default()));
    (app, shipper_id, pool, container)
}

async fn login(app: &Router) -> String {
    let resp = app
        .clone()
        .oneshot(
            Request::post("/login")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(Body::from("username=sales&password=pass1234"))
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

fn form_body(shipper_id: &ShipperId) -> String {
    format!(
        "shipper_id={sid}&consignee_name=LA+Trading&consignee_contact=contact%40la.example\
         &origin=JPOSA&destination=USLAX&arrival_deadline=2026-04-15&cargo_type=GENERAL&weight=1200",
        sid = shipper_id
    )
}

#[tokio::test]
async fn 既存荷主で予約を登録すると予約詳細へリダイレクトされる() {
    let (app, shipper_id, _c) = setup().await;
    let cookie = login(&app).await;
    let resp = app
        .oneshot(
            Request::post("/bookings")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from(form_body(&shipper_id)))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    let location = resp
        .headers()
        .get(header::LOCATION)
        .unwrap()
        .to_str()
        .unwrap();
    assert!(location.starts_with("/bookings/BKG-"));
}

#[tokio::test]
async fn 存在しない荷主では予約登録がエラーになる() {
    let (app, _shipper_id, _c) = setup().await;
    let cookie = login(&app).await;
    let missing = ShipperId::generate();
    let resp = app
        .oneshot(
            Request::post("/bookings")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from(form_body(&missing)))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = String::from_utf8(
        resp.into_body()
            .collect()
            .await
            .unwrap()
            .to_bytes()
            .to_vec(),
    )
    .unwrap();
    assert!(html.contains("booking-error"));
}

#[tokio::test]
async fn 危険物貨物を申告情報付きで登録できる() {
    let (app, shipper_id, _c) = setup().await;
    let cookie = login(&app).await;
    let body = format!(
        "shipper_id={sid}&consignee_name=LA+Trading&consignee_contact=contact%40la.example\
         &origin=JPOSA&destination=USLAX&arrival_deadline=2026-04-15&cargo_type=HAZARDOUS&weight=800\
         &hazardous_class=3&un_number=1203&proper_shipping_name=GASOLINE",
        sid = shipper_id
    );
    let resp = app
        .oneshot(
            Request::post("/bookings")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from(body))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
}

#[tokio::test]
async fn 危険物貨物で申告情報が欠落するとエラーになる() {
    let (app, shipper_id, _c) = setup().await;
    let cookie = login(&app).await;
    // hazardous_class 等を欠落させる
    let body = format!(
        "shipper_id={sid}&consignee_name=LA+Trading&consignee_contact=contact%40la.example\
         &origin=JPOSA&destination=USLAX&arrival_deadline=2026-04-15&cargo_type=HAZARDOUS&weight=800",
        sid = shipper_id
    );
    let resp = app
        .oneshot(
            Request::post("/bookings")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from(body))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = String::from_utf8(
        resp.into_body()
            .collect()
            .await
            .unwrap()
            .to_bytes()
            .to_vec(),
    )
    .unwrap();
    assert!(html.contains("booking-error"));
}

#[tokio::test]
async fn 冷凍貨物を温度条件付きで登録できる() {
    let (app, shipper_id, _c) = setup().await;
    let cookie = login(&app).await;
    let body = format!(
        "shipper_id={sid}&consignee_name=LA+Trading&consignee_contact=contact%40la.example\
         &origin=JPOSA&destination=USLAX&arrival_deadline=2026-04-15&cargo_type=REFRIGERATED&weight=500\
         &min_temperature=-20&max_temperature=-5&temperature_unit=CELSIUS",
        sid = shipper_id
    );
    let resp = app
        .oneshot(
            Request::post("/bookings")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from(body))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
}

/// 指定状態の cargo を SQL で直接 seed する。
async fn seed_cargo_with_status(
    pool: &PgPool,
    shipper_id: &ShipperId,
    booking_id: &str,
    status: &str,
) {
    sqlx::query(
        r"INSERT INTO cargo
            (booking_id, shipper_id, cargo_type, weight, origin_unlocode,
             destination_unlocode, arrival_deadline, consignee_name, consignee_email, booking_status)
          VALUES ($1, $2, 'GENERAL', 1000, 'JPOSA', 'DEHAM', DATE '2026-06-05',
                  'Hamburg Handels', 'import@hamburg.example', $3)",
    )
    .bind(booking_id)
    .bind(shipper_id.value())
    .bind(status)
    .execute(pool)
    .await
    .expect("seed cargo");
}

/// 確定経路（2 区間）を SQL で直接 seed する。
async fn seed_selected_route(pool: &PgPool, booking_id: &str) {
    let route_id: i64 = sqlx::query_scalar(
        r"INSERT INTO selected_route (booking_id, status) VALUES ($1, 'SELECTED') RETURNING id",
    )
    .bind(booking_id)
    .fetch_one(pool)
    .await
    .expect("seed selected_route");
    sqlx::query(
        r"INSERT INTO selected_route_leg
            (selected_route_id, voyage_number, load_location_unlocode, unload_location_unlocode,
             load_time, unload_time, seq_number)
          VALUES
            ($1, 'V0002', 'JPOSA', 'SGSIN', TIMESTAMPTZ '2026-05-02 09:00:00+09', TIMESTAMPTZ '2026-05-09 18:00:00+08', 1),
            ($1, 'V0003', 'SGSIN', 'DEHAM', TIMESTAMPTZ '2026-05-11 10:00:00+08', TIMESTAMPTZ '2026-05-30 16:00:00+02', 2)",
    )
    .bind(route_id)
    .execute(pool)
    .await
    .expect("seed selected_route_leg");
}

async fn post(app: &Router, path: &str, cookie: &str) -> axum::response::Response {
    app.clone()
        .oneshot(
            Request::post(path)
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap()
}

async fn get_html(app: &Router, path: &str, cookie: &str) -> String {
    let resp = app
        .clone()
        .oneshot(
            Request::get(path)
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
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
async fn 経路設計依頼で予約が経路設計中になる() {
    let (app, shipper_id, _c) = setup().await;
    let cookie = login(&app).await;
    // 仮受付予約を作成。
    let create = app
        .clone()
        .oneshot(
            Request::post("/bookings")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie.clone())
                .body(Body::from(form_body(&shipper_id)))
                .unwrap(),
        )
        .await
        .unwrap();
    let location = create
        .headers()
        .get(header::LOCATION)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();

    let resp = post(&app, &format!("{location}/assign-routing"), &cookie).await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);

    let html = get_html(&app, &location, &cookie).await;
    assert!(html.contains(r#"data-status="ROUTE_DESIGNING""#));
}

#[tokio::test]
async fn 経路提案中の予約は荷主通知と確定ができる() {
    let (app, shipper_id, pool, _c) = setup_full().await;
    let cookie = login(&app).await;
    // 経路提案中の予約＋確定経路（2 区間）を seed する。
    seed_cargo_with_status(&pool, &shipper_id, "BKG-2001", "ROUTE_PROPOSED").await;
    seed_selected_route(&pool, "BKG-2001").await;

    // 詳細に選択ルートと確定ボタンが表示される。
    let html = get_html(&app, "/bookings/BKG-2001", &cookie).await;
    assert!(html.contains("selected-route"));
    assert!(html.contains("DEHAM"));
    assert!(html.contains("confirm-btn"));

    // US12: 荷主通知（通知が記録される）。
    let notify = post(&app, "/bookings/BKG-2001/notify-route", &cookie).await;
    assert_eq!(notify.status(), StatusCode::SEE_OTHER);
    let count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM notification WHERE booking_id = 'BKG-2001' AND notification_type = 'ROUTE_NOTIFIED_TO_SHIPPER'",
    )
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(count, 1);

    // US13: 予約確定（ROUTE_PROPOSED → CONFIRMED）。
    let confirm = post(&app, "/bookings/BKG-2001/confirm", &cookie).await;
    assert_eq!(confirm.status(), StatusCode::SEE_OTHER);
    let html = get_html(&app, "/bookings/BKG-2001", &cookie).await;
    assert!(html.contains(r#"data-status="CONFIRMED""#));
}

#[tokio::test]
async fn 仮受付の予約は確定できず422になる() {
    let (app, shipper_id, pool, _c) = setup_full().await;
    let cookie = login(&app).await;
    seed_cargo_with_status(&pool, &shipper_id, "BKG-2002", "PRELIMINARY").await;
    // 仮受付から直接確定は不正遷移。
    let resp = post(&app, "/bookings/BKG-2002/confirm", &cookie).await;
    assert_eq!(resp.status(), StatusCode::UNPROCESSABLE_ENTITY);
}

#[tokio::test]
async fn 経路提案中の予約はキャンセルできる() {
    let (app, shipper_id, pool, _c) = setup_full().await;
    let cookie = login(&app).await;
    seed_cargo_with_status(&pool, &shipper_id, "BKG-2003", "ROUTE_PROPOSED").await;
    let resp = post(&app, "/bookings/BKG-2003/cancel", &cookie).await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    let html = get_html(&app, "/bookings/BKG-2003", &cookie).await;
    assert!(html.contains(r#"data-status="CANCELLED""#));
    let count: i64 = sqlx::query_scalar(
        "SELECT COUNT(*) FROM notification WHERE booking_id = 'BKG-2003' AND notification_type = 'BOOKING_CANCELLED'",
    )
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(count, 1);
}

#[tokio::test]
async fn 登録した予約は詳細画面で参照できる() {
    let (app, shipper_id, _c) = setup().await;
    let cookie = login(&app).await;
    let create = app
        .clone()
        .oneshot(
            Request::post("/bookings")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie.clone())
                .body(Body::from(form_body(&shipper_id)))
                .unwrap(),
        )
        .await
        .unwrap();
    let location = create
        .headers()
        .get(header::LOCATION)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();

    let resp = app
        .oneshot(
            Request::get(&location)
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = String::from_utf8(
        resp.into_body()
            .collect()
            .await
            .unwrap()
            .to_bytes()
            .to_vec(),
    )
    .unwrap();
    assert!(html.contains("PRELIMINARY"));
    assert!(html.contains("JPOSA"));
}
