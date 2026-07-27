// Package main は Cargo Tracker のエントリポイント。
// 依存の組み立て（手動 DI）はすべてここで行う。
package main

import (
	"context"
	"errors"
	"io/fs"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/alexedwards/scs/v2"
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"
	billingapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/application"
	billinginfra "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/infrastructure"
	billingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/interfaces/web"
	bookingapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	bookingdomain "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	bookinginfra "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/infrastructure"
	bookingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/interfaces/web"
	estimationapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/application"
	estimationinfra "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/infrastructure"
	estimationweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/interfaces/web"
	handlingapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/application"
	handlingdomain "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/domain"
	handlinginfra "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/infrastructure"
	handlingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/interfaces/web"
	routingapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/application"
	routinginfra "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/infrastructure"
	routingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/interfaces/web"
	authapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/auth/application"
	shareddomain "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	authinfra "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/auth"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	shipperapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/application"
	shipperinfra "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/infrastructure"
	shipperweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/interfaces/web"
	trackingapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/application"
	trackinginfra "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/infrastructure"
	trackingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/interfaces/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	slog.SetDefault(logger)

	addr := ":" + envOr("PORT", "8080")
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	pool, err := pgxpool.New(ctx, envOr("DB_URL",
		"postgres://cargo:cargo@localhost:5432/cargo_tracker?sslmode=disable"))
	if err != nil {
		logger.Error("db pool init failed", "error", err)
		os.Exit(1)
	}
	defer pool.Close()

	r := buildRouter(pool)

	srv := &http.Server{
		Addr:              addr,
		Handler:           r,
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		logger.Info("server starting", "addr", addr)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Error("server failed", "error", err)
			stop()
		}
	}()

	<-ctx.Done()
	logger.Info("shutting down")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("graceful shutdown failed", "error", err)
	}
}

// buildRouter はルーター全体を組み立てる（手動 DI）。
func buildRouter(pool *pgxpool.Pool) http.Handler {
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")

	// Shipper Context の配線
	shipperRepo := shipperinfra.NewShipperRepository(pool)
	shipperQuery := shipperinfra.NewShipperQuery(pool)
	registerSvc := shipperapp.NewRegisterShipperService(shipperRepo, uuidGenerator{})
	querySvc := shipperapp.NewShipperQueryService(shipperQuery)
	shipperHandler := shipperweb.NewShipperHandler(renderer, registerSvc, querySvc)

	// Booking Context の配線
	cargoRepo := bookinginfra.NewCargoRepository(pool)
	shipperChecker := bookinginfra.NewShipperExistenceAdapter(pool)
	registerCargoSvc := bookingapp.NewRegisterCargoService(cargoRepo, shipperChecker, uuidGenerator{}, loggingPublisher{})
	manageBookingSvc := bookingapp.NewManageBookingService(cargoRepo)
	cargoQuerySvc := bookingapp.NewCargoQueryService(bookinginfra.NewCargoQuery(pool))
	bookingHandler := bookingweb.NewBookingHandler(renderer, registerCargoSvc, manageBookingSvc, cargoRepo, cargoQuerySvc)

	// Routing Context の配線
	voyageRepo := routinginfra.NewVoyageRepository(pool)
	registerVoyageSvc := routingapp.NewRegisterVoyageService(voyageRepo)
	updateVoyageSvc := routingapp.NewUpdateVoyageService(voyageRepo)
	voyageQuerySvc := routingapp.NewVoyageQueryService(voyageRepo)
	voyageHandler := routingweb.NewVoyageHandler(renderer, registerVoyageSvc, updateVoyageSvc, voyageQuerySvc)

	// 経路割り当て（US08/US09）の配線。
	// BC 横断は合成ルート注入方式（ADR-0007）: Routing の SearchRoutesService を
	// Booking の RouteSearcher ポートへ変換アダプタ経由で注入する（go-arch-lint 無改変）。
	searchRoutesSvc := routingapp.NewSearchRoutesService(voyageRepo)
	assignRouteSvc := bookingapp.NewAssignRouteService(cargoRepo, routeSearcherAdapter{search: searchRoutesSvc})
	// US10: 条件調整で候補が見つからない場合の協議依頼（EventPublisher でイベント発行）。
	requestNegotiationSvc := bookingapp.NewRequestNegotiationService(cargoRepo, loggingPublisher{})
	routeHandler := bookingweb.NewRouteHandler(renderer, assignRouteSvc, cargoRepo, requestNegotiationSvc, cargoQuerySvc)

	// US12: 確定経路の荷主通知（NotificationPort はログ実装・記録は notification テーブル）。
	notificationRepo := bookinginfra.NewNotificationRepository(pool)
	notifyRouteSvc := bookingapp.NewNotifyRouteService(cargoRepo, loggingNotifier{}, notificationRepo, shareddomain.SystemClock{})
	notifyHandler := bookingweb.NewNotifyHandler(renderer, notifyRouteSvc)

	// Estimation Context の配線
	estimateRepo := estimationinfra.NewEstimateRepository(pool)
	createEstimateSvc := estimationapp.NewCreateEstimateService(estimateRepo, uuidGenerator{}, shareddomain.SystemClock{}, estimationRouteSearcherAdapter{search: searchRoutesSvc})
	estimateQuerySvc := estimationapp.NewEstimateQueryService(estimateRepo)
	estimateHandler := estimationweb.NewEstimateHandler(renderer, createEstimateSvc, estimateQuerySvc)

	// Tracking / Handling Context の配線（IT6・US14/US15/US16/US18）
	trackingRepo := trackinginfra.NewTrackingActivityRepository(pool)
	trackingCmdSvc := trackingapp.NewTrackingCommandService(trackingRepo)
	trackingQuerySvc := trackingapp.NewTrackingQueryService(trackingRepo)
	trackingHandler := trackingweb.NewTrackingHandler(renderer, trackingQuerySvc)
	// IT7: 例外処理・状態手動更新（US17/US19/US20）。荷主/管理職通知はログ実装。
	exceptionSvc := trackingapp.NewExceptionService(trackingRepo, loggingTrackingNotifier{}, shareddomain.SystemClock{})
	exceptionHandler := trackingweb.NewExceptionHandler(renderer, exceptionSvc, trackingQuerySvc)

	// Billing Context の配線（IT8・US21/US22/US23）
	invoiceRepo := billinginfra.NewInvoiceRepository(pool)
	shipperContractAdapter := billinginfra.NewShipperContractAdapter(pool)
	generateInvoiceSvc := billingapp.NewGenerateInvoiceService(
		invoiceRepo,
		cargoBillingSnapshotAdapter{repo: cargoRepo},
		shipperContractAdapter,
		invoiceNumberIssuerAdapter{repo: invoiceRepo},
		loggingBillingNotifier{},
		shareddomain.SystemClock{},
	)
	confirmPaymentSvc := billingapp.NewConfirmPaymentService(
		invoiceRepo,
		bookingSettlerAdapter{repo: cargoRepo},
		loggingBillingNotifier{},
		shareddomain.SystemClock{},
	)
	invoiceQuerySvc := billingapp.NewInvoiceQueryService(invoiceRepo)
	billingCommand := billingCommandAdapter{gen: generateInvoiceSvc, confirm: confirmPaymentSvc}
	billingHandler := billingweb.NewBillingHandler(renderer, billingCommand, invoiceQuerySvc)

	handlingRepo := handlinginfra.NewHandlingActivityRepository(pool)
	// 荷役登録イベント → 追跡状態同期（Handling→Tracking の合成ルート配線）。
	handlingSvc := handlingapp.NewRegisterHandlingActivityService(
		handlingRepo,
		cargoSnapshotAdapter{repo: cargoRepo},
		handlingEventAdapter{tracking: trackingCmdSvc},
	)
	handlingHandler := handlingweb.NewHandlingHandler(renderer, handlingSvc)

	// US14: 追跡番号発行（Booking→Tracking の合成ルート配線）。採番は Tracking の日次連番。
	assignTrackingSvc := bookingapp.NewAssignTrackingNumberService(
		cargoRepo,
		trackingNumberIssuerAdapter{repo: trackingRepo, clock: shareddomain.SystemClock{}},
		trackingCreatorAdapter{tracking: trackingCmdSvc},
		loggingNotifier{},
	)
	issueTrackingHandler := bookingweb.NewIssueTrackingHandler(renderer, assignTrackingSvc)

	// 認証の配線（scs セッション + bcrypt）
	session := scs.New()
	session.Lifetime = 12 * time.Hour
	userRepo := authinfra.NewUserRepository(pool)
	authSvc := authapp.NewAuthService(userRepo, authinfra.BcryptHasher{})
	authHandler := sharedweb.NewAuthHandler(renderer, session, authSvc)

	r := chi.NewRouter()
	r.Use(middleware.RequestID)
	r.Use(middleware.Recoverer)
	r.Use(session.LoadAndSave)
	r.Use(sharedweb.SessionCurrentUser(session)) // セッション由来のカレントユーザー

	// --- 公開ルート（認証不要） ---
	r.Get("/healthz", handleHealthz)
	r.Get("/login", authHandler.LoginForm)
	r.Post("/login", authHandler.Login)
	r.Post("/logout", authHandler.Logout)

	staticFS, _ := fs.Sub(webassets.StaticFS, "static")
	r.Handle("/static/*", http.StripPrefix("/static/", http.FileServer(http.FS(staticFS))))

	// 公開貨物追跡（US18・認証不要）
	trackingHandler.RegisterPublic(r)

	// --- 保護ルート（要認証） ---
	r.Group(func(pr chi.Router) {
		pr.Use(sharedweb.RequireAuth)

		pr.Get("/", placeholder(renderer, "ダッシュボード"))

		// 荷主・貨物予約は営業担当者ロールを要求
		pr.Group(func(sr chi.Router) {
			sr.Use(sharedweb.RequireRole("ROLE_SALES", "ROLE_SHIPPER"))
			shipperHandler.Register(sr)
			bookingHandler.Register(sr)
			notifyHandler.Register(sr) // 確定経路の荷主通知 /bookings/{id}/notify（US12・営業担当者）
		})

		// 予約詳細は営業担当者に加え経路設計者も参照可（US09 割り当て後の遷移先）
		pr.Group(func(dr chi.Router) {
			dr.Use(sharedweb.RequireRole("ROLE_SALES", "ROLE_SHIPPER", "ROLE_ROUTE_DESIGNER"))
			bookingHandler.RegisterDetail(dr)
		})

		// 見積管理は営業担当者ロールを要求（US01）
		pr.Group(func(er chi.Router) {
			er.Use(sharedweb.RequireRole("ROLE_SALES"))
			estimateHandler.Register(er)
		})

		// 貨物追跡照会（US18・荷主/荷受人/追跡管理者）
		pr.Group(func(tr chi.Router) {
			tr.Use(sharedweb.RequireRole("ROLE_SHIPPER", "ROLE_CONSIGNEE", "ROLE_TRACKER"))
			trackingHandler.Register(tr)
		})
		// 例外処理・状態手動更新は追跡管理者。破損/紛失登録は荷役作業員も可（US20）
		pr.Group(func(er chi.Router) {
			er.Use(sharedweb.RequireRole("ROLE_TRACKER", "ROLE_HANDLER"))
			exceptionHandler.Register(er)
		})
		// 荷役作業記録（US15/US16・荷役作業員/追跡管理者）
		pr.Group(func(hr chi.Router) {
			hr.Use(sharedweb.RequireRole("ROLE_HANDLER", "ROLE_TRACKER"))
			handlingHandler.Register(hr)
		})
		pr.Group(func(vr chi.Router) {
			vr.Use(sharedweb.RequireRole("ROLE_ROUTE_DESIGNER"))
			voyageHandler.Register(vr)
			routeHandler.Register(vr)         // 経路割り当て /bookings/{id}/route（US08/US09）
			issueTrackingHandler.Register(vr) // 追跡番号発行 /bookings/{id}/tracking-number（US14）
		})
		// 精算・請求管理は経理担当者（US21/US22/US23）
		pr.Group(func(br chi.Router) {
			br.Use(sharedweb.RequireRole("ROLE_BILLING"))
			billingHandler.Register(br)
		})
		pr.With(sharedweb.RequireRole("ROLE_ADMIN")).
			Get("/admin/discount-policies", placeholder(renderer, "割引ポリシー一覧"))
	})

	return r
}

func placeholder(renderer *sharedweb.Renderer, title string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		renderer.RenderPage(w, r, "templates/placeholder.html", title)
	}
}

// --- IT6 合成ルートアダプタ（BC 横断は変換注入・go-arch-lint 無改変） ---

// cargoSnapshotAdapter は Booking の貨物情報を Handling の CargoSnapshot へ変換する ACL 実装。
type cargoSnapshotAdapter struct {
	repo *bookinginfra.CargoRepository
}

func (a cargoSnapshotAdapter) FetchSnapshot(ctx context.Context, bookingID string) (handlingdomain.CargoSnapshot, error) {
	bid, err := bookingdomain.NewBookingId(bookingID)
	if err != nil {
		return handlingdomain.CargoSnapshot{}, err
	}
	cargo, err := a.repo.FindByBookingID(ctx, bid)
	if err != nil {
		return handlingdomain.CargoSnapshot{}, err
	}
	var legs []handlingdomain.LegSnapshot
	if it := cargo.Itinerary(); it != nil && !it.IsEmpty() {
		for _, l := range it.Legs() {
			legs = append(legs, handlingdomain.NewLegSnapshot(l.LoadLocation(), l.UnloadLocation(), l.VoyageNumber()))
		}
	}
	return handlingdomain.NewCargoSnapshot(
		bookingID, cargo.RouteSpec().Origin(), cargo.RouteSpec().Destination(), legs, cargo.RoutingStatus(),
	), nil
}

// handlingEventAdapter は荷役登録イベントを Tracking の追跡イベント記録へ配線する。
// 追跡レコード未作成（追跡番号未発行）の場合は記録をスキップする。
type handlingEventAdapter struct {
	tracking *trackingapp.TrackingCommandService
}

func (a handlingEventAdapter) Publish(ctx context.Context, e handlingapp.HandlingActivityRegisteredEvent) error {
	err := a.tracking.RecordHandlingEvent(ctx, trackingapp.RecordHandlingEventCommand{
		BookingID:        e.BookingID,
		HandlingType:     e.HandlingType,
		LocationUnLocode: e.LocationUnLocode,
		VoyageNumber:     e.VoyageNumber,
		TransportStatus:  e.TransportStatus,
		Misrouted:        e.Misrouted,
		CompletionTime:   e.CompletionTime,
	})
	if errors.Is(err, trackingapp.ErrTrackingNotFound) {
		// 追跡番号未発行の貨物に対する荷役。追跡タイムラインへは反映されないが
		// 荷役自体は Handling 側に永続化済み。将来の履歴リプレイは ADR-0015 参照。
		slog.WarnContext(ctx, "handling event skipped: tracking not issued",
			"bookingId", e.BookingID, "handlingType", e.HandlingType)
		return nil
	}
	return err
}

// trackingNumberIssuerAdapter は Tracking の日次連番から追跡番号を採番する。
type trackingNumberIssuerAdapter struct {
	repo  *trackinginfra.TrackingActivityRepository
	clock shareddomain.SystemClock
}

func (a trackingNumberIssuerAdapter) Next(ctx context.Context) (string, error) {
	return a.repo.NextTrackingNumber(ctx, a.clock.Now())
}

// trackingCreatorAdapter は追跡番号発行時に Tracking の追跡レコードを新規作成する。
type trackingCreatorAdapter struct {
	tracking *trackingapp.TrackingCommandService
}

func (a trackingCreatorAdapter) Create(ctx context.Context, trackingNumber, bookingID string) error {
	return a.tracking.CreateTracking(ctx, trackingNumber, bookingID)
}

// --- IT8 Billing 合成ルートアダプタ（BC 横断は変換注入・go-arch-lint 無改変） ---

// cargoBillingSnapshotAdapter は Booking の貨物情報を Billing の精算スナップショットへ変換する ACL 実装。
type cargoBillingSnapshotAdapter struct {
	repo *bookinginfra.CargoRepository
}

func (a cargoBillingSnapshotAdapter) FetchBillingSnapshot(ctx context.Context, bookingID string) (billingapp.CargoBillingSnapshot, error) {
	bid, err := bookingdomain.NewBookingId(bookingID)
	if err != nil {
		return billingapp.CargoBillingSnapshot{}, err
	}
	cargo, err := a.repo.FindByBookingID(ctx, bid)
	if err != nil {
		return billingapp.CargoBillingSnapshot{}, err
	}
	// 距離係数は実距離データが無いためスタブ（旅程区間数ベースの簡易モデル・IT8 注2）。
	distanceFactor := int64(100)
	if it := cargo.Itinerary(); it != nil && !it.IsEmpty() {
		distanceFactor = int64(50 + 50*len(it.Legs()))
	}
	return billingapp.CargoBillingSnapshot{
		BookingID:       bookingID,
		ShipperCode:     cargo.ShipperCode().Value(),
		WeightKg:        cargo.Weight().Kg(),
		CargoType:       string(cargo.CargoType()),
		TransportStatus: string(cargo.TransportStatus()),
		DistanceFactor:  distanceFactor,
	}, nil
}

// bookingSettlerAdapter は入金確認後に予約を精算済み（SETTLED）にする ACL 実装。
type bookingSettlerAdapter struct {
	repo *bookinginfra.CargoRepository
}

func (a bookingSettlerAdapter) MarkSettled(ctx context.Context, bookingID string) error {
	bid, err := bookingdomain.NewBookingId(bookingID)
	if err != nil {
		return err
	}
	cargo, err := a.repo.FindByBookingID(ctx, bid)
	if err != nil {
		return err
	}
	if err := cargo.Settle(); err != nil {
		return err
	}
	return a.repo.UpdateStatus(ctx, cargo)
}

// billingCommandAdapter は請求発行・入金確認を BillingHandler の入力ポートへ束ねる。
type billingCommandAdapter struct {
	gen     *billingapp.GenerateInvoiceService
	confirm *billingapp.ConfirmPaymentService
}

func (a billingCommandAdapter) Generate(ctx context.Context, cmd billingapp.GenerateInvoiceCommand) (string, error) {
	return a.gen.Generate(ctx, cmd)
}
func (a billingCommandAdapter) Confirm(ctx context.Context, invoiceNumber string) error {
	return a.confirm.Confirm(ctx, invoiceNumber)
}

// invoiceNumberIssuerAdapter は請求番号の原子採番を InvoiceNumberIssuer ポートへ適合させる。
type invoiceNumberIssuerAdapter struct {
	repo *billinginfra.InvoiceRepository
}

func (a invoiceNumberIssuerAdapter) Next(ctx context.Context, day time.Time) (string, error) {
	return a.repo.NextInvoiceNumber(ctx, day)
}

// loggingBillingNotifier は Billing の NotificationPort のログ実装（US23）。
type loggingBillingNotifier struct{}

func (loggingBillingNotifier) NotifyShipper(ctx context.Context, shipperCode, summary string) error {
	slog.InfoContext(ctx, "billing notify shipper", "shipperCode", shipperCode, "summary", summary)
	return nil
}

func (loggingBillingNotifier) NotifyAccountant(ctx context.Context, invoiceNumber, summary string) error {
	slog.WarnContext(ctx, "billing notify accountant", "invoiceNumber", invoiceNumber, "summary", summary)
	return nil
}

// loggingTrackingNotifier は Tracking の NotificationPort のログ実装（US17/US19/US20）。
// 荷主・管理職通知をログ出力する（実メール/エスカレーションは後続の外部連携）。
type loggingTrackingNotifier struct{}

func (loggingTrackingNotifier) NotifyShipper(ctx context.Context, bookingID, summary string) error {
	slog.InfoContext(ctx, "notify shipper", "bookingId", bookingID, "summary", summary)
	return nil
}

func (loggingTrackingNotifier) NotifyManager(ctx context.Context, bookingID, summary string) error {
	slog.WarnContext(ctx, "escalate to manager", "bookingId", bookingID, "summary", summary)
	return nil
}

// loggingPublisher はドメインイベントをログ出力する簡易 EventPublisher 実装。
// 購読側（routing 等）の登録は Phase 2 で in-process ディスパッチャに置き換える。
type loggingPublisher struct{}

// Publish はイベントをログに記録する。
func (loggingPublisher) Publish(ctx context.Context, name string, payload any) error {
	slog.InfoContext(ctx, "domain event published", "event", name, "payload", payload)
	return nil
}

// loggingNotifier は NotificationPort のログ実装（US12）。
// 実メール送信は行わず、送信をログに記録する（記録の永続化は NotificationRepository が担う）。
type loggingNotifier struct{}

// Notify は通知をログに記録する。
func (loggingNotifier) Notify(ctx context.Context, shipperCode shareddomain.ShipperCode, summary string) error {
	slog.InfoContext(ctx, "route notification sent", "shipper", shipperCode.Value(), "summary", summary)
	return nil
}

// routeSearcherAdapter は Routing の SearchRoutesService を Booking の RouteSearcher ポートへ
// 適合させる合成ルート専用の変換アダプタ（ADR-0007）。
// Routing の公開 DTO（RouteCandidateView）を Booking の語彙（RouteCandidateDTO）へ写像し、
// booking-infrastructure が routing-application に依存しないよう BC 独立性を保つ。
type routeSearcherAdapter struct {
	search *routingapp.SearchRoutesService
}

// Search は Booking の探索仕様を Routing の照会へ変換し、結果を Booking の DTO へ写像する。
func (a routeSearcherAdapter) Search(ctx context.Context, spec bookingapp.RouteSearchSpec) ([]bookingapp.RouteCandidateDTO, error) {
	views, err := a.search.Search(ctx, routingapp.RouteSearchQuery{
		OriginUnLocode:      spec.OriginUnLocode,
		DestinationUnLocode: spec.DestinationUnLocode,
		ArrivalDeadline:     spec.ArrivalDeadline,
		CargoType:           spec.CargoType,
	})
	if err != nil {
		return nil, err
	}
	candidates := make([]bookingapp.RouteCandidateDTO, 0, len(views))
	for _, v := range views {
		legs := make([]bookingapp.RouteLegDTO, 0, len(v.Legs))
		for _, l := range v.Legs {
			legs = append(legs, bookingapp.RouteLegDTO{
				VoyageNumber:   l.VoyageNumber,
				LoadUnLocode:   l.LoadUnLocode,
				UnloadUnLocode: l.UnloadUnLocode,
				LoadTime:       l.LoadTime,
				UnloadTime:     l.UnloadTime,
			})
		}
		candidates = append(candidates, bookingapp.RouteCandidateDTO{
			Legs:          legs,
			TransitDays:   v.TransitDays,
			EstimatedCost: v.EstimatedCost,
			Waypoints:     v.Waypoints,
		})
	}
	return candidates, nil
}

// estimationRouteSearcherAdapter は Routing の SearchRoutesService を Estimation の
// RouteSearcher ポートへ適合させる合成ルート専用アダプタ（T3・ADR-0007）。
type estimationRouteSearcherAdapter struct {
	search *routingapp.SearchRoutesService
}

// Search は Estimation の探索仕様を Routing の照会へ変換し、結果を Estimation の DTO へ写像する。
func (a estimationRouteSearcherAdapter) Search(ctx context.Context, spec estimationapp.RouteSearchSpec) ([]estimationapp.RouteCandidateResult, error) {
	views, err := a.search.Search(ctx, routingapp.RouteSearchQuery{
		OriginUnLocode:      spec.OriginUnLocode,
		DestinationUnLocode: spec.DestinationUnLocode,
		ArrivalDeadline:     spec.ArrivalDeadline,
		CargoType:           spec.CargoType,
	})
	if err != nil {
		return nil, err
	}
	results := make([]estimationapp.RouteCandidateResult, 0, len(views))
	for _, v := range views {
		numbers := make([]string, 0, len(v.Legs))
		for _, l := range v.Legs {
			numbers = append(numbers, l.VoyageNumber)
		}
		results = append(results, estimationapp.RouteCandidateResult{
			VoyageNumbers: numbers,
			TransitDays:   v.TransitDays,
			EstimatedCost: v.EstimatedCost,
			Waypoints:     v.Waypoints,
		})
	}
	return results, nil
}

func handleHealthz(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(`{"status":"UP"}`))
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
