package web

import (
	"context"
	"errors"
	"net/http"
	"net/url"
	"strconv"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
)

// RouteAssigner は経路候補の算出・確定の入力ポート（US08/US09）。
type RouteAssigner interface {
	Candidates(ctx context.Context, bookingID domain.BookingId) ([]application.RouteCandidateDTO, error)
	Assign(ctx context.Context, bookingID domain.BookingId, index int) error
}

// RouteHandler は経路割り当て画面（/bookings/{bookingId}/route）のハンドラ。
type RouteHandler struct {
	renderer *sharedweb.Renderer
	assigner RouteAssigner
	finder   BookingFinder
}

// NewRouteHandler は RouteHandler を生成する。
func NewRouteHandler(renderer *sharedweb.Renderer, assigner RouteAssigner, finder BookingFinder) *RouteHandler {
	return &RouteHandler{renderer: renderer, assigner: assigner, finder: finder}
}

// Register はルートを chi ルーターに登録する。
func (h *RouteHandler) Register(r chi.Router) {
	r.Get("/bookings/{bookingId}/route", h.routeForm)
	r.Post("/bookings/{bookingId}/route", h.assign)
}

// routeForm は経路候補一覧を表示する（US08）。
func (h *RouteHandler) routeForm(w http.ResponseWriter, r *http.Request) {
	bookingID, ok := parseBookingID(w, r)
	if !ok {
		return
	}
	candidates, err := h.assigner.Candidates(r.Context(), bookingID)
	if err != nil {
		h.handleError(w, err)
		return
	}
	h.renderer.RenderPage(w, r, "templates/bookings/route.html", routeView(bookingID.Value(), candidates))
}

// assign は選択された経路候補を確定する（US09・PRG で予約詳細へ）。
func (h *RouteHandler) assign(w http.ResponseWriter, r *http.Request) {
	bookingID, ok := parseBookingID(w, r)
	if !ok {
		return
	}
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	index, err := strconv.Atoi(r.FormValue("candidateIndex"))
	if err != nil {
		http.Error(w, "経路候補が選択されていません", http.StatusBadRequest)
		return
	}
	if err := h.assigner.Assign(r.Context(), bookingID, index); err != nil {
		h.handleError(w, err)
		return
	}
	http.Redirect(w, r, "/bookings/"+url.PathEscape(bookingID.Value()), http.StatusSeeOther)
}

// parseBookingID は URL パラメータから予約 ID を解釈する。
func parseBookingID(w http.ResponseWriter, r *http.Request) (domain.BookingId, bool) {
	raw := chi.URLParam(r, "bookingId")
	bookingID, err := domain.NewBookingId(raw)
	if err != nil {
		http.NotFound(w, r)
		return domain.BookingId{}, false
	}
	return bookingID, true
}

// handleError は経路割り当てのエラーを HTTP ステータスへ写像する。
func (h *RouteHandler) handleError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, domain.ErrCargoNotFound):
		http.Error(w, "対象の予約が見つかりません。", http.StatusNotFound)
	case errors.Is(err, application.ErrCandidateOutOfRange):
		http.Error(w, "選択された経路候補が無効です。", http.StatusBadRequest)
	case errors.Is(err, domain.ErrInvalidStatusTransition):
		http.Error(w, "現在の予約状態では経路を確定できません。", http.StatusUnprocessableEntity)
	default:
		http.Error(w, "経路の算出・確定に失敗しました。", http.StatusUnprocessableEntity)
	}
}

// routeView は経路候補一覧を画面テンプレートデータへ変換する。
func routeView(bookingID string, candidates []application.RouteCandidateDTO) map[string]any {
	views := make([]map[string]any, 0, len(candidates))
	for i, c := range candidates {
		legs := make([]map[string]any, 0, len(c.Legs))
		for _, l := range c.Legs {
			legs = append(legs, map[string]any{
				"VoyageNumber": l.VoyageNumber,
				"Load":         l.LoadUnLocode,
				"Unload":       l.UnloadUnLocode,
				"LoadTime":     l.LoadTime.Format(dateLayout),
				"UnloadTime":   l.UnloadTime.Format(dateLayout),
			})
		}
		views = append(views, map[string]any{
			"Index":         i,
			"Legs":          legs,
			"TransitDays":   c.TransitDays,
			"EstimatedCost": c.EstimatedCost,
			"Waypoints":     c.Waypoints,
			"IsDirect":      len(c.Legs) == 1,
		})
	}
	return map[string]any{
		"BookingID":  bookingID,
		"Candidates": views,
		"HasNone":    len(views) == 0,
	}
}

// dateLayout は経路候補表示の日付フォーマット。
const dateLayout = "2006-01-02"
