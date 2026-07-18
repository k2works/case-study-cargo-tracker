//! `/api/shippers` 荷主検索の HTTP レベル統合テスト（testcontainers + tower oneshot）。

use axum::body::Body;
use axum::http::{Request, StatusCode};
use domain_shipper::{Email, Shipper, ShipperKind, ShipperName, ShipperRepository};
use http_body_util::BodyExt;
use infra_persistence::{MIGRATOR, SqlxShipperRepository};
use interface_rest::{RestState, rest_router};
use sqlx::PgPool;
use testcontainers::ImageExt;
use testcontainers_modules::postgres::Postgres;
use testcontainers_modules::testcontainers::runners::AsyncRunner;
use tower::ServiceExt;

#[tokio::test]
async fn 名称の部分一致で荷主を検索できる() {
    let container = Postgres::default()
        .with_tag("16-alpine")
        .start()
        .await
        .expect("start");
    let port = container.get_host_port_ipv4(5432).await.unwrap();
    let url = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");
    let pool = PgPool::connect(&url).await.unwrap();
    MIGRATOR.run(&pool).await.unwrap();

    let repo = SqlxShipperRepository::new(pool.clone());
    for (name, email) in [
        ("山田商事", "yamada@example.com"),
        ("鈴木物流", "suzuki@example.com"),
    ] {
        repo.save(&Shipper::register(
            ShipperName::new(name).unwrap(),
            Email::new(email).unwrap(),
            None,
            None,
            ShipperKind::Individual,
        ))
        .await
        .unwrap();
    }

    let app = rest_router(RestState { pool });
    let resp = app
        .oneshot(
            Request::get("/api/shippers?q=山田")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let bytes = resp.into_body().collect().await.unwrap().to_bytes();
    let json: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
    let arr = json.as_array().unwrap();
    assert_eq!(arr.len(), 1);
    assert_eq!(arr[0]["name"], "山田商事");
    assert_eq!(arr[0]["email"], "yamada@example.com");
}

#[tokio::test]
async fn 空キーワードは空結果を返す() {
    let container = Postgres::default()
        .with_tag("16-alpine")
        .start()
        .await
        .expect("start");
    let port = container.get_host_port_ipv4(5432).await.unwrap();
    let url = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");
    let pool = PgPool::connect(&url).await.unwrap();
    MIGRATOR.run(&pool).await.unwrap();

    let app = rest_router(RestState { pool });
    let resp = app
        .oneshot(
            Request::get("/api/shippers?q=")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let bytes = resp.into_body().collect().await.unwrap().to_bytes();
    let json: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(json.as_array().unwrap().len(), 0);
}
