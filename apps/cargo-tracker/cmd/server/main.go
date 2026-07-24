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

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"
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

	r := chi.NewRouter()
	r.Use(middleware.RequestID)
	r.Use(middleware.Recoverer)
	r.Use(stubCurrentUser) // 注: 認証は IT1 スコープ外。スタブで ROLE_SALES を設定

	r.Get("/healthz", handleHealthz)

	// 静的資産
	staticFS, _ := fs.Sub(webassets.StaticFS, "static")
	r.Handle("/static/*", http.StripPrefix("/static/", http.FileServer(http.FS(staticFS))))

	// ダッシュボード（プレースホルダ）
	r.Get("/", placeholder(renderer, "ダッシュボード"))

	// Shipper 画面
	shipperHandler.Register(r)

	// ウォーキングスケルトン: 他ルートのプレースホルダ
	r.Get("/bookings", placeholder(renderer, "貨物予約一覧"))
	r.Get("/bookings/new", placeholder(renderer, "貨物予約登録"))
	r.Get("/tracking", placeholder(renderer, "貨物追跡入力"))
	r.Get("/handling", placeholder(renderer, "荷役作業一覧"))
	r.Get("/voyages", placeholder(renderer, "航路一覧"))
	r.Get("/billing/invoices", placeholder(renderer, "請求書一覧"))
	r.Get("/admin/discount-policies", placeholder(renderer, "割引ポリシー一覧"))

	return r
}

// stubCurrentUser は IT1 のスケルトン用にカレントユーザー（ROLE_SALES）を設定する。
// 認証実装は後続イテレーションで scs セッション + RBAC に置き換える。
func stubCurrentUser(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		user := sharedweb.CurrentUser{Username: "sales", Roles: []string{"ROLE_SALES"}}
		ctx := sharedweb.WithCurrentUser(r.Context(), user)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func placeholder(renderer *sharedweb.Renderer, title string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		renderer.RenderPage(w, r, "templates/placeholder.html", title)
	}
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
