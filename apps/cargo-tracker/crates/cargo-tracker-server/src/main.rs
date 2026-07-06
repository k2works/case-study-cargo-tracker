//! Cargo Tracker サーバーの composition root。
//!
//! axum Router を組み立て、HTTP サーバーとして起動する。
//! 現状はヘルスチェックのみ。各コンテキストのルートは Phase 1 以降で追加する。

use axum::{Json, Router, routing::get};

/// アプリケーションの Router を組み立てる。
fn app() -> Router {
    Router::new().route("/health", get(health))
}

/// ヘルスチェックハンドラ。
async fn health() -> Json<serde_json::Value> {
    Json(serde_json::json!({ "status": "UP" }))
}

/// リッスンアドレスを環境変数 PORT（既定 8080）から組み立てる。
fn listen_addr() -> String {
    let port = std::env::var("PORT").unwrap_or_else(|_| "8080".to_string());
    format!("0.0.0.0:{port}")
}

#[tokio::main]
async fn main() {
    let addr = listen_addr();
    let listener = tokio::net::TcpListener::bind(&addr)
        .await
        .unwrap_or_else(|e| panic!("{addr} にバインドできません: {e}"));
    println!("cargo-tracker-server listening on http://{addr}");
    axum::serve(listener, app())
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
        let response = app()
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
        let response = app()
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
