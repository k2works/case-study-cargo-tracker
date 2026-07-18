//! 開発用ユーザーの seed バイナリ。
//!
//! ログイン検証用のロール別ユーザーを冪等に投入する（既存ユーザーはスキップ）。
//! パスワードは argon2 でハッシュ化して保存する（`SqlxUserRepository` に委譲）。
//!
//! 実行例:
//! ```bash
//! DATABASE_URL=postgres://cargo:cargo@127.0.0.1:5432/cargo_tracker \
//!   cargo run -p cargo-tracker-server --bin seed
//! ```
//!
//! 開発専用。本番環境では実行しないこと。

use infra_persistence::{MIGRATOR, SqlxUserRepository};
use shared_kernel::Role;
use sqlx::PgPool;

/// 投入する開発用ユーザー（username, email, password, roles）。
const DEV_USERS: &[(&str, &str, &str, &[Role])] = &[
    ("sales", "sales@example.com", "password", &[Role::Sales]),
    (
        "shipper",
        "shipper@example.com",
        "password",
        &[Role::Shipper],
    ),
    (
        "consignee",
        "consignee@example.com",
        "password",
        &[Role::Consignee],
    ),
    (
        "tracker",
        "tracker@example.com",
        "password",
        &[Role::Tracker],
    ),
    (
        "handler",
        "handler@example.com",
        "password",
        &[Role::Handler],
    ),
    (
        "designer",
        "designer@example.com",
        "password",
        &[Role::RouteDesigner],
    ),
    (
        "billing",
        "billing@example.com",
        "password",
        &[Role::Billing],
    ),
    ("admin", "admin@example.com", "password", &[Role::Admin]),
];

fn database_url() -> String {
    std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://cargo:cargo@127.0.0.1:5432/cargo_tracker".to_string())
}

#[tokio::main]
async fn main() {
    let pool = PgPool::connect(&database_url())
        .await
        .expect("データベースに接続できません");
    MIGRATOR
        .run(&pool)
        .await
        .expect("マイグレーションの適用に失敗しました");

    let repo = SqlxUserRepository::new(pool);
    let mut created = 0;
    let mut skipped = 0;

    for (username, email, password, roles) in DEV_USERS {
        let exists = repo
            .find_credentials(username)
            .await
            .expect("ユーザー検索に失敗しました")
            .is_some();
        if exists {
            println!("スキップ: {username}（既に存在）");
            skipped += 1;
            continue;
        }
        repo.create_user(username, email, password, roles)
            .await
            .expect("ユーザー作成に失敗しました");
        let role_str: Vec<&str> = roles.iter().map(|r| r.as_str()).collect();
        println!("作成: {username} / password（{}）", role_str.join(", "));
        created += 1;
    }

    println!("seed 完了: 作成 {created} 件 / スキップ {skipped} 件");
    println!("ログイン例: ユーザー名 sales / パスワード password");
}
