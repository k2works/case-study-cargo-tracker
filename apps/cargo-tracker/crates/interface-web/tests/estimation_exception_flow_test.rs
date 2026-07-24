//! IT6: 見積・公開照会・遅延例外の HTTP フロー統合テスト（US01/US18/US19）。

use axum::Router;
use axum::body::Body;
use axum::http::{StatusCode, header};
use http_body_util::BodyExt;
use infra_persistence::{
    MIGRATOR, SqlxCargoRepository, SqlxCargoSpecProvider, SqlxEstimateRepository,
    SqlxFreightChargeRepository, SqlxHandlingActivityRepository, SqlxNotificationRepository,
    SqlxSelectedRouteRepository, SqlxSelectedRouteView, SqlxShipperExistenceChecker,
    SqlxShipperRepository, SqlxTrackingActivityRepository, SqlxUserRepository,
    SqlxVoyageRepository,
};
use interface_web::{AppState, web_router};
use shared_kernel::Role;
use sqlx::{PgPool, Row};
use std::sync::Arc;
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
        notification_port: Arc::new(SqlxNotificationRepository::new(pool.clone())),
        selected_route_view: Arc::new(SqlxSelectedRouteView::new(pool.clone())),
        tracking_repo: Arc::new(SqlxTrackingActivityRepository::new(pool.clone())),
        handling_repo: Arc::new(SqlxHandlingActivityRepository::new(pool.clone())),
        estimate_repo: Arc::new(SqlxEstimateRepository::new(pool.clone())),
        charge_repo: Arc::new(SqlxFreightChargeRepository::new(pool.clone())),
        pool,
    }
}

async fn setup() -> (Router, PgPool, ContainerAsync<Postgres>) {
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
        .create_user("sales", "s@example.com", "pass1234", &[Role::Sales])
        .await
        .expect("seed sales");
    users
        .create_user("tracker", "t@example.com", "pass1234", &[Role::Tracker])
        .await
        .expect("seed tracker");

    // 直行航海（JPOSA→USLAX・GENERAL・到着 2026-05-14）を seed。
    let voyage_id: i64 = sqlx::query_scalar(
        r"INSERT INTO voyage (voyage_number, vessel_name, carrier)
          VALUES ('V0001', 'SAKURA MARU', 'Nippon Line') RETURNING id",
    )
    .fetch_one(&pool)
    .await
    .expect("seed voyage");
    sqlx::query(
        r"INSERT INTO carrier_movement
            (voyage_id, departure_location_unlocode, arrival_location_unlocode,
             departure_date, arrival_date, seq_number)
          VALUES ($1, 'JPOSA', 'USLAX', TIMESTAMPTZ '2026-05-01 18:00:00+09',
                  TIMESTAMPTZ '2026-05-14 08:00:00-07', 1)",
    )
    .bind(voyage_id)
    .execute(&pool)
    .await
    .expect("seed movement");
    sqlx::query("INSERT INTO voyage_cargo_type (voyage_id, cargo_type) VALUES ($1, 'GENERAL')")
        .bind(voyage_id)
        .execute(&pool)
        .await
        .expect("seed voyage_cargo_type");

    let app =
        web_router(app_state(pool.clone())).layer(SessionManagerLayer::new(MemoryStore::default()));
    (app, pool, container)
}

async fn login_as(app: &Router, username: &str) -> String {
    let resp = app
        .clone()
        .oneshot(
            axum::http::Request::post("/login")
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .body(Body::from(format!("username={username}&password=pass1234")))
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

async fn post_form(app: &Router, path: &str, cookie: &str, body: &str) -> axum::response::Response {
    app.clone()
        .oneshot(
            axum::http::Request::post(path)
                .header(header::CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(header::COOKIE, cookie)
                .body(Body::from(body.to_string()))
                .unwrap(),
        )
        .await
        .unwrap()
}

async fn body_string(resp: axum::response::Response) -> String {
    let bytes = resp.into_body().collect().await.unwrap().to_bytes();
    String::from_utf8(bytes.to_vec()).unwrap()
}

async fn notification_count(pool: &PgPool, notification_type: &str) -> i64 {
    sqlx::query("SELECT COUNT(*) AS c FROM notification WHERE notification_type = $1")
        .bind(notification_type)
        .fetch_one(pool)
        .await
        .unwrap()
        .try_get::<i64, _>("c")
        .unwrap()
}

/// 通知宛先解決（Try#3）を検証するための荷受人連絡先。`seed_tracking` が投入する cargo の宛先。
const CONSIGNEE_EMAIL: &str = "consignee@flow-test.example";

async fn seed_tracking(pool: &PgPool, tracking_number: &str, booking_id: &str) {
    // 通知宛先（consignee_email）を解決できるよう荷主・予約を用意する。
    sqlx::query(
        r"INSERT INTO shipper (id, shipper_code, shipper_type, name, email)
          VALUES ('33333333-3333-3333-3333-333333333333', 'SHP-09999999', 'INDIVIDUAL',
                  'フロー試験 荷主', 'shipper@flow-test.example')
          ON CONFLICT (id) DO NOTHING",
    )
    .execute(pool)
    .await
    .expect("seed shipper");
    sqlx::query(
        r"INSERT INTO cargo
            (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode,
             arrival_deadline, consignee_name, consignee_email, booking_status)
          VALUES ($1, '33333333-3333-3333-3333-333333333333', 'GENERAL', 1000.000,
                  'JPOSA', 'USLAX', DATE '2026-05-20', 'Flow Consignee', $2, 'TRACKING_ISSUED')",
    )
    .bind(booking_id)
    .bind(CONSIGNEE_EMAIL)
    .execute(pool)
    .await
    .expect("seed cargo");

    let tracking_id: i64 = sqlx::query_scalar(
        r"INSERT INTO tracking_activity (tracking_number, booking_id, transport_status)
          VALUES ($1, $2, 'LOADED') RETURNING id",
    )
    .bind(tracking_number)
    .bind(booking_id)
    .fetch_one(pool)
    .await
    .expect("seed tracking");
    // current_status が LOADED を導出できるよう積込イベントを 1 件記録する。
    sqlx::query(
        r"INSERT INTO tracking_handling_event
            (tracking_id, event_type, event_time, location_unlocode, voyage_number)
          VALUES ($1, 'LOADED', TIMESTAMPTZ '2026-05-01 18:00:00+09', 'JPOSA', 'V0001')",
    )
    .bind(tracking_id)
    .execute(pool)
    .await
    .expect("seed tracking event");
}

#[tokio::test]
async fn us01_見積を作成しルート候補が表示される() {
    let (app, pool, _c) = setup().await;
    let cookie = login_as(&app, "sales").await;

    let body =
        "origin=JPOSA&destination=USLAX&arrival_deadline=2026-05-20&cargo_type=GENERAL&weight=1200";
    let resp = post_form(&app, "/estimates", &cookie, body).await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    let location = resp
        .headers()
        .get(header::LOCATION)
        .unwrap()
        .to_str()
        .unwrap()
        .to_string();
    assert!(location.starts_with("/estimates/EST-"));

    // 見積が保存されている。
    let cnt: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM estimate")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(cnt, 1);
    // ルート候補（直行 V0001）が算出されている。
    let cand: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM route_candidate")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(cand, 1);
}

#[tokio::test]
async fn us01_期限内ルートが無い見積は候補0件になる() {
    let (app, pool, _c) = setup().await;
    let cookie = login_as(&app, "sales").await;

    // 到着 2026-05-14 より前の期限 → 候補 0。
    let body =
        "origin=JPOSA&destination=USLAX&arrival_deadline=2026-05-10&cargo_type=GENERAL&weight=800";
    let resp = post_form(&app, "/estimates", &cookie, body).await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    let cand: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM route_candidate")
        .fetch_one(&pool)
        .await
        .unwrap();
    assert_eq!(cand, 0);
}

#[tokio::test]
async fn us18_公開追跡を未認証で照会できる() {
    let (app, pool, _c) = setup().await;
    seed_tracking(&pool, "TRK-PUB-1", "BKG-100").await;

    // Cookie なし（未認証）で照会。
    let resp = app
        .clone()
        .oneshot(
            axum::http::Request::get("/public/tracking/TRK-PUB-1")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = body_string(resp).await;
    assert!(html.contains("public-transport-status"));
    assert!(html.contains("積込済"));
}

#[tokio::test]
async fn us18_存在しない追跡番号は見つかりません表示() {
    let (app, _pool, _c) = setup().await;
    let resp = app
        .clone()
        .oneshot(
            axum::http::Request::get("/public/tracking/TRK-UNKNOWN")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let html = body_string(resp).await;
    assert!(html.contains("追跡番号が見つかりません"));
}

#[tokio::test]
async fn us19_遅延例外の登録と対応報告で通知が記録される() {
    let (app, pool, _c) = setup().await;
    seed_tracking(&pool, "TRK-EXC-1", "BKG-200").await;
    let cookie = login_as(&app, "tracker").await;

    // 遅延例外を登録 → 状態 Exception・遅延通知記録。
    let body = "exception_type=DELAY&un_locode=SGSIN&occurred_at=2026-05-05T12:00&description=台風による遅延";
    let resp = post_form(&app, "/tracking/TRK-EXC-1/exceptions", &cookie, body).await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);
    let status: String = sqlx::query_scalar(
        "SELECT transport_status FROM tracking_activity WHERE tracking_number = $1",
    )
    .bind("TRK-EXC-1")
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(status, "EXCEPTION");
    assert_eq!(notification_count(&pool, "EXCEPTION_RAISED").await, 1);
    // 通知宛先が荷受人連絡先に解決されている（Try#3 宛先ハードコード解消）。
    let recipient: String = sqlx::query_scalar(
        "SELECT recipient_email FROM notification WHERE notification_type = 'EXCEPTION_RAISED'",
    )
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(recipient, CONSIGNEE_EMAIL);

    // 対応報告 → 例外解決・対応報告通知記録。
    let resolve_body = "resolution_notes=代替便を手配・新到着予定 6/25&new_arrival=2026-06-25";
    let resp2 = post_form(
        &app,
        "/tracking/TRK-EXC-1/exceptions/0/resolve",
        &cookie,
        resolve_body,
    )
    .await;
    assert_eq!(resp2.status(), StatusCode::SEE_OTHER);
    assert_eq!(notification_count(&pool, "EXCEPTION_RESOLVED").await, 1);
    // 解決後は Exception ではなくなり、イベント末尾（LOADED）に復帰する。
    let status2: String = sqlx::query_scalar(
        "SELECT transport_status FROM tracking_activity WHERE tracking_number = $1",
    )
    .bind("TRK-EXC-1")
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(status2, "LOADED");
}

#[tokio::test]
async fn us20_紛失例外は荷主通知に加え管理職へエスカレーション通知される() {
    let (app, pool, _c) = setup().await;
    seed_tracking(&pool, "TRK-EXC-2", "BKG-200").await;
    let cookie = login_as(&app, "tracker").await;

    // 紛失例外を登録 → 状態 Exception・荷主通知＋管理職 escalation 通知。
    let body = "exception_type=LOST&un_locode=SGSIN&occurred_at=2026-05-06T09:00&description=荷物が紛失しました";
    let resp = post_form(&app, "/tracking/TRK-EXC-2/exceptions", &cookie, body).await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);

    let status: String = sqlx::query_scalar(
        "SELECT transport_status FROM tracking_activity WHERE tracking_number = $1",
    )
    .bind("TRK-EXC-2")
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(status, "EXCEPTION");

    // 荷主宛の破損/紛失通知（宛先＝荷受人連絡先）。
    assert_eq!(notification_count(&pool, "EXCEPTION_RAISED").await, 1);
    let raised_recipient: String = sqlx::query_scalar(
        "SELECT recipient_email FROM notification WHERE notification_type = 'EXCEPTION_RAISED'",
    )
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(raised_recipient, CONSIGNEE_EMAIL);

    // 管理職宛の escalation 通知（宛先・ロールまでアサート・Try#1）。
    assert_eq!(notification_count(&pool, "EXCEPTION_ESCALATED").await, 1);
    let (esc_recipient, esc_role): (String, String) = sqlx::query_as(
        "SELECT recipient_email, recipient_role FROM notification \
         WHERE notification_type = 'EXCEPTION_ESCALATED'",
    )
    .fetch_one(&pool)
    .await
    .unwrap();
    assert_eq!(esc_recipient, "manager@cargotracker.example");
    assert_eq!(esc_role, "ROLE_ADMIN");
}

#[tokio::test]
async fn us20_破損例外はエスカレーション通知されない() {
    let (app, pool, _c) = setup().await;
    seed_tracking(&pool, "TRK-EXC-3", "BKG-200").await;
    let cookie = login_as(&app, "tracker").await;

    let body = "exception_type=DAMAGE&un_locode=SGSIN&occurred_at=2026-05-06T09:00&description=荷物が破損しました";
    let resp = post_form(&app, "/tracking/TRK-EXC-3/exceptions", &cookie, body).await;
    assert_eq!(resp.status(), StatusCode::SEE_OTHER);

    assert_eq!(notification_count(&pool, "EXCEPTION_RAISED").await, 1);
    assert_eq!(notification_count(&pool, "EXCEPTION_ESCALATED").await, 0);
}
