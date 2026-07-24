//! `SqlxFreightChargeRepository` の統合テスト（testcontainers・US21/US22）。

use domain_billing::{
    AdjustmentReason, BillingBookingId, ChargeAdjustment, ChargeStatus, DiscountRate,
    FreightCharge, FreightChargeId, FreightChargeRepository, Money,
};
use infra_persistence::{MIGRATOR, SqlxFreightChargeRepository};
use rust_decimal::Decimal;
use rust_decimal::prelude::*;
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
async fn 割引と調整付きの料金を保存し予約idで再構築できる() {
    let (pool, _c) = setup().await;
    let repo = SqlxFreightChargeRepository::new(pool.clone());

    let mut charge = FreightCharge::create(
        FreightChargeId::generate(),
        BillingBookingId::parse("BKG-FC-1").unwrap(),
        Money::jpy(Decimal::from(200_000)),
    );
    charge
        .add_adjustment(ChargeAdjustment::new(
            AdjustmentReason::DelayReduction,
            Money::jpy(Decimal::from(15_000)),
        ))
        .unwrap();
    charge
        .apply_discount(DiscountRate::new(Decimal::from_str("0.10").unwrap()).unwrap())
        .unwrap();
    repo.save(&charge).await.expect("保存できるはず");

    let found = repo
        .find_by_booking_id(&BillingBookingId::parse("BKG-FC-1").unwrap())
        .await
        .expect("検索成功")
        .expect("料金が見つかるはず");
    assert_eq!(found.base_amount(), Money::jpy(Decimal::from(200_000)));
    assert_eq!(found.adjustments().len(), 1);
    assert!(found.discount().is_some());
    // 合計 = 200,000 − 15,000 − 20,000(割引) = 165,000
    assert_eq!(found.total().unwrap(), Money::jpy(Decimal::from(165_000)));
    assert_eq!(found.status(), ChargeStatus::Draft);
}

#[tokio::test]
async fn 確定した料金は状態が保持され再算出でupsertされる() {
    let (pool, _c) = setup().await;
    let repo = SqlxFreightChargeRepository::new(pool.clone());

    let mut charge = FreightCharge::create(
        FreightChargeId::generate(),
        BillingBookingId::parse("BKG-FC-2").unwrap(),
        Money::jpy(Decimal::from(100_000)),
    );
    repo.save(&charge).await.unwrap();
    charge.confirm().unwrap();
    repo.save(&charge).await.unwrap();

    let found = repo
        .find_by_booking_id(&BillingBookingId::parse("BKG-FC-2").unwrap())
        .await
        .unwrap()
        .unwrap();
    assert_eq!(found.status(), ChargeStatus::Confirmed);
    // 予約 1 件に 1 料金（UNIQUE・upsert・二重算出なし）。
    assert_eq!(repo.find_all().await.unwrap().len(), 1);
}
