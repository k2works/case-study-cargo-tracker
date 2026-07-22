//! 貨物予約登録フローの HTTP レベル統合テスト（testcontainers + tower oneshot）。

use axum::Router;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use http_body_util::BodyExt;
use infra_persistence::{MIGRATOR, SqlxShipperRepository, SqlxUserRepository};
use interface_web::{AppState, web_router};
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
    let app = web_router(AppState { pool }).layer(SessionManagerLayer::new(MemoryStore::default()));
    (app, shipper_id, container)
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
