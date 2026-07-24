//! 航路管理フロー（US24/US25/US07）の HTTP レベル統合テスト（testcontainers + tower oneshot）。

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
        charge_repo: Arc::new(infra_persistence::SqlxFreightChargeRepository::new(
            pool.clone(),
        )),
        invoice_repo: Arc::new(infra_persistence::SqlxInvoiceRepository::new(pool.clone())),
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
async fn 寄港地経由の複数区間で航海を登録し一覧に両港が表示される() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app, "user").await;
    // JPOSA → SGSIN → USLAX の 2 区間（時系列順）
    let body = "voyage_number=V0070&vessel_name=SAKURA&carrier=NipponLine&cargo_general=on\
                &leg1_departure=JPOSA&leg1_arrival=SGSIN\
                &leg1_departure_time=2026-04-01T18%3A00&leg1_arrival_time=2026-04-07T08%3A00\
                &leg2_departure=SGSIN&leg2_arrival=USLAX\
                &leg2_departure_time=2026-04-08T10%3A00&leg2_arrival_time=2026-04-20T08%3A00"
        .to_string();
    let resp = post_voyage(&app, &cookie, "/voyages", body).await;
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
    // 一覧は全体の出発地（JPOSA）と到着地（USLAX）を表示する
    assert!(html.contains("JPOSA"));
    assert!(html.contains("USLAX"));
}

#[tokio::test]
async fn 区間跨ぎの時系列が逆転した登録は422になる() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app, "user").await;
    // leg1 到着(04-20) > leg2 出発(04-08) で逆転
    let body = "voyage_number=V0071&vessel_name=SAKURA&carrier=NipponLine&cargo_general=on\
                &leg1_departure=JPOSA&leg1_arrival=SGSIN\
                &leg1_departure_time=2026-04-01T18%3A00&leg1_arrival_time=2026-04-20T08%3A00\
                &leg2_departure=SGSIN&leg2_arrival=USLAX\
                &leg2_departure_time=2026-04-08T10%3A00&leg2_arrival_time=2026-04-25T08%3A00"
        .to_string();
    let resp = post_voyage(&app, &cookie, "/voyages", body).await;
    assert_eq!(resp.status(), StatusCode::UNPROCESSABLE_ENTITY);
    let html = body_string(resp).await;
    assert!(html.contains("voyage-error"));
}

#[tokio::test]
async fn 出発が到着より後の登録は422でエラーを示す() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app, "user").await;
    // leg1 の出発(04-14) > 到着(04-01)
    let body = "voyage_number=V0072&vessel_name=SAKURA&carrier=NipponLine&cargo_general=on\
                &leg1_departure=JPOSA&leg1_arrival=USLAX\
                &leg1_departure_time=2026-04-14T08%3A00&leg1_arrival_time=2026-04-01T18%3A00"
        .to_string();
    let resp = post_voyage(&app, &cookie, "/voyages", body).await;
    assert_eq!(resp.status(), StatusCode::UNPROCESSABLE_ENTITY);
    let html = body_string(resp).await;
    assert!(html.contains("voyage-error"));
}

#[tokio::test]
async fn 検索で該当なしのとき空メッセージが表示される() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app, "user").await;
    post_voyage(
        &app,
        &cookie,
        "/voyages",
        voyage_body("V0080", "JPOSA", "USLAX", "cargo_general=on"),
    )
    .await;
    // 一致しない出発港で検索 → 0 件
    let resp = app
        .oneshot(
            Request::get("/voyages?origin=JPKIX")
                .header(header::COOKIE, cookie)
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    let html = body_string(resp).await;
    assert!(html.contains("voyage-empty"));
    assert!(html.contains("該当する航海がありません"));
}

#[tokio::test]
async fn 更新画面を開いてもキャンセルなら既存は変わらない() {
    let (app, _c) = setup(Role::RouteDesigner).await;
    let cookie = login(&app, "user").await;
    post_voyage(
        &app,
        &cookie,
        "/voyages",
        voyage_body("V0090", "JPOSA", "USLAX", "cargo_general=on"),
    )
    .await;
    // 更新フォームを開く（GET のみ・副作用なし = キャンセル相当）
    let resp = app
        .clone()
        .oneshot(
            Request::get("/voyages/V0090/edit")
                .header(header::COOKIE, cookie.clone())
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = body_string(resp).await;
    assert!(html.contains("voyage-current")); // 現在の登録内容カード

    // 一覧は元の内容のまま（SAKURA / JPOSA）
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
    assert!(html.contains("V0090"));
    assert!(html.contains("SAKURA"));
    assert!(html.contains("JPOSA"));
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
