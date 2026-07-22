//! 航路管理フロー（US24/US25/US07）の HTTP レベル統合テスト（testcontainers + tower oneshot）。

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
    let app = web_router(AppState { pool }).layer(SessionManagerLayer::new(MemoryStore::default()));
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

/// V0042 の登録用フォームボディ（cargo_types・区間 1 のみ）。
fn voyage_body(number: &str, origin: &str, dest: &str, cargo_param: &str) -> String {
    format!(
        "voyage_number={number}&vessel_name=SAKURA&carrier=NipponLine&{cargo_param}\
         &leg1_departure={origin}&leg1_arrival={dest}\
         &leg1_departure_time=2026-04-01T18%3A00&leg1_arrival_time=2026-04-14T08%3A00"
    )
}

async fn post_voyage(
    app: &Router,
    cookie: &str,
    path: &str,
    body: String,
) -> axum::response::Response {
    app.clone()
        .oneshot(
            Request::post(path)
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from(body))
                .unwrap(),
        )
        .await
        .unwrap()
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
async fn 経路設計者は航海を登録すると一覧へリダイレクトされる() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app, "user").await;
    let resp = post_voyage(
        &app,
        &cookie,
        "/voyages",
        voyage_body("V0042", "JPOSA", "USLAX", "cargo_general=on"),
    )
    .await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    assert!(
        resp.headers()
            .get(header::LOCATION)
            .unwrap()
            .to_str()
            .unwrap()
            .starts_with("/voyages")
    );
}

#[tokio::test]
async fn 登録した航海は一覧に表示される() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app, "user").await;
    post_voyage(
        &app,
        &cookie,
        "/voyages",
        voyage_body("V0042", "JPOSA", "USLAX", "cargo_general=on"),
    )
    .await;
    let resp = app
        .oneshot(
            Request::get("/voyages")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = body_string(resp).await;
    assert!(html.contains("V0042"));
    assert!(html.contains("SAKURA"));
}

#[tokio::test]
async fn 同一航海番号の登録は422エラーになる() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app, "user").await;
    let body = voyage_body("V0100", "JPOSA", "USLAX", "cargo_general=on");
    post_voyage(&app, &cookie, "/voyages", body.clone()).await;
    let resp = post_voyage(&app, &cookie, "/voyages", body).await;
    assert_eq!(resp.status(), StatusCode::UNPROCESSABLE_ENTITY);
    let html = body_string(resp).await;
    assert!(html.contains("voyage-error"));
}

#[tokio::test]
async fn 権限のないロールは航路登録フォームにアクセスできない() {
    let (app, _c) = setup(Role::Handler).await;
    let cookie = login(&app, "user").await;
    let resp = app
        .oneshot(
            Request::get("/voyages/new")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn 貨物種別で航海を検索できる() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app, "user").await;
    post_voyage(
        &app,
        &cookie,
        "/voyages",
        voyage_body("V0001", "JPOSA", "USLAX", "cargo_general=on"),
    )
    .await;
    post_voyage(
        &app,
        &cookie,
        "/voyages",
        voyage_body("V0002", "JPYOK", "DEHAM", "cargo_hazardous=on"),
    )
    .await;

    let resp = app
        .oneshot(
            Request::get("/voyages?cargo_type=HAZARDOUS")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    let html = body_string(resp).await;
    assert!(html.contains("V0002"));
    assert!(!html.contains("V0001"));
}

#[tokio::test]
async fn 既存航海を更新すると内容が反映される() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app, "user").await;
    post_voyage(
        &app,
        &cookie,
        "/voyages",
        voyage_body("V0055", "JPOSA", "USLAX", "cargo_general=on"),
    )
    .await;

    // 船名と経路を変えて更新
    let update = "voyage_number=V0055&vessel_name=FUJI&carrier=OceanCorp&cargo_refrigerated=on\
         &leg1_departure=JPYOK&leg1_arrival=DEHAM\
         &leg1_departure_time=2026-05-01T18%3A00&leg1_arrival_time=2026-05-20T08%3A00"
        .to_string();
    let resp = post_voyage(&app, &cookie, "/voyages/V0055", update).await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);

    let resp = app
        .oneshot(
            Request::get("/voyages")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    let html = body_string(resp).await;
    assert!(html.contains("FUJI"));
    assert!(html.contains("JPYOK"));
}
