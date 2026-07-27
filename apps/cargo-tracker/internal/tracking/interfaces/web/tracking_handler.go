// Package web は Tracking Context の画面ハンドラ（html/template + htmx）を提供する。
package web

import (
	"context"
	"errors"
	"net/http"
	"regexp"
	"strings"

	"github.com/go-chi/chi/v5"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/application"
)

// trackingNumberPattern は追跡番号の形式（TRK-YYYYMMDD-NNNN）を検証する（オープンリダイレクト対策）。
var trackingNumberPattern = regexp.MustCompile(`^TRK-\d{8}-\d{4}$`)

// TrackingQuery は追跡照会の入力ポート（US18）。
type TrackingQuery interface {
	FindByTrackingNumber(ctx context.Context, trackingNumber string) (application.TrackingView, error)
}

// TrackingHandler は貨物追跡照会画面のハンドラ。
type TrackingHandler struct {
	renderer *sharedweb.Renderer
	query    TrackingQuery
}

// NewTrackingHandler は TrackingHandler を生成する。
func NewTrackingHandler(renderer *sharedweb.Renderer, query TrackingQuery) *TrackingHandler {
	return &TrackingHandler{renderer: renderer, query: query}
}

// Register は認証配下のルート（貨物追跡入力・詳細）を登録する。
func (h *TrackingHandler) Register(r chi.Router) {
	r.Get("/tracking", h.input)
	r.Get("/tracking/{trackingNumber}", h.detail)
}

// RegisterPublic は認証不要の公開追跡ルートを登録する（US18）。
func (h *TrackingHandler) RegisterPublic(r chi.Router) {
	r.Get("/public/tracking/{trackingId}", h.publicDetail)
}

func (h *TrackingHandler) input(w http.ResponseWriter, r *http.Request) {
	number := strings.TrimSpace(r.URL.Query().Get("trackingNumber"))
	if number == "" {
		h.renderer.RenderPage(w, r, "templates/tracking/input.html", nil)
		return
	}
	if !trackingNumberPattern.MatchString(number) {
		w.WriteHeader(http.StatusBadRequest)
		h.renderer.RenderPageWithError(w, r, "templates/tracking/input.html", nil, "追跡番号の形式が正しくありません（TRK-YYYYMMDD-NNNN）")
		return
	}
	// number は上の trackingNumberPattern で TRK-YYYYMMDD-NNNN 形式に検証済み。
	http.Redirect(w, r, "/tracking/"+number, http.StatusSeeOther) //nolint:gosec // number は TRK 形式に検証済み
}

func (h *TrackingHandler) detail(w http.ResponseWriter, r *http.Request) {
	h.renderDetail(w, r, "templates/tracking/detail.html", chi.URLParam(r, "trackingNumber"))
}

func (h *TrackingHandler) publicDetail(w http.ResponseWriter, r *http.Request) {
	h.renderDetail(w, r, "templates/public/tracking.html", chi.URLParam(r, "trackingId"))
}

func (h *TrackingHandler) renderDetail(w http.ResponseWriter, r *http.Request, page, number string) {
	view, err := h.query.FindByTrackingNumber(r.Context(), number)
	if err != nil {
		if errors.Is(err, application.ErrTrackingNotFound) {
			w.WriteHeader(http.StatusNotFound)
			h.renderer.RenderPageWithError(w, r, "templates/tracking/input.html", nil, "追跡番号が見つかりません: "+number)
			return
		}
		http.Error(w, "追跡情報の取得に失敗しました", http.StatusInternalServerError)
		return
	}
	h.renderer.RenderPage(w, r, page, view)
}
