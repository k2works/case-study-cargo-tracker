//! マイグレーション適用バイナリ。
//!
//! 埋め込みマイグレータ（`infra_persistence::MIGRATOR`）で未適用のマイグレーションを適用する。
//! sqlx-cli（postgres ドライバ）に依存せず、アプリ本体と同一のマイグレーションを適用する。
//!
//! 実行例:
//! ```bash
//! cargo run -p cargo-tracker-server --bin migrate
//! ```

use infra_persistence::MIGRATOR;
use sqlx::PgPool;

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
    println!("マイグレーション適用完了");
}
