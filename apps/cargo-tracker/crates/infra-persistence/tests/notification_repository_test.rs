//! `SqlxNotificationRepository` の統合テスト（testcontainers）。

use domain_booking::{BookingId, Notification, NotificationPort, NotificationType};
use infra_persistence::{MIGRATOR, SqlxNotificationRepository};
use shared_kernel::Role;
use sqlx::PgPool;
use testcontainers::ContainerAsync;
use testcontainers::ImageExt;
use testcontainers_modules::postgres::Postgres;
use testcontainers_modules::testcontainers::runners::AsyncRunner;

async fn setup() -> (PgPool, ContainerAsync<Postgres>) {
    let container = Postgres::default()
        .with_tag("16-alpine")
        .start()
        .await
        .expect("start postgres");
    let port = container.get_host_port_ipv4(5432).await.expect("host port");
    let url = format!("postgres://postgres:postgres@127.0.0.1:{port}/postgres");
    let pool = PgPool::connect(&url).await.expect("connect");
    MIGRATOR.run(&pool).await.expect("migrate");
    (pool, container)
}

#[tokio::test]
async fn 通知を記録し予約番号で件数を取得できる() {
    let (pool, _c) = setup().await;
    let repo = SqlxNotificationRepository::new(pool.clone());
    let booking_id = BookingId::parse("BKG-0001").unwrap();

    let notification = Notification::new(
        booking_id,
        NotificationType::RouteDesignRequested,
        Role::RouteDesigner,
        "designer@example.com",
        "経路設計依頼",
        "予約 BKG-0001 の経路設計を依頼します。",
    );
    repo.notify(&notification).await.expect("notify");

    assert_eq!(repo.count_by_booking_id("BKG-0001").await.unwrap(), 1);
    assert_eq!(repo.count_by_booking_id("BKG-9999").await.unwrap(), 0);
}

#[tokio::test]
async fn 複数種別の通知を同一予約に記録できる() {
    let (pool, _c) = setup().await;
    let repo = SqlxNotificationRepository::new(pool.clone());

    for (ty, role, email) in [
        (
            NotificationType::RouteNotifiedToShipper,
            Role::Shipper,
            "shipper@example.com",
        ),
        (
            NotificationType::BookingCancelled,
            Role::Shipper,
            "shipper@example.com",
        ),
    ] {
        let n = Notification::new(
            BookingId::parse("BKG-0002").unwrap(),
            ty,
            role,
            email,
            "通知",
            "本文",
        );
        repo.notify(&n).await.expect("notify");
    }

    assert_eq!(repo.count_by_booking_id("BKG-0002").await.unwrap(), 2);
}
