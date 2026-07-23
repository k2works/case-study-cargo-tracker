//! ログイン・ダッシュボード・認可の HTTP レベル統合テスト（testcontainers + tower oneshot）。

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
        tracking_repo: Arc::new(infra_persistence::SqlxTrackingActivityRepository::new(
            pool.clone(),
        )),
        handling_repo: Arc::new(infra_persistence::SqlxHandlingActivityRepository::new(
            pool.clone(),
        )),
        estimate_repo: Arc::new(infra_persistence::SqlxEstimateRepository::new(pool.clone())),
        pool,
    }
}
use shared_kernel::Role;
use sqlx::PgPool;
use testcontainers::ContainerAsync;
use testcontainers::ImageExt;
use testcontainers_modules::postgres::Postgres;
use testcontainers_modules::testcontainers::runners::AsyncRunner;
use tower::ServiceExt;
use tower_sessions::{MemoryStore, SessionManagerLayer};

async fn setup() -> (Router, ContainerAsync<Postgres>) {
    let container = Postgres::default()
        .with_tag("16-alpine")
        .start()
        .await
        .expect("start postgres");
    let port = container.get_host_port_ipv4(5432).await.expect("host port");
    let url = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");
    let pool = PgPool::connect(&url).await.expect("connect");
    MIGRATOR.run(&pool).await.expect("migrate");

    let users = SqlxUserRepository::new(pool.clone());
    users
        .create_user("sales", "sales@example.com", "pass1234", &[Role::Sales])
        .await
        .expect("seed user");
    users
        .create_user(
            "handler",
            "handler@example.com",
            "pass1234",
            &[Role::Handler],
        )
        .await
        .expect("seed handler");
    users
        .create_user(
            "tracker",
            "tracker@example.com",
            "pass1234",
            &[Role::Tracker],
        )
        .await
        .expect("seed tracker");

    let session_layer = SessionManagerLayer::new(MemoryStore::default());
    let app = web_router(app_state(pool)).layer(session_layer);
    (app, container)
}

async fn body_string(resp: axum::response::Response) -> String {
    let bytes = resp.into_body().collect().await.unwrap().to_bytes();
    String::from_utf8(bytes.to_vec()).unwrap()
}

#[tokio::test]
async fn 未認証でダッシュボードにアクセスするとログインへリダイレクトされる() {
    let (app, _c) = setup().await;
    let resp = app
        .oneshot(Request::get("/").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    assert_eq!(resp.headers().get(header::LOCATION).unwrap(), "/login");
}

#[tokio::test]
async fn 正しい認証情報でログインするとダッシュボードへリダイレクトされる() {
    let (app, _c) = setup().await;
    let resp = app
        .oneshot(
            Request::post("/login")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(Body::from("username=sales&password=pass1234"))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    assert_eq!(resp.headers().get(header::LOCATION).unwrap(), "/");
    // セッション Cookie が発行される
    assert!(resp.headers().get(header::SET_COOKIE).is_some());
}

#[tokio::test]
async fn 誤った認証情報はエラー表示のまま200を返す() {
    let (app, _c) = setup().await;
    let resp = app
        .oneshot(
            Request::post("/login")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(Body::from("username=sales&password=wrong"))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = body_string(resp).await;
    assert!(html.contains("login-error"));
}

#[tokio::test]
async fn ログイン後のダッシュボードはロール別ナビを表示する() {
    let (app, _c) = setup().await;
    // ログインして Cookie を取得
    let login = app
        .clone()
        .oneshot(
            Request::post("/login")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(Body::from("username=sales&password=pass1234"))
                .unwrap(),
        )
        .await
        .unwrap();
    let cookie = login
        .headers()
        .get(header::SET_COOKIE)
        .unwrap()
        .to_str()
        .unwrap()
        .split(';')
        .next()
        .unwrap()
        .to_string();

    let resp = app
        .oneshot(
            Request::get("/")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = body_string(resp).await;
    assert!(html.contains("dashboard-title"));
    // ROLE_SALES には貨物予約・見積管理が表示され、請求管理は表示されない
    assert!(html.contains("nav-bookings"));
    assert!(html.contains("nav-estimates"));
    assert!(!html.contains("nav-billing"));
}

async fn dashboard_html_for(app: &Router, username: &str) -> String {
    let login = app
        .clone()
        .oneshot(
            Request::post("/login")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(Body::from(format!("username={username}&password=pass1234")))
                .unwrap(),
        )
        .await
        .unwrap();
    let cookie = login
        .headers()
        .get(header::SET_COOKIE)
        .unwrap()
        .to_str()
        .unwrap()
        .split(';')
        .next()
        .unwrap()
        .to_string();
    let resp = app
        .clone()
        .oneshot(
            Request::get("/")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    body_string(resp).await
}

#[tokio::test]
async fn 荷役作業員のナビは荷役管理を表示し貨物予約を表示しない() {
    let (app, _c) = setup().await;
    let html = dashboard_html_for(&app, "handler").await;
    // ROLE_HANDLER: 荷役管理あり・貨物予約/貨物追跡なし（ui_design ロール別メニュー）
    assert!(html.contains("nav-handling"));
    assert!(!html.contains("nav-bookings"));
    assert!(!html.contains("nav-tracking"));
}

#[tokio::test]
async fn 追跡管理者のナビは貨物追跡と荷役管理を表示する() {
    let (app, _c) = setup().await;
    let html = dashboard_html_for(&app, "tracker").await;
    // ROLE_TRACKER: 貨物追跡・荷役管理あり（ui_design ロール別メニュー）
    assert!(html.contains("nav-tracking"));
    assert!(html.contains("nav-handling"));
    assert!(!html.contains("nav-bookings"));
}
