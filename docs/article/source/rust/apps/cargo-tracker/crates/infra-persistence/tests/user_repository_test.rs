//! `SqlxUserRepository` の統合テスト（testcontainers）。

use infra_persistence::{MIGRATOR, SqlxUserRepository, verify_password};
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
async fn ユーザーを作成してロール付きで認証情報を取得できる() {
    let (pool, _c) = setup().await;
    let repo = SqlxUserRepository::new(pool);
    repo.create_user(
        "yamada",
        "yamada@example.com",
        "SecurePass123!",
        &[Role::Sales],
    )
    .await
    .expect("create");

    let (user, hash) = repo
        .find_credentials("yamada")
        .await
        .expect("find")
        .expect("present");

    assert_eq!(user.username, "yamada");
    assert_eq!(user.roles, vec![Role::Sales]);
    assert!(verify_password(&hash, "SecurePass123!"));
    assert!(!verify_password(&hash, "wrong"));
}

#[tokio::test]
async fn 存在しないユーザーは_none_を返す() {
    let (pool, _c) = setup().await;
    let repo = SqlxUserRepository::new(pool);
    assert!(
        repo.find_credentials("nobody")
            .await
            .expect("find")
            .is_none()
    );
}
