//! Booking / Shipper Context の Web インターフェース層（Askama SSR + tower-sessions）。
//!
//! IT1 ではログイン認証・ロール別ナビゲーション・ダッシュボードのウォーキングスケルトンを提供する。

mod estimation_acl;
mod expected_voyages;
mod tracking_acl;

use app_booking::{BookCargoCommandService, BookingLifecycleService, BookingServiceError};
use app_routing::{
    MovementInput, RouteAdjustment, RoutePlanningService, VoyageCommandService, VoyageInput,
    VoyageQueryService, VoyageSearchInput, VoyageServiceError,
};
use app_shipper::{
    RegisterShipperCommandService, RegisterShipperInput, ShipperKindInput, ShipperServiceError,
};
use askama::Template;
use axum::Router;
use axum::extract::{Form, FromRequestParts, Path, Query, State};
use axum::http::StatusCode;
use axum::http::request::Parts;
use axum::response::{Html, IntoResponse, Redirect, Response};
use axum::routing::{get, post};
use chrono::{NaiveDate, NaiveDateTime, TimeZone, Utc};
use domain_booking::{
    BookCargoCommand, CargoRepository, CargoType, Consignee, HazardousDeclaration,
    NotificationPort, RouteSpecification, SelectedRouteView, ShipperExistenceChecker,
    TemperatureRequirement, TemperatureUnit, Weight,
};
use domain_routing::{
    CargoSpecProvider, RouteCandidate, SelectedRouteRepository, Voyage, VoyageRepository,
};
use domain_shipper::ShipperRepository;
use infra_persistence::{SqlxUserRepository, verify_password};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use shared_kernel::{Location, ShipperId};
use sqlx::PgPool;
use std::marker::PhantomData;
use std::str::FromStr;
use std::sync::Arc;
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

/// 認可 extractor が要求するロールを表すマーカー trait。
///
/// ハンドラ引数で `RoleGuard<SalesRole>` 等を受けることで、認可チェックの
/// 書き忘れをコンパイラが構造的に防ぐ（IT1 Try #1）。
pub trait RequiredRole: Send + Sync + 'static {
    /// 要求する `ROLE_` プレフィックス付きロール文字列。
    const ROLE: &'static str;
}

/// 営業担当者ロール。
pub struct SalesRole;
impl RequiredRole for SalesRole {
    const ROLE: &'static str = "ROLE_SALES";
}

/// 経路設計者ロール。
pub struct RouteDesignerRole;
impl RequiredRole for RouteDesignerRole {
    const ROLE: &'static str = "ROLE_ROUTE_DESIGNER";
}

/// 荷役作業員ロール。
pub struct HandlerRole;
impl RequiredRole for HandlerRole {
    const ROLE: &'static str = "ROLE_HANDLER";
}

/// 追跡管理者ロール。
pub struct TrackerRole;
impl RequiredRole for TrackerRole {
    const ROLE: &'static str = "ROLE_TRACKER";
}

/// ロール認可 extractor。認証・認可を型で保証し、ハンドラ本体から
/// `require_user` + `has_role` の重複を排除する。
pub struct RoleGuard<R: RequiredRole>(pub CurrentUser, PhantomData<R>);

impl<S, R> FromRequestParts<S> for RoleGuard<R>
where
    S: Send + Sync,
    R: RequiredRole,
{
    type Rejection = Response;

    async fn from_request_parts(parts: &mut Parts, state: &S) -> Result<Self, Self::Rejection> {
        let session = Session::from_request_parts(parts, state)
            .await
            .map_err(|_| Redirect::to("/login").into_response())?;
        match session.get::<CurrentUser>(SESSION_USER_KEY).await {
            Ok(Some(user)) if user.has_role(R::ROLE) => Ok(Self(user, PhantomData)),
            Ok(Some(_)) => Err(StatusCode::FORBIDDEN.into_response()),
            _ => Err(Redirect::to("/login").into_response()),
        }
    }
}

/// 営業担当者に認可されたユーザー。
type SalesUser = RoleGuard<SalesRole>;
/// 経路設計者に認可されたユーザー。
type RouteDesignerUser = RoleGuard<RouteDesignerRole>;
/// 荷役作業員に認可されたユーザー。
type HandlerUser = RoleGuard<HandlerRole>;
/// 追跡管理者に認可されたユーザー。
type TrackerUser = RoleGuard<TrackerRole>;

/// Web 層の共有状態。
///
/// リポジトリは出力ポート trait のトレイトオブジェクトとして保持し、
/// composition root（`cargo-tracker-server`）で sqlx 実装を注入する（ADR-0003）。
/// `pool` は認証（`SqlxUserRepository`）の特例用途にのみ残す。
#[derive(Clone)]
pub struct AppState {
    /// DB コネクションプール（認証専用。ADR-0003 の例外）。
    pub pool: PgPool,
    /// 荷主リポジトリ（出力ポート）。
    pub shipper_repo: Arc<dyn ShipperRepository>,
    /// 貨物リポジトリ（出力ポート）。
    pub cargo_repo: Arc<dyn CargoRepository>,
    /// 荷主存在確認 ACL（出力ポート）。
    pub shipper_checker: Arc<dyn ShipperExistenceChecker>,
    /// 航海リポジトリ（出力ポート）。
    pub voyage_repo: Arc<dyn VoyageRepository>,
    /// 貨物仕様プロバイダ ACL（Routing → Booking の予約仕様参照・出力ポート）。
    pub cargo_spec_provider: Arc<dyn CargoSpecProvider>,
    /// 確定経路リポジトリ（US09・出力ポート）。
    pub selected_route_repo: Arc<dyn SelectedRouteRepository>,
    /// 通知ポート（US06/US12/US13・送信＝記録・出力ポート）。
    pub notification_port: Arc<dyn NotificationPort>,
    /// 確定経路の読み取りビュー ACL（US11/US12・Booking → Routing 逆方向・出力ポート）。
    pub selected_route_view: Arc<dyn SelectedRouteView>,
    /// 追跡活動リポジトリ（US14/US15/US17・出力ポート）。
    pub tracking_repo: Arc<dyn domain_tracking::TrackingActivityRepository>,
    /// 荷役作業リポジトリ（US15/US16・出力ポート）。
    pub handling_repo: Arc<dyn domain_handling::HandlingActivityRepository>,
    /// 見積リポジトリ（US01・出力ポート）。
    pub estimate_repo: Arc<dyn domain_estimation::EstimateRepository>,
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
    status_label: String,
    origin: String,
    destination: String,
    cargo_type: String,
    /// 状態別の操作可否（テンプレートのボタン活性条件）。
    is_preliminary: bool,
    is_route_designing: bool,
    is_route_proposed: bool,
    /// 予約確定済み（US14 追跡番号発行の活性条件）。
    is_confirmed: bool,
    /// 追跡番号発行済み（発行済み表示・追跡導線の活性条件）。
    is_tracking_issued: bool,
    /// 確定経路の要約（US11 で紐付け済みなら表示）。
    has_route: bool,
    route_voyages: String,
    route_transit_ports: String,
    route_transit_days: i64,
    route_arrival: String,
}

/// 予約状態の日本語ラベル。ui_design.md 付録「ステータス対応表（正典）」に準拠する。
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
        .route("/bookings", get(booking_list).post(booking_create))
        .route("/bookings/{booking_id}", get(booking_show))
        .route("/tracking", get(tracking_input))
        .route("/tracking/{tracking_number}", get(tracking_detail))
        .route("/tracking/{tracking_number}/updates", post(tracking_update))
        .route("/handling", get(handling_list).post(handling_create))
        .route("/handling/new", get(handling_new_form))
        .route("/estimates", get(estimate_list).post(estimate_create))
        .route("/estimates/new", get(estimate_new_form))
        .route("/estimates/{estimate_id}", get(estimate_show))
        .route("/voyages", get(voyage_list).post(voyage_create))
        .route("/voyages/new", get(voyage_new_form))
        .route("/voyages/{voyage_number}/edit", get(voyage_edit_form))
        .route("/voyages/{voyage_number}", post(voyage_update))
        .route("/bookings/{booking_id}/route", get(route_design))
        .route("/bookings/{booking_id}/route/confirm", post(route_confirm))
        .route("/bookings/{booking_id}/route/adjust", post(route_adjust))
        .route(
            "/bookings/{booking_id}/consult-shipper",
            post(consult_shipper),
        )
        .route(
            "/bookings/{booking_id}/assign-routing",
            post(booking_assign_routing),
        )
        .route(
            "/bookings/{booking_id}/notify-route",
            post(booking_notify_route),
        )
        .route("/bookings/{booking_id}/confirm", post(booking_confirm))
        .route("/bookings/{booking_id}/revert", post(booking_revert))
        .route("/bookings/{booking_id}/cancel", post(booking_cancel))
        .route(
            "/bookings/{booking_id}/issue-tracking-number",
            post(issue_tracking_number),
        )
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

/// 予約一覧の 1 行分の表示データ。
struct BookingRow {
    booking_id: String,
    status: String,
    status_label: String,
    origin: String,
    destination: String,
    cargo_type: String,
}

#[derive(Template)]
#[template(path = "booking_list.html")]
struct BookingListTemplate {
    current_user: CurrentUser,
    bookings: Vec<BookingRow>,
}

/// 貨物予約一覧（navbar「貨物予約」の遷移先）。詳細画面への導線を提供する。
async fn booking_list(State(state): State<AppState>, session: Session) -> Response {
    let current_user = match require_user(&session).await {
        Ok(u) => u,
        Err(resp) => return resp,
    };
    match state.cargo_repo.find_all().await {
        Ok(cargos) => {
            let bookings = cargos
                .iter()
                .map(|c| {
                    let status_enum = c.status();
                    let status = status_enum.as_str().to_string();
                    BookingRow {
                        booking_id: c.booking_id().as_str().to_string(),
                        status_label: status_enum.label().to_string(),
                        status,
                        origin: c.route_specification().origin().code().to_string(),
                        destination: c.route_specification().destination().code().to_string(),
                        cargo_type: c.cargo_type().as_str().to_string(),
                    }
                })
                .collect();
            render(&BookingListTemplate {
                current_user,
                bookings,
            })
        }
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}
async fn placeholder_billing(session: Session) -> Response {
    render_placeholder(&session, "請求管理").await
}
async fn placeholder_admin(session: Session) -> Response {
    render_placeholder(&session, "管理設定").await
}

async fn shipper_new_form(RoleGuard(current_user, _): SalesUser) -> Response {
    render(&ShipperNewTemplate {
        current_user,
        error: false,
        error_message: String::new(),
    })
}

async fn shipper_create(
    State(state): State<AppState>,
    RoleGuard(current_user, _): SalesUser,
    Form(form): Form<ShipperForm>,
) -> Response {
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

    let service = RegisterShipperCommandService::new(state.shipper_repo.clone());
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

async fn booking_new_form(RoleGuard(current_user, _): SalesUser) -> Response {
    render(&BookingNewTemplate {
        current_user,
        error: false,
        error_message: String::new(),
    })
}

async fn booking_create(
    State(state): State<AppState>,
    RoleGuard(current_user, _): SalesUser,
    Form(form): Form<BookingForm>,
) -> Response {
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

    let service =
        BookCargoCommandService::new(state.cargo_repo.clone(), state.shipper_checker.clone());
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
    let repo = state.cargo_repo.clone();
    let id = match BookingId::parse(&booking_id) {
        Ok(id) => id,
        Err(_) => return StatusCode::NOT_FOUND.into_response(),
    };
    let cargo = match repo.find_by_booking_id(&id).await {
        Ok(Some(cargo)) => cargo,
        Ok(None) => return StatusCode::NOT_FOUND.into_response(),
        Err(_) => return StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    };
    let status_enum = cargo.status();
    let status = status_enum.as_str().to_string();
    // 確定経路の要約を読み取る（US11 で紐付け済みなら表示・BC 独立 ACL 経由）。
    let route = state
        .selected_route_view
        .find_by_booking(&booking_id)
        .await
        .ok()
        .flatten();
    render(&BookingShowTemplate {
        current_user,
        booking_id: cargo.booking_id().as_str().to_string(),
        status_label: status_enum.label().to_string(),
        is_preliminary: status_enum.is_preliminary(),
        is_route_designing: status_enum.is_route_designing(),
        is_route_proposed: status_enum.is_route_proposed(),
        is_confirmed: status_enum.is_confirmed(),
        is_tracking_issued: matches!(status_enum, domain_booking::BookingStatus::TrackingIssued),
        status,
        origin: cargo.route_specification().origin().code().to_string(),
        destination: cargo.route_specification().destination().code().to_string(),
        cargo_type: cargo.cargo_type().as_str().to_string(),
        has_route: route.is_some(),
        route_voyages: route
            .as_ref()
            .map(|r| r.voyage_numbers.join(" → "))
            .unwrap_or_default(),
        route_transit_ports: route
            .as_ref()
            .map(|r| r.transit_ports.join(" → "))
            .unwrap_or_default(),
        route_transit_days: route.as_ref().map(|r| r.transit_days).unwrap_or_default(),
        route_arrival: route.map(|r| r.expected_arrival).unwrap_or_default(),
    })
}

// ===== Routing Context（航路管理・US24 / US25 / US07）=====

/// 航路一覧の 1 行分の表示データ。
struct VoyageRow {
    number: String,
    vessel_name: String,
    carrier: String,
    origin: String,
    destination: String,
    departure: String,
    arrival: String,
    cargo_types: String,
}

/// 航路フォームのプリフィル / 再描画用データ。
#[derive(Default)]
struct VoyageFormData {
    voyage_number: String,
    vessel_name: String,
    carrier: String,
    general: bool,
    hazardous: bool,
    refrigerated: bool,
    leg1_departure: String,
    leg1_arrival: String,
    leg1_departure_time: String,
    leg1_arrival_time: String,
    leg2_departure: String,
    leg2_arrival: String,
    leg2_departure_time: String,
    leg2_arrival_time: String,
}

#[derive(Template)]
#[template(path = "voyage_list.html")]
struct VoyageListTemplate {
    current_user: CurrentUser,
    voyages: Vec<VoyageRow>,
    origin: String,
    destination: String,
    cargo_type_general: bool,
    cargo_type_hazardous: bool,
    cargo_type_refrigerated: bool,
    flash: String,
}

#[derive(Template)]
#[template(path = "voyage_new.html")]
struct VoyageNewTemplate {
    current_user: CurrentUser,
    error: bool,
    error_message: String,
    form: VoyageFormData,
}

#[derive(Template)]
#[template(path = "voyage_edit.html")]
struct VoyageEditTemplate {
    current_user: CurrentUser,
    error: bool,
    error_message: String,
    form: VoyageFormData,
    current: VoyageRow,
}

/// 航路検索クエリ（US07）。
#[derive(Debug, Deserialize)]
pub struct VoyageSearchQuery {
    origin: Option<String>,
    destination: Option<String>,
    cargo_type: Option<String>,
    #[serde(default)]
    flash: Option<String>,
}

/// 航路登録 / 更新フォーム入力。
#[derive(Debug, Deserialize)]
pub struct VoyageForm {
    voyage_number: String,
    vessel_name: String,
    carrier: String,
    cargo_general: Option<String>,
    cargo_hazardous: Option<String>,
    cargo_refrigerated: Option<String>,
    leg1_departure: String,
    leg1_arrival: String,
    leg1_departure_time: String,
    leg1_arrival_time: String,
    leg2_departure: Option<String>,
    leg2_arrival: Option<String>,
    leg2_departure_time: Option<String>,
    leg2_arrival_time: Option<String>,
}

fn cargo_types_label(types: &[domain_routing::CargoType]) -> String {
    use domain_routing::CargoType as Ct;
    // enum で直接 match し、バリアント追加時の考慮漏れをコンパイラに検出させる
    types
        .iter()
        .map(|t| match t {
            Ct::General => "一般",
            Ct::Hazardous => "危険物",
            Ct::Refrigerated => "冷凍・冷蔵",
        })
        .collect::<Vec<_>>()
        .join("、")
}

fn voyage_to_row(v: &Voyage) -> VoyageRow {
    let movements = v.schedule().carrier_movements();
    let first = &movements[0];
    let last = &movements[movements.len() - 1];
    VoyageRow {
        number: v.voyage_number().as_str().to_string(),
        vessel_name: v.vessel_name().as_str().to_string(),
        carrier: v.carrier().as_str().to_string(),
        origin: v.origin().code().to_string(),
        destination: v.destination().code().to_string(),
        departure: first.departure_time().format("%Y-%m-%d %H:%M").to_string(),
        arrival: last.arrival_time().format("%Y-%m-%d %H:%M").to_string(),
        cargo_types: cargo_types_label(v.supported_cargo_types()),
    }
}

fn voyage_to_form_data(v: &Voyage) -> VoyageFormData {
    let movements = v.schedule().carrier_movements();
    let fmt = |dt: chrono::DateTime<Utc>| dt.format("%Y-%m-%dT%H:%M").to_string();
    let leg1 = &movements[0];
    let mut data = VoyageFormData {
        voyage_number: v.voyage_number().as_str().to_string(),
        vessel_name: v.vessel_name().as_str().to_string(),
        carrier: v.carrier().as_str().to_string(),
        general: v.supports(domain_routing::CargoType::General),
        hazardous: v.supports(domain_routing::CargoType::Hazardous),
        refrigerated: v.supports(domain_routing::CargoType::Refrigerated),
        leg1_departure: leg1.departure_location().code().to_string(),
        leg1_arrival: leg1.arrival_location().code().to_string(),
        leg1_departure_time: fmt(leg1.departure_time()),
        leg1_arrival_time: fmt(leg1.arrival_time()),
        ..VoyageFormData::default()
    };
    if let Some(leg2) = movements.get(1) {
        data.leg2_departure = leg2.departure_location().code().to_string();
        data.leg2_arrival = leg2.arrival_location().code().to_string();
        data.leg2_departure_time = fmt(leg2.departure_time());
        data.leg2_arrival_time = fmt(leg2.arrival_time());
    }
    data
}

fn parse_local_dt(value: &str) -> Result<chrono::DateTime<Utc>, String> {
    NaiveDateTime::parse_from_str(value.trim(), "%Y-%m-%dT%H:%M")
        .map(|naive| Utc.from_utc_datetime(&naive))
        .map_err(|_| "日時の形式が不正です".to_string())
}

fn form_to_data(form: &VoyageForm) -> VoyageFormData {
    VoyageFormData {
        voyage_number: form.voyage_number.clone(),
        vessel_name: form.vessel_name.clone(),
        carrier: form.carrier.clone(),
        general: form.cargo_general.is_some(),
        hazardous: form.cargo_hazardous.is_some(),
        refrigerated: form.cargo_refrigerated.is_some(),
        leg1_departure: form.leg1_departure.clone(),
        leg1_arrival: form.leg1_arrival.clone(),
        leg1_departure_time: form.leg1_departure_time.clone(),
        leg1_arrival_time: form.leg1_arrival_time.clone(),
        leg2_departure: form.leg2_departure.clone().unwrap_or_default(),
        leg2_arrival: form.leg2_arrival.clone().unwrap_or_default(),
        leg2_departure_time: form.leg2_departure_time.clone().unwrap_or_default(),
        leg2_arrival_time: form.leg2_arrival_time.clone().unwrap_or_default(),
    }
}

fn build_voyage_input(form: &VoyageForm) -> Result<VoyageInput, String> {
    let mut cargo_types = Vec::new();
    if form.cargo_general.is_some() {
        cargo_types.push("GENERAL".to_string());
    }
    if form.cargo_hazardous.is_some() {
        cargo_types.push("HAZARDOUS".to_string());
    }
    if form.cargo_refrigerated.is_some() {
        cargo_types.push("REFRIGERATED".to_string());
    }

    let mut movements = vec![MovementInput {
        departure_unlocode: form.leg1_departure.trim().to_string(),
        arrival_unlocode: form.leg1_arrival.trim().to_string(),
        departure_time: parse_local_dt(&form.leg1_departure_time)?,
        arrival_time: parse_local_dt(&form.leg1_arrival_time)?,
    }];

    let leg2_dep = form.leg2_departure.as_deref().unwrap_or("").trim();
    let leg2_arr = form.leg2_arrival.as_deref().unwrap_or("").trim();
    let leg2_dep_t = form.leg2_departure_time.as_deref().unwrap_or("").trim();
    let leg2_arr_t = form.leg2_arrival_time.as_deref().unwrap_or("").trim();
    if !leg2_dep.is_empty()
        || !leg2_arr.is_empty()
        || !leg2_dep_t.is_empty()
        || !leg2_arr_t.is_empty()
    {
        movements.push(MovementInput {
            departure_unlocode: leg2_dep.to_string(),
            arrival_unlocode: leg2_arr.to_string(),
            departure_time: parse_local_dt(leg2_dep_t)?,
            arrival_time: parse_local_dt(leg2_arr_t)?,
        });
    }

    Ok(VoyageInput {
        voyage_number: form.voyage_number.trim().to_string(),
        vessel_name: form.vessel_name.trim().to_string(),
        carrier: form.carrier.trim().to_string(),
        cargo_types,
        movements,
    })
}

fn voyage_error_message(e: &VoyageServiceError) -> String {
    match e {
        VoyageServiceError::AlreadyExists(_) => "この航海番号は既に登録されています".to_string(),
        VoyageServiceError::NotFound(_) => "対象の航海が見つかりません".to_string(),
        VoyageServiceError::Domain(_)
        | VoyageServiceError::Location(_)
        | VoyageServiceError::InvalidDate(_) => {
            "入力内容に誤りがあります（港コード・日時・スケジュール順序を確認してください）"
                .to_string()
        }
        VoyageServiceError::BookingNotFound(_) => "対象の予約が見つかりません".to_string(),
        VoyageServiceError::InvalidCandidate(_) => "選択した経路候補が不正です".to_string(),
        VoyageServiceError::CandidateExpired => {
            "期限超過の経路は確定できません。条件を調整してください".to_string()
        }
        VoyageServiceError::CandidateMismatch => {
            "表示時と経路候補が変化しました。再度確認してください".to_string()
        }
        VoyageServiceError::Repository(_) | VoyageServiceError::Acl(_) => {
            "処理に失敗しました".to_string()
        }
    }
}

async fn voyage_list(
    State(state): State<AppState>,
    RoleGuard(current_user, _): RouteDesignerUser,
    Query(query): Query<VoyageSearchQuery>,
) -> Response {
    let service = VoyageQueryService::new(state.voyage_repo.clone());
    let origin = query.origin.clone().unwrap_or_default();
    let destination = query.destination.clone().unwrap_or_default();
    let cargo_type = query.cargo_type.clone().unwrap_or_default();
    let cargo_type_general = cargo_type == "GENERAL";
    let cargo_type_hazardous = cargo_type == "HAZARDOUS";
    let cargo_type_refrigerated = cargo_type == "REFRIGERATED";

    let search = VoyageSearchInput {
        origin: non_empty(query.origin),
        destination: non_empty(query.destination),
        cargo_type: non_empty(query.cargo_type),
        ..Default::default()
    };
    let result =
        if search.origin.is_none() && search.destination.is_none() && search.cargo_type.is_none() {
            service.list().await
        } else {
            service.search(search).await
        };

    match result {
        Ok(voyages) => render(&VoyageListTemplate {
            current_user,
            voyages: voyages.iter().map(voyage_to_row).collect(),
            origin,
            destination,
            cargo_type_general,
            cargo_type_hazardous,
            cargo_type_refrigerated,
            flash: query.flash.unwrap_or_default(),
        }),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

async fn voyage_new_form(RoleGuard(current_user, _): RouteDesignerUser) -> Response {
    render(&VoyageNewTemplate {
        current_user,
        error: false,
        error_message: String::new(),
        form: VoyageFormData::default(),
    })
}

async fn voyage_create(
    State(state): State<AppState>,
    RoleGuard(current_user, _): RouteDesignerUser,
    Form(form): Form<VoyageForm>,
) -> Response {
    let render_error = |current_user: CurrentUser, message: String| -> Response {
        (
            StatusCode::UNPROCESSABLE_ENTITY,
            render(&VoyageNewTemplate {
                current_user,
                error: true,
                error_message: message,
                form: form_to_data(&form),
            }),
        )
            .into_response()
    };

    let input = match build_voyage_input(&form) {
        Ok(input) => input,
        Err(message) => return render_error(current_user, message),
    };

    let service = VoyageCommandService::new(state.voyage_repo.clone());
    match service.register(input).await {
        Ok(_) => Redirect::to("/voyages?flash=%E8%88%AA%E6%B5%B7%E3%82%92%E7%99%BB%E9%8C%B2%E3%81%97%E3%81%BE%E3%81%97%E3%81%9F").into_response(),
        Err(e) => {
            let message = voyage_error_message(&e);
            render_error(current_user, message)
        }
    }
}

async fn voyage_edit_form(
    State(state): State<AppState>,
    RoleGuard(current_user, _): RouteDesignerUser,
    Path(voyage_number): Path<String>,
) -> Response {
    let service = VoyageQueryService::new(state.voyage_repo.clone());
    match service.find(&voyage_number).await {
        Ok(Some(voyage)) => render(&VoyageEditTemplate {
            current_user,
            error: false,
            error_message: String::new(),
            form: voyage_to_form_data(&voyage),
            current: voyage_to_row(&voyage),
        }),
        Ok(None) => StatusCode::NOT_FOUND.into_response(),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

async fn voyage_update(
    State(state): State<AppState>,
    RoleGuard(current_user, _): RouteDesignerUser,
    Path(voyage_number): Path<String>,
    Form(form): Form<VoyageForm>,
) -> Response {
    let query = VoyageQueryService::new(state.voyage_repo.clone());
    let current_row = match query.find(&voyage_number).await {
        Ok(Some(voyage)) => voyage_to_row(&voyage),
        Ok(None) => return StatusCode::NOT_FOUND.into_response(),
        Err(_) => return StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    };

    let render_error =
        |current_user: CurrentUser, message: String, current: VoyageRow| -> Response {
            (
                StatusCode::UNPROCESSABLE_ENTITY,
                render(&VoyageEditTemplate {
                    current_user,
                    error: true,
                    error_message: message,
                    form: form_to_data(&form),
                    current,
                }),
            )
                .into_response()
        };

    let input = match build_voyage_input(&form) {
        Ok(input) => input,
        Err(message) => return render_error(current_user, message, current_row),
    };

    let service = VoyageCommandService::new(state.voyage_repo.clone());
    match service.update(input).await {
        Ok(_) => Redirect::to("/voyages?flash=%E8%88%AA%E6%B5%B7%E3%82%92%E6%9B%B4%E6%96%B0%E3%81%97%E3%81%BE%E3%81%97%E3%81%9F").into_response(),
        Err(e) => {
            let message = voyage_error_message(&e);
            render_error(current_user, message, current_row)
        }
    }
}

// ===== Routing Context（経路設計・割り当て・US08 / US09）=====

/// 経路候補 1 件の表示データ。
struct RouteRow {
    direct: bool,
    transit_ports_label: String,
    transit_ports: Vec<String>,
    transit_days: i64,
    voyage_label: String,
    /// 航海番号列のカンマ区切り（TOCTOU 照合の hidden 入力用）。
    voyage_csv: String,
    arrival: String,
    within_deadline: bool,
}

#[derive(Template)]
#[template(path = "route_design.html")]
struct RouteDesignTemplate {
    current_user: CurrentUser,
    error: bool,
    error_message: String,
    booking_id: String,
    origin: String,
    destination: String,
    arrival_deadline: String,
    cargo_type: String,
    candidates: Vec<RouteRow>,
    /// TOCTOU 照合用の期待航海番号列（`;` 区切り・集約ヘルパーで生成）。
    expected_voyages_list: String,
}

impl RouteDesignTemplate {
    /// 経路候補算出成功時のテンプレートを組み立てる（route_design / route_adjust 共通）。
    fn from_candidates(
        current_user: CurrentUser,
        booking_id: String,
        spec: &domain_routing::CargoSpec,
        candidates: &[RouteCandidate],
    ) -> Self {
        let rows: Vec<RouteRow> = candidates
            .iter()
            .map(|c| route_row(c, spec.arrival_deadline))
            .collect();
        let expected_voyages_list = expected_voyages::encode_list(
            &rows
                .iter()
                .map(|r| r.voyage_csv.clone())
                .collect::<Vec<_>>(),
        );
        Self {
            current_user,
            error: false,
            error_message: String::new(),
            booking_id,
            origin: spec.origin.code().to_string(),
            destination: spec.destination.code().to_string(),
            arrival_deadline: spec.arrival_deadline.format("%Y-%m-%d").to_string(),
            cargo_type: spec.cargo_type.as_str().to_string(),
            candidates: rows,
            expected_voyages_list,
        }
    }
}

fn route_row(c: &RouteCandidate, deadline: chrono::NaiveDate) -> RouteRow {
    let transit_ports: Vec<String> = c
        .transit_ports()
        .iter()
        .map(|l| l.code().to_string())
        .collect();
    RouteRow {
        direct: c.leg_count() == 1,
        transit_ports_label: transit_ports.join(" → "),
        transit_ports,
        transit_days: c.transit_days(),
        voyage_label: c
            .voyage_numbers()
            .iter()
            .map(|v| v.as_str().to_string())
            .collect::<Vec<_>>()
            .join(" → "),
        voyage_csv: expected_voyages::encode_candidate(
            &c.voyage_numbers()
                .iter()
                .map(|v| v.as_str().to_string())
                .collect::<Vec<_>>(),
        ),
        arrival: c.expected_arrival().format("%Y-%m-%d").to_string(),
        within_deadline: c.within_deadline(deadline),
    }
}

/// US10: 経路条件調整フォーム入力。
#[derive(Debug, Deserialize)]
pub struct RouteAdjustForm {
    /// 期限延長日数（空可）。
    #[serde(default)]
    extend_deadline_days: String,
    /// 貨物種別の上書き（空可）。
    #[serde(default)]
    cargo_type: String,
}

/// US10: 条件を調整して経路候補を再算出する。
async fn route_adjust(
    State(state): State<AppState>,
    RoleGuard(current_user, _): RouteDesignerUser,
    Path(booking_id): Path<String>,
    Form(form): Form<RouteAdjustForm>,
) -> Response {
    let service = RoutePlanningService::new(
        state.voyage_repo.clone(),
        state.cargo_spec_provider.clone(),
        state.selected_route_repo.clone(),
    );
    let adjustment = RouteAdjustment {
        extend_deadline_days: form
            .extend_deadline_days
            .trim()
            .parse::<i64>()
            .ok()
            .filter(|d| *d != 0),
        cargo_type_override: Some(form.cargo_type.trim().to_string()).filter(|s| !s.is_empty()),
    };
    match service.plan_routes_adjusted(&booking_id, &adjustment).await {
        Ok((spec, candidates)) => render(&RouteDesignTemplate::from_candidates(
            current_user,
            booking_id,
            &spec,
            &candidates,
        )),
        Err(VoyageServiceError::BookingNotFound(_)) => StatusCode::NOT_FOUND.into_response(),
        Err(VoyageServiceError::Domain(_)) => StatusCode::UNPROCESSABLE_ENTITY.into_response(),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

async fn route_design(
    State(state): State<AppState>,
    RoleGuard(current_user, _): RouteDesignerUser,
    Path(booking_id): Path<String>,
) -> Response {
    let service = RoutePlanningService::new(
        state.voyage_repo.clone(),
        state.cargo_spec_provider.clone(),
        state.selected_route_repo.clone(),
    );
    match service.plan_routes(&booking_id).await {
        Ok((spec, candidates)) => render(&RouteDesignTemplate::from_candidates(
            current_user,
            booking_id,
            &spec,
            &candidates,
        )),
        Err(VoyageServiceError::BookingNotFound(_)) => StatusCode::NOT_FOUND.into_response(),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

/// 経路確定フォーム入力（US09/US11）。
#[derive(Debug, Deserialize)]
pub struct RouteConfirmForm {
    candidate_index: usize,
    /// 表示時の全候補の航海番号列（候補ごとにカンマ区切り・候補間はセミコロン区切り）。
    /// 選択インデックスの要素を取り出して確定時の候補と照合する（TOCTOU 対策・IT3 Try #4）。
    #[serde(default)]
    expected_voyages_list: String,
}

/// `AppState` から予約ライフサイクルサービスを構成する（US06/US11/US12/US13）。
fn lifecycle_service(
    state: &AppState,
) -> BookingLifecycleService<
    Arc<dyn CargoRepository>,
    Arc<dyn NotificationPort>,
    Arc<dyn SelectedRouteView>,
> {
    BookingLifecycleService::new(
        state.cargo_repo.clone(),
        state.notification_port.clone(),
        state.selected_route_view.clone(),
    )
}

async fn route_confirm(
    State(state): State<AppState>,
    RoleGuard(_current_user, _): RouteDesignerUser,
    Path(booking_id): Path<String>,
    Form(form): Form<RouteConfirmForm>,
) -> Response {
    let service = RoutePlanningService::new(
        state.voyage_repo.clone(),
        state.cargo_spec_provider.clone(),
        state.selected_route_repo.clone(),
    );
    // 選択インデックスに対応する候補の航海番号列を取り出す（TOCTOU 照合用・集約ヘルパー経由）。
    let expected =
        expected_voyages::decode_selected(&form.expected_voyages_list, form.candidate_index);
    // US09: 選択候補を確定し確定経路を永続化する（期限超過拒否・候補同一性照合を含む）。
    match service
        .confirm_route(&booking_id, form.candidate_index, &expected)
        .await
    {
        Ok(()) => {}
        Err(VoyageServiceError::BookingNotFound(_)) => {
            return StatusCode::NOT_FOUND.into_response();
        }
        Err(
            VoyageServiceError::InvalidCandidate(_)
            | VoyageServiceError::CandidateExpired
            | VoyageServiceError::CandidateMismatch,
        ) => return StatusCode::UNPROCESSABLE_ENTITY.into_response(),
        Err(_) => return StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
    // US11: 予約を経路提案中へ遷移する（BC 独立のライフサイクルサービス経由）。
    match lifecycle_service(&state).propose_route(&booking_id).await {
        Ok(()) => Redirect::to(&format!("/bookings/{booking_id}")).into_response(),
        Err(BookingServiceError::NotFound(_)) => StatusCode::NOT_FOUND.into_response(),
        Err(BookingServiceError::Domain(_)) => StatusCode::UNPROCESSABLE_ENTITY.into_response(),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

/// US10: 期限内経路が無い場合に荷主との条件協議を依頼する（通知記録・実導線）。
///
/// IT4 Try#3 返済。案内文のみだった 0 件時導線を、荷主への協議依頼通知を記録する実導線にする。
/// 状態遷移は伴わず（経路設計中のまま）、条件調整の再算出を促す。
async fn consult_shipper(
    State(state): State<AppState>,
    RoleGuard(_u, _): RouteDesignerUser,
    Path(booking_id): Path<String>,
) -> Response {
    use domain_booking::{BookingId, CargoRepository};
    let id = match BookingId::parse(&booking_id) {
        Ok(id) => id,
        Err(_) => return StatusCode::NOT_FOUND.into_response(),
    };
    let cargo = match state.cargo_repo.find_by_booking_id(&id).await {
        Ok(Some(c)) => c,
        Ok(None) => return StatusCode::NOT_FOUND.into_response(),
        Err(_) => return StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    };
    // 荷主への条件協議依頼を通知テーブルへ記録（送信＝記録）。
    let result = sqlx::query(
        r"INSERT INTO notification
            (booking_id, notification_type, recipient_role, recipient_email, subject, body)
          VALUES ($1, 'SHIPPER_CONSULTATION_REQUESTED', 'ROLE_SHIPPER', $2, $3, $4)",
    )
    .bind(cargo.booking_id().as_str())
    .bind(cargo.consignee().contact())
    .bind("輸送条件のご相談")
    .bind(format!(
        "予約 {} について、期限内に到達可能な経路がありませんでした。到着期限や条件のご相談をお願いします。",
        cargo.booking_id().as_str()
    ))
    .execute(&state.pool)
    .await;
    match result {
        Ok(_) => Redirect::to(&format!("/bookings/{booking_id}/route")).into_response(),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

/// 予約状態遷移操作（US06/US12/US13）の POST ハンドラ共通処理。
async fn lifecycle_action<F, Fut>(state: AppState, booking_id: String, action: F) -> Response
where
    F: FnOnce(
        BookingLifecycleService<
            Arc<dyn CargoRepository>,
            Arc<dyn NotificationPort>,
            Arc<dyn SelectedRouteView>,
        >,
        String,
    ) -> Fut,
    Fut: std::future::Future<Output = Result<(), BookingServiceError>>,
{
    match action(lifecycle_service(&state), booking_id.clone()).await {
        Ok(()) => Redirect::to(&format!("/bookings/{booking_id}")).into_response(),
        Err(BookingServiceError::NotFound(_)) => StatusCode::NOT_FOUND.into_response(),
        Err(BookingServiceError::Domain(_)) => StatusCode::UNPROCESSABLE_ENTITY.into_response(),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

/// US06: 経路設計依頼（`Preliminary → RouteDesigning`＋経路設計者通知）。
async fn booking_assign_routing(
    State(state): State<AppState>,
    RoleGuard(_u, _): SalesUser,
    Path(booking_id): Path<String>,
) -> Response {
    lifecycle_action(state, booking_id, |svc, id| async move {
        svc.request_route_design(&id).await
    })
    .await
}

/// US12: 荷主への経路通知（確定経路要約から通知記録）。
async fn booking_notify_route(
    State(state): State<AppState>,
    RoleGuard(_u, _): SalesUser,
    Path(booking_id): Path<String>,
) -> Response {
    lifecycle_action(state, booking_id, |svc, id| async move {
        svc.notify_route_to_shipper(&id).await
    })
    .await
}

/// US13: 予約確定（`RouteProposed → Confirmed`＋追跡番号発行依頼通知）。
async fn booking_confirm(
    State(state): State<AppState>,
    RoleGuard(_u, _): SalesUser,
    Path(booking_id): Path<String>,
) -> Response {
    lifecycle_action(state, booking_id, |svc, id| async move {
        svc.confirm(&id).await
    })
    .await
}

/// US13: ルート変更差し戻し（`RouteProposed → RouteDesigning`）。
async fn booking_revert(
    State(state): State<AppState>,
    RoleGuard(_u, _): SalesUser,
    Path(booking_id): Path<String>,
) -> Response {
    lifecycle_action(state, booking_id, |svc, id| async move {
        svc.revert_to_route_designing(&id).await
    })
    .await
}

/// US13: 予約キャンセル（`→ Cancelled`＋荷主キャンセル確認通知）。
async fn booking_cancel(
    State(state): State<AppState>,
    RoleGuard(_u, _): SalesUser,
    Path(booking_id): Path<String>,
) -> Response {
    lifecycle_action(
        state,
        booking_id,
        |svc, id| async move { svc.cancel(&id).await },
    )
    .await
}

/// US14: 追跡番号を発行する（`Confirmed → TrackingIssued`＋追跡活動生成＋荷主通知）。
///
/// 経路設計者のみが実行可能。ACL（`CargoConfirmedBookingIssuer`）で Booking を先に遷移させ、
/// `IssueTrackingService` が追跡番号採番・`TrackingActivity` 保存・荷主通知を行う（ADR-0004）。
async fn issue_tracking_number(
    State(state): State<AppState>,
    RoleGuard(_u, _): RouteDesignerUser,
    Path(booking_id): Path<String>,
) -> Response {
    use app_tracking::IssueTrackingService;
    use domain_tracking::UuidTrackingNumberGenerator;
    use tracking_acl::{CargoConfirmedBookingIssuer, SqlxTrackingNotificationPort};

    let service = IssueTrackingService::new(
        state.tracking_repo.clone(),
        UuidTrackingNumberGenerator,
        CargoConfirmedBookingIssuer::new(state.cargo_repo.clone()),
        SqlxTrackingNotificationPort::new(state.pool.clone()),
    );
    match service.issue_tracking(&booking_id).await {
        Ok(_) => Redirect::to(&format!("/bookings/{booking_id}")).into_response(),
        Err(app_tracking::TrackingServiceError::NotFound(_)) => {
            StatusCode::NOT_FOUND.into_response()
        }
        Err(app_tracking::TrackingServiceError::NotIssuable(_)) => {
            StatusCode::UNPROCESSABLE_ENTITY.into_response()
        }
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

// ===== Handling Context（荷役・引取記録・US15 / US16）=====

#[derive(Template)]
#[template(path = "handling_list.html")]
struct HandlingListTemplate {
    current_user: CurrentUser,
}

#[derive(Template)]
#[template(path = "handling_new.html")]
struct HandlingNewTemplate {
    current_user: CurrentUser,
    error: bool,
    error_message: String,
    warning: bool,
    warning_message: String,
}

/// 荷役登録フォーム入力（US15/US16）。
#[derive(Debug, Deserialize)]
pub struct HandlingForm {
    tracking_number: String,
    handling_type: String,
    un_locode: String,
    completion_time: String,
    voyage_number: Option<String>,
    operator_name: Option<String>,
    receipt_confirmation: Option<String>,
}

/// 荷役管理一覧（US15）。
async fn handling_list(session: Session) -> Response {
    let current_user = match require_user(&session).await {
        Ok(u) => u,
        Err(resp) => return resp,
    };
    render(&HandlingListTemplate { current_user })
}

/// 荷役登録フォーム（US15/US16・荷役作業員のみ）。
async fn handling_new_form(RoleGuard(current_user, _): HandlerUser) -> Response {
    render(&HandlingNewTemplate {
        current_user,
        error: false,
        error_message: String::new(),
        warning: false,
        warning_message: String::new(),
    })
}

/// 荷役作業を記録する（US15/US16）。
async fn handling_create(
    State(state): State<AppState>,
    RoleGuard(current_user, _): HandlerUser,
    Form(form): Form<HandlingForm>,
) -> Response {
    use app_handling::{RecordHandlingInput, RecordHandlingService};
    use domain_handling::HandlingType;
    use tracking_acl::{SelectedRouteCheckAdapter, TrackingReflectionAdapter};

    let Some(handling_type) = HandlingType::parse_type(&form.handling_type) else {
        return render(&HandlingNewTemplate {
            current_user,
            error: true,
            error_message: "作業種別が不正です".to_string(),
            warning: false,
            warning_message: String::new(),
        });
    };
    let Ok(completion_time) = parse_local_dt(&form.completion_time) else {
        return render(&HandlingNewTemplate {
            current_user,
            error: true,
            error_message: "作業日時が不正です".to_string(),
            warning: false,
            warning_message: String::new(),
        });
    };

    let service = RecordHandlingService::new(
        state.handling_repo.clone(),
        TrackingReflectionAdapter::new(state.tracking_repo.clone(), state.pool.clone()),
        SelectedRouteCheckAdapter::new(state.selected_route_view.clone()),
    );
    let input = RecordHandlingInput {
        tracking_number: form.tracking_number.trim().to_string(),
        handling_type,
        completion_time,
        un_locode: form.un_locode.trim().to_string(),
        voyage_number: non_empty(form.voyage_number),
        operator_name: non_empty(form.operator_name),
        receipt_confirmation: non_empty(form.receipt_confirmation),
    };
    match service.record(input).await {
        Ok(outcome) => match outcome.route_warning {
            // ルート相違警告は非ブロッキング。登録は成立し警告のみ表示する。
            Some(msg) => render(&HandlingNewTemplate {
                current_user,
                error: false,
                error_message: String::new(),
                warning: true,
                warning_message: msg,
            }),
            None => Redirect::to("/handling").into_response(),
        },
        Err(app_handling::HandlingServiceError::TrackingNotFound(_)) => {
            render(&HandlingNewTemplate {
                current_user,
                error: true,
                error_message: "追跡番号が存在しません".to_string(),
                warning: false,
                warning_message: String::new(),
            })
        }
        Err(app_handling::HandlingServiceError::Domain(_))
        | Err(app_handling::HandlingServiceError::InvalidInput(_)) => {
            render(&HandlingNewTemplate {
                current_user,
                error: true,
                error_message: "入力内容を確認してください（引取には荷受人確認が必要です）"
                    .to_string(),
                warning: false,
                warning_message: String::new(),
            })
        }
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

// ===== Tracking Context（追跡照会・手動更新・US17）=====

#[derive(Template)]
#[template(path = "tracking_input.html")]
struct TrackingInputTemplate {
    current_user: CurrentUser,
}

struct TrackingEventRow {
    status_label: String,
    un_locode: String,
    event_time: String,
    voyage_number: String,
}

#[derive(Template)]
#[template(path = "tracking_show.html")]
struct TrackingShowTemplate {
    current_user: CurrentUser,
    tracking_number: String,
    status_label: String,
    events: Vec<TrackingEventRow>,
}

/// 貨物状態手動更新フォーム入力（US17）。
#[derive(Debug, Deserialize)]
pub struct TrackingUpdateForm {
    status: String,
    un_locode: String,
    event_time: String,
    voyage_number: Option<String>,
}

/// 追跡入力画面（US18 照会の入口・IT5 では手動更新導線への入口）。
async fn tracking_input(session: Session) -> Response {
    let current_user = match require_user(&session).await {
        Ok(u) => u,
        Err(resp) => return resp,
    };
    render(&TrackingInputTemplate { current_user })
}

/// 追跡詳細画面（タイムライン＋手動更新導線・US17）。
async fn tracking_detail(
    State(state): State<AppState>,
    session: Session,
    Path(tracking_number): Path<String>,
) -> Response {
    let current_user = match require_user(&session).await {
        Ok(u) => u,
        Err(resp) => return resp,
    };
    let Ok(number) = domain_tracking::TrackingNumber::parse(&tracking_number) else {
        return StatusCode::NOT_FOUND.into_response();
    };
    let activity = match state.tracking_repo.find_by_tracking_number(&number).await {
        Ok(Some(a)) => a,
        Ok(None) => return StatusCode::NOT_FOUND.into_response(),
        Err(_) => return StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    };
    let events = activity
        .events()
        .iter()
        .map(|e| TrackingEventRow {
            status_label: e.status().label().to_string(),
            un_locode: e.location().un_locode().to_string(),
            event_time: e.event_time().format("%Y-%m-%d %H:%M").to_string(),
            voyage_number: e
                .voyage_number()
                .map(|v| v.as_str().to_string())
                .unwrap_or_default(),
        })
        .collect();
    render(&TrackingShowTemplate {
        current_user,
        status_label: activity.current_status().label().to_string(),
        tracking_number,
        events,
    })
}

/// 貨物状態を手動更新する（US17・追跡管理者のみ）。
async fn tracking_update(
    State(state): State<AppState>,
    RoleGuard(_u, _): TrackerUser,
    Path(tracking_number): Path<String>,
    Form(form): Form<TrackingUpdateForm>,
) -> Response {
    use app_tracking::{ManualStatusUpdate, ManualTrackingUpdateService};
    use tracking_acl::SqlxTrackingNotificationPort;

    let Ok(event_time) = parse_local_dt(&form.event_time) else {
        return StatusCode::UNPROCESSABLE_ENTITY.into_response();
    };
    let service = ManualTrackingUpdateService::new(
        state.tracking_repo.clone(),
        SqlxTrackingNotificationPort::new(state.pool.clone()),
    );
    let update = ManualStatusUpdate {
        status: domain_tracking::TrackingStatus::from_str_or_unknown(&form.status),
        un_locode: form.un_locode.trim().to_string(),
        event_time,
        voyage_number: non_empty(form.voyage_number),
    };
    match service.update_status(&tracking_number, update).await {
        Ok(()) => Redirect::to(&format!("/tracking/{tracking_number}")).into_response(),
        Err(app_tracking::TrackingServiceError::NotFound(_)) => {
            StatusCode::NOT_FOUND.into_response()
        }
        Err(app_tracking::TrackingServiceError::InvalidInput(_)) => {
            StatusCode::UNPROCESSABLE_ENTITY.into_response()
        }
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

// ===== Estimation Context（輸送見積・US01）=====

struct EstimateRow {
    estimate_id: String,
    origin: String,
    destination: String,
    arrival_deadline: String,
    cargo_type: String,
    candidate_count: usize,
}

#[derive(Template)]
#[template(path = "estimate_list.html")]
struct EstimateListTemplate {
    current_user: CurrentUser,
    estimates: Vec<EstimateRow>,
}

#[derive(Template)]
#[template(path = "estimate_new.html")]
struct EstimateNewTemplate {
    current_user: CurrentUser,
    error: bool,
    error_message: String,
}

struct EstimateCandidateRow {
    rank: i32,
    voyage_number: String,
    transit_ports: Vec<String>,
    transit_ports_label: String,
    transit_days: i32,
    estimated_cost: String,
}

#[derive(Template)]
#[template(path = "estimate_show.html")]
struct EstimateShowTemplate {
    current_user: CurrentUser,
    estimate_id: String,
    origin: String,
    destination: String,
    arrival_deadline: String,
    cargo_type: String,
    weight: String,
    candidates: Vec<EstimateCandidateRow>,
}

/// 見積作成フォーム入力（US01）。
#[derive(Debug, Deserialize)]
pub struct EstimateForm {
    origin: String,
    destination: String,
    arrival_deadline: String,
    cargo_type: String,
    weight: String,
    #[allow(dead_code)]
    hazardous_class: Option<String>,
}

/// 見積一覧（US01・営業担当者）。
async fn estimate_list(
    State(state): State<AppState>,
    RoleGuard(current_user, _): SalesUser,
) -> Response {
    use domain_estimation::EstimateRepository;
    match state.estimate_repo.find_all().await {
        Ok(estimates) => {
            let rows = estimates
                .iter()
                .map(|e| EstimateRow {
                    estimate_id: e.estimate_id().as_str().to_string(),
                    origin: e.origin().un_locode().to_string(),
                    destination: e.destination().un_locode().to_string(),
                    arrival_deadline: e.arrival_deadline().format("%Y-%m-%d").to_string(),
                    cargo_type: e.cargo_type().as_str().to_string(),
                    candidate_count: e.candidates().len(),
                })
                .collect();
            render(&EstimateListTemplate {
                current_user,
                estimates: rows,
            })
        }
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

/// 見積作成フォーム（US01・営業担当者）。
async fn estimate_new_form(RoleGuard(current_user, _): SalesUser) -> Response {
    render(&EstimateNewTemplate {
        current_user,
        error: false,
        error_message: String::new(),
    })
}

/// 見積を作成する（US01）。
async fn estimate_create(
    State(state): State<AppState>,
    RoleGuard(current_user, _): SalesUser,
    Form(form): Form<EstimateForm>,
) -> Response {
    use app_estimation::{CreateEstimateInput, CreateEstimateService};
    use estimation_acl::RoutingRouteCandidateProvider;

    let err = |msg: &str| EstimateNewTemplate {
        current_user: current_user.clone(),
        error: true,
        error_message: msg.to_string(),
    };
    let Ok(deadline) = NaiveDate::parse_from_str(form.arrival_deadline.trim(), "%Y-%m-%d") else {
        return render(&err("希望期限の形式が不正です"));
    };
    let Ok(weight) = Decimal::from_str(form.weight.trim()) else {
        return render(&err("重量が不正です"));
    };
    let cargo_type = domain_estimation::CargoType::from_str_or_general(&form.cargo_type);

    let service = CreateEstimateService::new(
        state.estimate_repo.clone(),
        RoutingRouteCandidateProvider::new(state.voyage_repo.clone()),
    );
    let input = CreateEstimateInput {
        origin: form.origin.trim().to_string(),
        destination: form.destination.trim().to_string(),
        arrival_deadline: deadline,
        cargo_type,
        weight,
    };
    match service.create(input).await {
        Ok(outcome) => Redirect::to(&format!("/estimates/{}", outcome.estimate_id)).into_response(),
        Err(app_estimation::EstimationServiceError::InvalidInput(msg)) => render(&err(&msg)),
        Err(_) => StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    }
}

/// 見積詳細（US01・ルート候補一覧）。
async fn estimate_show(
    State(state): State<AppState>,
    RoleGuard(current_user, _): SalesUser,
    Path(estimate_id): Path<String>,
) -> Response {
    use domain_estimation::{EstimateId, EstimateRepository};
    let id = EstimateId::from_string(estimate_id);
    let estimate = match state.estimate_repo.find_by_id(&id).await {
        Ok(Some(e)) => e,
        Ok(None) => return StatusCode::NOT_FOUND.into_response(),
        Err(_) => return StatusCode::INTERNAL_SERVER_ERROR.into_response(),
    };
    let candidates = estimate
        .candidates()
        .iter()
        .map(|c| {
            let ports: Vec<String> = c.transit_ports().to_vec();
            EstimateCandidateRow {
                rank: c.rank(),
                voyage_number: c.voyage_number().to_string(),
                transit_ports_label: ports.join(" → "),
                transit_ports: ports,
                transit_days: c.transit_days(),
                estimated_cost: c.estimated_cost().to_string(),
            }
        })
        .collect();
    render(&EstimateShowTemplate {
        current_user,
        estimate_id: estimate.estimate_id().as_str().to_string(),
        origin: estimate.origin().un_locode().to_string(),
        destination: estimate.destination().un_locode().to_string(),
        arrival_deadline: estimate.arrival_deadline().format("%Y-%m-%d").to_string(),
        cargo_type: estimate.cargo_type().as_str().to_string(),
        weight: estimate.weight().value().to_string(),
        candidates,
    })
}
