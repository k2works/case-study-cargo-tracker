//! ログイン・ダッシュボード・認可の HTTP レベル統合テスト（testcontainers + tower oneshot）。

use axum::Router;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use http_body_util::BodyExt;
use infra_persistence::{MIGRATOR, SqlxUserRepository};
use interface_web::{AppState, web_router};
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

    let session_layer = SessionManagerLayer::new(MemoryStore::default());
    let app = web_router(AppState { pool }).layer(session_layer);
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
