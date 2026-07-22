//! Booking / Shipper Context の Web インターフェース層（Askama SSR + tower-sessions）。
//!
//! IT1 ではログイン認証・ロール別ナビゲーション・ダッシュボードのウォーキングスケルトンを提供する。

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
    /// 確定経路の要約（US11 で紐付け済みなら表示）。
    has_route: bool,
    route_voyages: String,
    route_transit_ports: String,
    route_transit_days: i64,
    route_arrival: String,
}

/// 予約状態の日本語ラベル。ui_design.md 付録「ステータス対応表（正典）」に準拠する。
fn booking_status_label(status: &str) -> &'static str {
    match status {
        "PRELIMINARY" => "仮予約",
        "ROUTE_DESIGNING" => "経路設計中",
        "ROUTE_PROPOSED" => "経路提案済",
        "CONFIRMED" => "確認済",
        "TRACKING_ISSUED" => "追跡番号発行済",
        "IN_TRANSIT" => "輸送中",
        "DELIVERED" => "配送完了",
        "SETTLED" => "精算完了",
        "CANCELLED" => "キャンセル",
        _ => "不明",
    }
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
        .route("/voyages", get(voyage_list).post(voyage_create))
        .route("/voyages/new", get(voyage_new_form))
        .route("/voyages/{voyage_number}/edit", get(voyage_edit_form))
        .route("/voyages/{voyage_number}", post(voyage_update))
        .route("/bookings/{booking_id}/route", get(route_design))
        .route("/bookings/{booking_id}/route/confirm", post(route_confirm))
        .route("/bookings/{booking_id}/route/adjust", post(route_adjust))
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
    let status = cargo.status().as_str().to_string();
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
        status_label: booking_status_label(&status).to_string(),
        is_preliminary: status == "PRELIMINARY",
        is_route_designing: status == "ROUTE_DESIGNING",
        is_route_proposed: status == "ROUTE_PROPOSED",
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
        voyage_csv: c
            .voyage_numbers()
            .iter()
            .map(|v| v.as_str().to_string())
            .collect::<Vec<_>>()
            .join(","),
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
        Ok((spec, candidates)) => render(&RouteDesignTemplate {
            current_user,
            error: false,
            error_message: String::new(),
            booking_id,
            origin: spec.origin.code().to_string(),
            destination: spec.destination.code().to_string(),
            arrival_deadline: spec.arrival_deadline.format("%Y-%m-%d").to_string(),
            cargo_type: spec.cargo_type.as_str().to_string(),
            candidates: candidates
                .iter()
                .map(|c| route_row(c, spec.arrival_deadline))
                .collect(),
        }),
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
        Ok((spec, candidates)) => render(&RouteDesignTemplate {
            current_user,
            error: false,
            error_message: String::new(),
            booking_id,
            origin: spec.origin.code().to_string(),
            destination: spec.destination.code().to_string(),
            arrival_deadline: spec.arrival_deadline.format("%Y-%m-%d").to_string(),
            cargo_type: spec.cargo_type.as_str().to_string(),
            candidates: candidates
                .iter()
                .map(|c| route_row(c, spec.arrival_deadline))
                .collect(),
        }),
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
    // 選択インデックスに対応する候補の航海番号列を取り出す（TOCTOU 照合用）。
    let expected: Vec<String> = form
        .expected_voyages_list
        .split(';')
        .nth(form.candidate_index)
        .unwrap_or("")
        .split(',')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(String::from)
        .collect();
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
