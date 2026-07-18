//! Booking / Shipper Context の Web インターフェース層（Askama SSR + tower-sessions）。
//!
//! IT1 ではログイン認証・ロール別ナビゲーション・ダッシュボードのウォーキングスケルトンを提供する。

use app_shipper::{
    RegisterShipperCommandService, RegisterShipperInput, ShipperKindInput, ShipperServiceError,
};
use askama::Template;
use axum::Router;
use axum::extract::{Form, State};
use axum::http::StatusCode;
use axum::response::{Html, IntoResponse, Redirect, Response};
use axum::routing::{get, post};
use infra_persistence::{SqlxShipperRepository, SqlxUserRepository, verify_password};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use sqlx::PgPool;
use std::str::FromStr;
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

#[derive(Template)]
#[template(path = "shipper_new.html")]
struct ShipperNewTemplate {
    current_user: CurrentUser,
    error: bool,
    error_message: String,
}

/// ログインフォーム入力。
#[derive(Debug, Deserialize)]
pub struct LoginForm {
    username: String,
    password: String,
}

/// 荷主登録フォーム入力。
#[derive(Debug, Deserialize)]
pub struct ShipperForm {
    kind: String,
    name: String,
    email: String,
    phone: Option<String>,
    address: Option<String>,
    contract_number: Option<String>,
    discount_rate: Option<String>,
}

fn non_empty(value: Option<String>) -> Option<String> {
    value.filter(|s| !s.trim().is_empty())
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
        .route("/shippers/new", get(shipper_new_form))
        .route("/shippers", post(shipper_create))
        .with_state(state)
}

/// セッションから現在のユーザーを取得する。未認証なら `/login` へのリダイレクトを `Err` で返す。
async fn require_user(session: &Session) -> Result<CurrentUser, Response> {
    match session.get::<CurrentUser>(SESSION_USER_KEY).await {
        Ok(Some(user)) => Ok(user),
        _ => Err(Redirect::to("/login").into_response()),
    }
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

async fn shipper_new_form(session: Session) -> Response {
    let current_user = match require_user(&session).await {
        Ok(u) => u,
        Err(resp) => return resp,
    };
    if !current_user.has_role("ROLE_SALES") {
        return StatusCode::FORBIDDEN.into_response();
    }
    render(&ShipperNewTemplate {
        current_user,
        error: false,
        error_message: String::new(),
    })
}

async fn shipper_create(
    State(state): State<AppState>,
    session: Session,
    Form(form): Form<ShipperForm>,
) -> Response {
    let current_user = match require_user(&session).await {
        Ok(u) => u,
        Err(resp) => return resp,
    };
    if !current_user.has_role("ROLE_SALES") {
        return StatusCode::FORBIDDEN.into_response();
    }

    let kind = if form.kind == "CORPORATE" {
        let rate = form
            .discount_rate
            .as_deref()
            .and_then(|s| Decimal::from_str(s.trim()).ok())
            .unwrap_or(Decimal::ZERO);
        ShipperKindInput::Corporate {
            contract_number: form.contract_number.unwrap_or_default(),
            discount_rate: rate,
        }
    } else {
        ShipperKindInput::Individual
    };

    let input = RegisterShipperInput {
        name: form.name,
        email: form.email,
        phone: non_empty(form.phone),
        address: non_empty(form.address),
        kind,
    };

    let service =
        RegisterShipperCommandService::new(SqlxShipperRepository::new(state.pool.clone()));
    match service.register(input).await {
        Ok(_) => Redirect::to("/bookings/new").into_response(),
        Err(e) => {
            let error_message = match e {
                ShipperServiceError::EmailAlreadyExists(_) => {
                    "このメールアドレスは既に登録されています".to_string()
                }
                ShipperServiceError::Domain(_) => "入力内容に誤りがあります".to_string(),
                ShipperServiceError::Repository(_) => "登録処理に失敗しました".to_string(),
            };
            (
                StatusCode::OK,
                render(&ShipperNewTemplate {
                    current_user,
                    error: true,
                    error_message,
                }),
            )
                .into_response()
        }
    }
}
