//! REST / htmx 用インターフェース層。
//!
//! IT1 では荷主インクリメンタル検索 `GET /api/shippers?q=` を提供する。

use app_shipper::FindShipperQueryService;
use axum::Router;
use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Json, Response};
use axum::routing::get;
use infra_persistence::SqlxShipperQueryAdapter;
use serde::Deserialize;
use sqlx::PgPool;

/// REST 層の共有状態。
#[derive(Clone)]
pub struct RestState {
    /// DB コネクションプール。
    pub pool: PgPool,
}

/// 荷主検索クエリパラメータ。
#[derive(Debug, Deserialize)]
pub struct ShipperSearchQuery {
    /// 検索キーワード（名称・メールの部分一致）。
    #[serde(default)]
    q: String,
}

/// REST 層のルーターを組み立てる。
pub fn rest_router(state: RestState) -> Router {
    Router::new()
        .route("/api/shippers", get(search_shippers))
        .with_state(state)
}

async fn search_shippers(
    State(state): State<RestState>,
    Query(params): Query<ShipperSearchQuery>,
) -> Response {
    let service = FindShipperQueryService::new(SqlxShipperQueryAdapter::new(state.pool.clone()));
    match service.search(&params.q).await {
        Ok(summaries) => Json(summaries).into_response(),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}
