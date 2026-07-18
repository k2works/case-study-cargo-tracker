//! Booking / Shipper Context の Web インターフェース層（Askama SSR + tower-sessions）。
//!
//! IT1 ではログイン認証・ロール別ナビゲーション・ダッシュボードのウォーキングスケルトンを提供する。

use askama::Template;
use axum::Router;
use axum::extract::{Form, State};
use axum::http::StatusCode;
use axum::response::{Html, IntoResponse, Redirect, Response};
use axum::routing::{get, post};
use infra_persistence::{SqlxUserRepository, verify_password};
use serde::{Deserialize, Serialize};
use sqlx::PgPool;
use tower_sessions::Session;

/// セッションに保存する現在のユーザー（ロール付き）。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CurrentUser {
    /// ログイン名。
    pub username: String,
    /// 付与ロール（`ROLE_` プレフィックス付き文字列）。
    pub roles: Vec<String>,
}

impl CurrentUser {
    /// 指定ロールを保有するかを返す（テンプレートのロール出し分けに使用）。
    #[must_use]
    pub fn has_role(&self, role: &str) -> bool {
        self.roles.iter().any(|r| r == role)
    }
}

const SESSION_USER_KEY: &str = "current_user";

/// Web 層の共有状態。
#[derive(Clone)]
pub struct AppState {
    /// DB コネクションプール。
    pub pool: PgPool,
}

#[derive(Template)]
#[template(path = "login.html")]
struct LoginTemplate {
    error: bool,
}

#[derive(Template)]
#[template(path = "dashboard.html")]
struct DashboardTemplate {
    current_user: CurrentUser,
}

/// ログインフォーム入力。
#[derive(Debug, Deserialize)]
pub struct LoginForm {
    username: String,
    password: String,
}

fn render<T: Template>(template: &T) -> Response {
    match template.render() {
        Ok(html) => Html(html).into_response(),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

/// Web 層のルーターを組み立てる（セッションレイヤーは呼び出し側で付与する）。
pub fn web_router(state: AppState) -> Router {
    Router::new()
        .route("/", get(dashboard))
        .route("/login", get(login_form).post(login_submit))
        .route("/logout", post(logout))
        .with_state(state)
}

async fn login_form() -> Response {
    render(&LoginTemplate { error: false })
}

async fn login_submit(
    State(state): State<AppState>,
    session: Session,
    Form(form): Form<LoginForm>,
) -> Response {
    let repo = SqlxUserRepository::new(state.pool.clone());
    match repo.find_credentials(&form.username).await {
        Ok(Some((user, hash))) if verify_password(&hash, &form.password) => {
            let current = CurrentUser {
                username: user.username,
                roles: user.roles.iter().map(|r| r.as_str().to_string()).collect(),
            };
            if session.insert(SESSION_USER_KEY, current).await.is_err() {
                return StatusCode::INTERNAL_SERVER_ERROR.into_response();
            }
            Redirect::to("/").into_response()
        }
        Ok(_) => (StatusCode::OK, render(&LoginTemplate { error: true })).into_response(),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

async fn logout(session: Session) -> Response {
    let _ = session.flush().await;
    Redirect::to("/login").into_response()
}

async fn dashboard(session: Session) -> Response {
    match session.get::<CurrentUser>(SESSION_USER_KEY).await {
        Ok(Some(current_user)) => render(&DashboardTemplate { current_user }),
        _ => Redirect::to("/login").into_response(),
    }
}
