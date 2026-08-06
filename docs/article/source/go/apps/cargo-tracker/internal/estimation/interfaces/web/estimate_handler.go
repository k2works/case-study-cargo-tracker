// Package web は Estimation Context の画面ハンドラ（html/template + PRG）を提供する。
package web

import (
	"context"
	"errors"
	"net/http"
	"net/url"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/domain"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
)

// Create は見積作成ユースケースの入力ポート。
type Create interface {
	Create(ctx context.Context, cmd application.CreateEstimateCommand) (domain.EstimateId, error)
}

// Query は見積一覧・詳細の入力ポート。
type Query interface {
	List(ctx context.Context) ([]application.EstimateView, error)
	Find(ctx context.Context, estimateID string) (application.EstimateView, error)
}

// EstimateHandler は輸送見積画面のハンドラ。
type EstimateHandler struct {
	renderer *sharedweb.Renderer
	create   Create
	query    Query
}

// NewEstimateHandler は EstimateHandler を生成する。
func NewEstimateHandler(renderer *sharedweb.Renderer, create Create, query Query) *EstimateHandler {
	return &EstimateHandler{renderer: renderer, create: create, query: query}
}

// Register はルートを chi ルーターに登録する。
func (h *EstimateHandler) Register(r chi.Router) {
	r.Get("/estimates", h.list)
	r.Get("/estimates/new", h.newForm)
	r.Post("/estimates", h.createEstimate)
	r.Get("/estimates/{estimateId}", h.detail)
}

func (h *EstimateHandler) list(w http.ResponseWriter, r *http.Request) {
	views, err := h.query.List(r.Context())
	if err != nil {
		http.Error(w, "見積一覧の取得に失敗しました", http.StatusInternalServerError)
		return
	}
	h.renderer.RenderPage(w, r, "templates/estimates/list.html", views)
}

func (h *EstimateHandler) newForm(w http.ResponseWriter, r *http.Request) {
	h.renderer.RenderPage(w, r, "templates/estimates/new.html", nil)
}

func (h *EstimateHandler) createEstimate(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	cmd := application.CreateEstimateCommand{
		OriginUnLocode:      r.FormValue("origin"),
		DestinationUnLocode: r.FormValue("destination"),
		CargoType:           r.FormValue("cargoType"),
	}
	if v := r.FormValue("arrivalDeadline"); v != "" {
		if d, err := time.Parse("2006-01-02", v); err == nil {
			cmd.ArrivalDeadline = d
		}
	}
	if kg, err := strconv.ParseFloat(r.FormValue("weightKg"), 64); err == nil {
		cmd.WeightKg = kg
	}
	id, err := h.create.Create(r.Context(), cmd)
	if err != nil {
		msg := "見積の作成に失敗しました。入力内容を確認してください。"
		if errors.Is(err, application.ErrNoRouteInDeadline) {
			msg = "希望期限に間に合うルートが見つかりません。期限を見直してください。"
		}
		w.WriteHeader(http.StatusUnprocessableEntity)
		h.renderer.RenderPageWithError(w, r, "templates/estimates/new.html", nil, msg)
		return
	}
	http.Redirect(w, r, "/estimates/"+url.PathEscape(id.Value()), http.StatusSeeOther)
}

func (h *EstimateHandler) detail(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "estimateId")
	view, err := h.query.Find(r.Context(), id)
	if err != nil {
		http.Error(w, "見積が見つかりません", http.StatusNotFound)
		return
	}
	h.renderer.RenderPage(w, r, "templates/estimates/detail.html", view)
}
