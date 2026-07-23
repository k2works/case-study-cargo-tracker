//! 荷主登録フローの HTTP レベル統合テスト（testcontainers + tower oneshot）。

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

async fn setup(role: Role) -> (Router, ContainerAsync<Postgres>) {
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
        .expect("seed");
    let app = web_router(app_state(pool)).layer(SessionManagerLayer::new(MemoryStore::default()));
    (app, container)
}

async fn login(app: &Router, user: &str) -> String {
    let resp = app
        .clone()
        .oneshot(
            Request::post("/login")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(Body::from(format!("username={user}&password=pass1234")))
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

#[tokio::test]
async fn 営業担当者は荷主を登録すると予約登録へリダイレクトされる() {
    let (app, _c) = setup(Role::Sales).await;
    let cookie = login(&app, "user").await;
    let resp = app
        .oneshot(
            Request::post("/shippers")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from(
                    "kind=INDIVIDUAL&name=山田商事&email=yamada@example.com",
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    assert_eq!(
        resp.headers().get(header::LOCATION).unwrap(),
        "/bookings/new"
    );
}

#[tokio::test]
async fn メール重複時はエラーメッセージを表示する() {
    let (app, _c) = setup(Role::Sales).await;
    let cookie = login(&app, "user").await;
    let body = "kind=INDIVIDUAL&name=山田商事&email=dup@example.com";
    // 1 回目は成功
    app.clone()
        .oneshot(
            Request::post("/shippers")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie.clone())
                .body(Body::from(body))
                .unwrap(),
        )
        .await
        .unwrap();
    // 2 回目は重複エラー
    let resp = app
        .oneshot(
            Request::post("/shippers")
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
    assert!(html.contains("shipper-error"));
}

#[tokio::test]
async fn 法人荷主を契約情報付きで登録できる() {
    let (app, _c) = setup(Role::Sales).await;
    let cookie = login(&app, "user").await;
    let body = "kind=CORPORATE&name=グローバル物流&email=corp@example.com\
                &contract_number=CT-2026-001&discount_rate=0.1000";
    let resp = app
        .oneshot(
            Request::post("/shippers")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from(body))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    assert_eq!(
        resp.headers().get(header::LOCATION).unwrap(),
        "/bookings/new"
    );
}

#[tokio::test]
async fn 権限のないロールは荷主登録フォームにアクセスできない() {
    let (app, _c) = setup(Role::Handler).await;
    let cookie = login(&app, "user").await;
    let resp = app
        .oneshot(
            Request::get("/shippers/new")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::FORBIDDEN);
}
