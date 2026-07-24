//! `SqlxInvoiceRepository` の統合テスト（testcontainers・US23）。

use chrono::{NaiveDate, Utc};
use domain_billing::{
    BillingBookingId, DEFAULT_TAX_RATE, Invoice, InvoiceId, InvoiceLineItem, InvoiceRepository,
    Money, Payment, PaymentMethod, PaymentStatus,
};
use infra_persistence::{MIGRATOR, SqlxInvoiceRepository};
use rust_decimal::Decimal;
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

fn invoice(booking: &str, number: &str) -> Invoice {
    Invoice::issue(
        InvoiceId::generate(),
        number,
        BillingBookingId::parse(booking).unwrap(),
        Money::jpy(Decimal::from(170_000)),
        DEFAULT_TAX_RATE,
        Utc::now(),
        NaiveDate::from_ymd_opt(2026, 6, 30).unwrap(),
        vec![InvoiceLineItem::new(
            "輸送料金",
            Money::jpy(Decimal::from(170_000)),
            1,
        )],
    )
    .unwrap()
}

#[tokio::test]
async fn 精算書を保存し番号で再構築できる() {
    let (pool, _c) = setup().await;
    let repo = SqlxInvoiceRepository::new(pool.clone());

    repo.save(&invoice("BKG-INV-1", "INV-0001"))
        .await
        .expect("保存できるはず");
    let found = repo
        .find_by_number("INV-0001")
        .await
        .unwrap()
        .expect("見つかるはず");
    assert_eq!(found.charge_total(), Money::jpy(Decimal::from(170_000)));
    assert_eq!(found.tax_amount(), Money::jpy(Decimal::from(17_000)));
    assert_eq!(found.total_amount(), Money::jpy(Decimal::from(187_000)));
    assert_eq!(found.payment_status(), PaymentStatus::Pending);
    assert_eq!(found.line_items().len(), 1);
}

#[tokio::test]
async fn 入金確認した精算書は支払記録付きで再構築される() {
    let (pool, _c) = setup().await;
    let repo = SqlxInvoiceRepository::new(pool.clone());

    let mut inv = invoice("BKG-INV-2", "INV-0002");
    repo.save(&inv).await.unwrap();
    inv.confirm_payment(Payment::new(
        Money::jpy(Decimal::from(187_000)),
        Utc::now(),
        PaymentMethod::BankTransfer,
        Some("TXN-9".to_string()),
    ))
    .unwrap();
    repo.save(&inv).await.unwrap();

    let found = repo
        .find_by_booking_id(&BillingBookingId::parse("BKG-INV-2").unwrap())
        .await
        .unwrap()
        .unwrap();
    assert_eq!(found.payment_status(), PaymentStatus::Confirmed);
    assert_eq!(found.payments().len(), 1);
    assert_eq!(found.payments()[0].transaction_reference(), Some("TXN-9"));
    // 予約 1 件に 1 精算書（UNIQUE・upsert）。
    assert_eq!(repo.find_all().await.unwrap().len(), 1);
}
