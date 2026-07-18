//! Booking / Shipper Context の Web インターフェース層（Askama SSR + tower-sessions）。
//!
//! IT1 ではログイン認証・ロール別ナビゲーション・ダッシュボードのウォーキングスケルトンを提供する。

use app_booking::{BookCargoCommandService, BookingServiceError};
use app_shipper::{
    RegisterShipperCommandService, RegisterShipperInput, ShipperKindInput, ShipperServiceError,
};
use askama::Template;
use axum::Router;
use axum::extract::{Form, Path, State};
use axum::http::StatusCode;
use axum::response::{Html, IntoResponse, Redirect, Response};
use axum::routing::{get, post};
use chrono::NaiveDate;
use domain_booking::{
    BookCargoCommand, CargoType, Consignee, HazardousDeclaration, RouteSpecification,
    TemperatureRequirement, TemperatureUnit, Weight,
};
use infra_persistence::{
    SqlxCargoRepository, SqlxShipperExistenceChecker, SqlxShipperRepository, SqlxUserRepository,
    verify_password,
};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use shared_kernel::{Location, ShipperId};
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
    default_username: String,
    default_password: String,
}

impl LoginTemplate {
    /// 開発用の既定アカウントを初期入力した状態のテンプレートを構成する。
    fn new(error: bool) -> Self {
        Self {
            error,
            default_username: "sales".to_string(),
            default_password: "password".to_string(),
        }
    }
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

#[derive(Template)]
#[template(path = "booking_new.html")]
struct BookingNewTemplate {
    current_user: CurrentUser,
    error: bool,
    error_message: String,
}

#[derive(Template)]
#[template(path = "booking_show.html")]
struct BookingShowTemplate {
    current_user: CurrentUser,
    booking_id: String,
    status: String,
    origin: String,
    destination: String,
    cargo_type: String,
}

#[derive(Template)]
#[template(path = "placeholder.html")]
struct PlaceholderTemplate {
    current_user: CurrentUser,
    title: String,
}

/// 予約登録フォーム入力。
#[derive(Debug, Deserialize)]
pub struct BookingForm {
    shipper_id: String,
    consignee_name: String,
    consignee_contact: String,
    origin: String,
    destination: String,
    arrival_deadline: String,
    cargo_type: String,
    weight: String,
    hazardous_class: Option<String>,
    un_number: Option<String>,
    proper_shipping_name: Option<String>,
    min_temperature: Option<String>,
    max_temperature: Option<String>,
    temperature_unit: Option<String>,
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
        .route("/bookings/new", get(booking_new_form))
        .route("/bookings", get(placeholder_bookings).post(booking_create))
        .route("/bookings/{booking_id}", get(booking_show))
        .route("/tracking", get(placeholder_tracking))
        .route("/handling", get(placeholder_handling))
        .route("/estimates", get(placeholder_estimates))
        .route("/voyages", get(placeholder_voyages))
        .route("/billing/invoices", get(placeholder_billing))
        .route("/admin/discount-policies", get(placeholder_admin))
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
    render(&LoginTemplate::new(false))
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
        Ok(_) => (StatusCode::OK, render(&LoginTemplate::new(true))).into_response(),
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

async fn render_placeholder(session: &Session, title: &str) -> Response {
    match require_user(session).await {
        Ok(current_user) => render(&PlaceholderTemplate {
            current_user,
            title: title.to_string(),
        }),
        Err(resp) => resp,
    }
}

async fn placeholder_bookings(session: Session) -> Response {
    render_placeholder(&session, "貨物予約一覧").await
}
async fn placeholder_tracking(session: Session) -> Response {
    render_placeholder(&session, "貨物追跡").await
}
async fn placeholder_handling(session: Session) -> Response {
    render_placeholder(&session, "荷役管理").await
}
async fn placeholder_estimates(session: Session) -> Response {
    render_placeholder(&session, "見積管理").await
}
async fn placeholder_voyages(session: Session) -> Response {
    render_placeholder(&session, "航路管理").await
}
async fn placeholder_billing(session: Session) -> Response {
    render_placeholder(&session, "請求管理").await
}
async fn placeholder_admin(session: Session) -> Response {
    render_placeholder(&session, "管理設定").await
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

/// 予約フォーム入力をドメインの `BookCargoCommand` に変換する。
fn build_command(form: &BookingForm) -> Result<BookCargoCommand, String> {
    let shipper_id =
        ShipperId::parse(form.shipper_id.trim()).map_err(|_| "荷主 ID が不正です".to_string())?;
    let origin =
        Location::new(form.origin.trim()).map_err(|_| "出発地の港コードが不正です".to_string())?;
    let destination = Location::new(form.destination.trim())
        .map_err(|_| "目的地の港コードが不正です".to_string())?;
    let deadline = NaiveDate::parse_from_str(form.arrival_deadline.trim(), "%Y-%m-%d")
        .map_err(|_| "到着期限の形式が不正です".to_string())?;
    let route = RouteSpecification::new(origin, destination, deadline)
        .map_err(|_| "出発地と目的地が同一です".to_string())?;
    let consignee = Consignee::new(form.consignee_name.trim(), form.consignee_contact.trim())
        .map_err(|_| "荷受人情報が不正です".to_string())?;
    let weight = Decimal::from_str(form.weight.trim())
        .ok()
        .and_then(|d| Weight::new(d).ok())
        .ok_or_else(|| "重量が不正です".to_string())?;
    let cargo_type = CargoType::from_str_or_general(&form.cargo_type);

    let hazardous_declaration = if cargo_type == CargoType::Hazardous {
        Some(
            HazardousDeclaration::new(
                form.hazardous_class.clone().unwrap_or_default(),
                form.un_number.clone().unwrap_or_default(),
                form.proper_shipping_name.clone().unwrap_or_default(),
            )
            .map_err(|_| "危険物申告情報が不正です".to_string())?,
        )
    } else {
        None
    };

    let temperature_requirement = if cargo_type == CargoType::Refrigerated {
        let min = form
            .min_temperature
            .as_deref()
            .and_then(|s| Decimal::from_str(s.trim()).ok())
            .ok_or_else(|| "最低温度が不正です".to_string())?;
        let max = form
            .max_temperature
            .as_deref()
            .and_then(|s| Decimal::from_str(s.trim()).ok())
            .ok_or_else(|| "最高温度が不正です".to_string())?;
        let unit = TemperatureUnit::from_str_or_celsius(
            form.temperature_unit.as_deref().unwrap_or("CELSIUS"),
        );
        Some(
            TemperatureRequirement::new(min, max, unit)
                .map_err(|_| "温度管理条件が不正です".to_string())?,
        )
    } else {
        None
    };

    Ok(BookCargoCommand {
        shipper_id,
        route_specification: route,
        consignee,
        cargo_type,
        weight,
        dimensions: None,
        quantity: None,
        description: None,
        hazardous_declaration,
        temperature_requirement,
    })
}

async fn booking_new_form(session: Session) -> Response {
    let current_user = match require_user(&session).await {
        Ok(u) => u,
        Err(resp) => return resp,
    };
    if !current_user.has_role("ROLE_SALES") {
        return StatusCode::FORBIDDEN.into_response();
    }
    render(&BookingNewTemplate {
        current_user,
        error: false,
        error_message: String::new(),
    })
}

async fn booking_create(
    State(state): State<AppState>,
    session: Session,
    Form(form): Form<BookingForm>,
) -> Response {
    let current_user = match require_user(&session).await {
        Ok(u) => u,
        Err(resp) => return resp,
    };
    if !current_user.has_role("ROLE_SALES") {
        return StatusCode::FORBIDDEN.into_response();
    }

    let render_error = |current_user: CurrentUser, message: String| -> Response {
        (
            StatusCode::OK,
            render(&BookingNewTemplate {
                current_user,
                error: true,
                error_message: message,
            }),
        )
            .into_response()
    };

    let command = match build_command(&form) {
        Ok(c) => c,
        Err(message) => return render_error(current_user, message),
    };

    let service = BookCargoCommandService::new(
        SqlxCargoRepository::new(state.pool.clone()),
        SqlxShipperExistenceChecker::new(state.pool.clone()),
    );
    match service.book(command).await {
        Ok(booking_id) => {
            Redirect::to(&format!("/bookings/{}", booking_id.as_str())).into_response()
        }
        Err(BookingServiceError::ShipperNotFound) => {
            render_error(current_user, "指定された荷主が存在しません".to_string())
        }
        Err(BookingServiceError::Domain(_)) => {
            render_error(current_user, "入力内容に誤りがあります".to_string())
        }
        Err(_) => render_error(current_user, "登録処理に失敗しました".to_string()),
    }
}

async fn booking_show(
    State(state): State<AppState>,
    session: Session,
    Path(booking_id): Path<String>,
) -> Response {
    let current_user = match require_user(&session).await {
        Ok(u) => u,
        Err(resp) => return resp,
    };

    use domain_booking::{BookingId, CargoRepository};
    let repo = SqlxCargoRepository::new(state.pool.clone());
    let id = match BookingId::parse(booking_id) {
        Ok(id) => id,
        Err(_) => return StatusCode::NOT_FOUND.into_response(),
    };
    match repo.find_by_booking_id(&id).await {
        Ok(Some(cargo)) => render(&BookingShowTemplate {
            current_user,
            booking_id: cargo.booking_id().as_str().to_string(),
            status: cargo.status().as_str().to_string(),
            origin: cargo.route_specification().origin().code().to_string(),
            destination: cargo.route_specification().destination().code().to_string(),
            cargo_type: cargo.cargo_type().as_str().to_string(),
        }),
        Ok(None) => StatusCode::NOT_FOUND.into_response(),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}
