//! Cargo Tracker サーバーの composition root。
//!
//! PgPool・セッション・Web ルーターを組み立て、HTTP サーバーとして起動する。
//! IT1 では認証・ダッシュボードのウォーキングスケルトンと `/health` を提供する。

use axum::{Json, Router, routing::get};
use infra_persistence::{
    MIGRATOR, SqlxCargoRepository, SqlxCargoSpecProvider, SqlxNotificationRepository,
    SqlxSelectedRouteRepository, SqlxSelectedRouteView, SqlxShipperExistenceChecker,
    SqlxShipperRepository, SqlxVoyageRepository,
};
use interface_rest::{RestState, rest_router};
use interface_web::{AppState, web_router};
use sqlx::PgPool;
use std::sync::Arc;
use tower_http::services::ServeDir;
use tower_sessions::{MemoryStore, SessionManagerLayer};

/// composition root: sqlx 実装を出力ポート trait のトレイトオブジェクトとして
/// `AppState` に注入する（ADR-0003）。
fn build_app_state(pool: PgPool) -> AppState {
    AppState {
        shipper_repo: Arc::new(SqlxShipperRepository::new(pool.clone())),
        cargo_repo: Arc::new(SqlxCargoRepository::new(pool.clone())),
        shipper_checker: Arc::new(SqlxShipperExistenceChecker::new(pool.clone())),
        voyage_repo: Arc::new(SqlxVoyageRepository::new(pool.clone())),
        cargo_spec_provider: Arc::new(SqlxCargoSpecProvider::new(pool.clone())),
        selected_route_repo: Arc::new(SqlxSelectedRouteRepository::new(pool.clone())),
        notification_port: Arc::new(SqlxNotificationRepository::new(pool.clone())),
        selected_route_view: Arc::new(SqlxSelectedRouteView::new(pool.clone())),
        pool,
    }
}

/// ヘルスチェックのみのルーター（DB 非依存）。
fn health_router() -> Router {
    Router::new().route("/health", get(health))
}

/// ヘルスチェックハンドラ。
async fn health() -> Json<serde_json::Value> {
    Json(serde_json::json!({ "status": "UP" }))
}

/// アプリケーション全体の Router を組み立てる。
///
/// セッションレイヤー・Web ルーター（認証/ダッシュボード）・ヘルスチェックを合成する。
fn build_app(pool: PgPool) -> Router {
    // セッション Cookie の Secure 属性。HTTPS 環境でのみ true にする。
    // 既定は false（開発は http://localhost のため）。本番は SECURE_COOKIES=1 で有効化する。
    // Secure=true のまま http でアクセスするとブラウザが Cookie を返送せず、
    // ログイン後にセッションが復元できず /login にループする。
    let secure_cookies = std::env::var("SECURE_COOKIES").is_ok_and(|v| v == "1" || v == "true");
    let session_layer =
        SessionManagerLayer::new(MemoryStore::default()).with_secure(secure_cookies);
    // 静的アセット（Bootstrap / htmx / カスタム CSS・JS）を /static で配信する。
    // 配信元は STATIC_DIR（既定 "static"、cwd = apps/cargo-tracker を想定）。
    let static_dir = std::env::var("STATIC_DIR").unwrap_or_else(|_| "static".to_string());
    let app = health_router()
        .merge(rest_router(RestState { pool: pool.clone() }))
        .merge(web_router(build_app_state(pool)))
        .nest_service("/static", ServeDir::new(static_dir))
        .layer(session_layer);

    // 環境変数 LIVERELOAD が設定されている場合のみ、ブラウザ自動リロードを有効化する（開発専用）。
    // tower-livereload が HTML にスクリプトを注入し、サーバ再起動時にブラウザを再読み込みさせる。
    if std::env::var("LIVERELOAD").is_ok() {
        tracing::info!("livereload 有効: ブラウザ自動リロードを注入します");
        app.layer(tower_livereload::LiveReloadLayer::new())
    } else {
        app
    }
}

/// リッスンアドレスを環境変数 PORT（既定 8080）から組み立てる。
fn listen_addr() -> String {
    let port = std::env::var("PORT").unwrap_or_else(|_| "8080".to_string());
    format!("0.0.0.0:{port}")
}

/// DB 接続 URL を環境変数 DATABASE_URL から取得する。
///
/// 既定値は `apps/cargo-tracker/docker-compose.yml` の postgres サービス
/// （user=cargo / password=cargo / db=cargo_tracker）に一致させる。
fn database_url() -> String {
    std::env::var("DATABASE_URL")
        .unwrap_or_else(|_| "postgres://cargo:cargo@127.0.0.1:5432/cargo_tracker".to_string())
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();

    let pool = PgPool::connect(&database_url())
        .await
        .expect("データベースに接続できません");
    MIGRATOR
        .run(&pool)
        .await
        .expect("マイグレーションの適用に失敗しました");

    let addr = listen_addr();
    let listener = tokio::net::TcpListener::bind(&addr)
        .await
        .unwrap_or_else(|e| panic!("{addr} にバインドできません: {e}"));
    tracing::info!("cargo-tracker-server listening on http://{addr}");
    axum::serve(listener, build_app(pool))
        .await
        .expect("サーバーの起動に失敗しました");
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use axum::http::{Request, StatusCode};
    use http_body_util::BodyExt;
    use tower::ServiceExt;

    #[tokio::test]
    async fn ヘルスチェックは200とupを返す() {
        let response = health_router()
            .oneshot(
                Request::builder()
                    .uri("/health")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::OK);
        let body = response.into_body().collect().await.unwrap().to_bytes();
        let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
        assert_eq!(json, serde_json::json!({ "status": "UP" }));
    }

    #[tokio::test]
    async fn 未定義パスは404を返す() {
        let response = health_router()
            .oneshot(
                Request::builder()
                    .uri("/unknown")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }

    #[test]
    fn リッスンアドレスは既定で8080() {
        assert_eq!(listen_addr(), "0.0.0.0:8080");
    }
}
