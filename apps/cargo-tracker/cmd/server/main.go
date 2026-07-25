// Package main は Cargo Tracker のエントリポイント。
// 依存の組み立て（手動 DI）はすべてここで行う。
package main

import (
	"context"
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
	bookingapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	bookinginfra "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/infrastructure"
	bookingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/interfaces/web"
	estimationapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/application"
	estimationinfra "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/infrastructure"
	estimationweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/interfaces/web"
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
	routeHandler := bookingweb.NewRouteHandler(renderer, assignRouteSvc, cargoRepo)

	// Estimation Context の配線
	estimateRepo := estimationinfra.NewEstimateRepository(pool)
	createEstimateSvc := estimationapp.NewCreateEstimateService(estimateRepo, uuidGenerator{}, shareddomain.SystemClock{}, estimationRouteSearcherAdapter{search: searchRoutesSvc})
	estimateQuerySvc := estimationapp.NewEstimateQueryService(estimateRepo)
	estimateHandler := estimationweb.NewEstimateHandler(renderer, createEstimateSvc, estimateQuerySvc)

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

	// --- 保護ルート（要認証） ---
	r.Group(func(pr chi.Router) {
		pr.Use(sharedweb.RequireAuth)

		pr.Get("/", placeholder(renderer, "ダッシュボード"))

		// 荷主・貨物予約は営業担当者ロールを要求
		pr.Group(func(sr chi.Router) {
			sr.Use(sharedweb.RequireRole("ROLE_SALES", "ROLE_SHIPPER"))
			shipperHandler.Register(sr)
			bookingHandler.Register(sr)
		})

		// 見積管理は営業担当者ロールを要求（US01）
		pr.Group(func(er chi.Router) {
			er.Use(sharedweb.RequireRole("ROLE_SALES"))
			estimateHandler.Register(er)
		})

		// ウォーキングスケルトン: 他ルートのプレースホルダ（ロール別）
		pr.With(sharedweb.RequireRole("ROLE_SHIPPER", "ROLE_CONSIGNEE", "ROLE_TRACKER")).
			Get("/tracking", placeholder(renderer, "貨物追跡入力"))
		pr.With(sharedweb.RequireRole("ROLE_HANDLER", "ROLE_TRACKER")).
			Get("/handling", placeholder(renderer, "荷役作業一覧"))
		pr.Group(func(vr chi.Router) {
			vr.Use(sharedweb.RequireRole("ROLE_ROUTE_DESIGNER"))
			voyageHandler.Register(vr)
			routeHandler.Register(vr) // 経路割り当て /bookings/{id}/route（US08/US09）
		})
		pr.With(sharedweb.RequireRole("ROLE_BILLING")).
			Get("/billing/invoices", placeholder(renderer, "請求書一覧"))
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

// loggingPublisher はドメインイベントをログ出力する簡易 EventPublisher 実装。
// 購読側（routing 等）の登録は Phase 2 で in-process ディスパッチャに置き換える。
type loggingPublisher struct{}

// Publish はイベントをログに記録する。
func (loggingPublisher) Publish(ctx context.Context, name string, payload any) error {
	slog.InfoContext(ctx, "domain event published", "event", name, "payload", payload)
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
