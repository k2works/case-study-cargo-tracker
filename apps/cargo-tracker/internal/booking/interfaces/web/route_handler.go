package web

import (
	"context"
	"errors"
	"net/http"
	"net/url"
	"strconv"
	"time"

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
	// 到着期限を表示し、各候補の期限充足を判断できるようにする（US08）。
	var deadline time.Time
	if cargo, ferr := h.finder.FindByBookingID(r.Context(), bookingID); ferr == nil && cargo != nil {
		deadline = cargo.RouteSpec().ArrivalDeadline()
	}
	h.renderer.RenderPage(w, r, "templates/bookings/route.html", routeView(bookingID.Value(), candidates, deadline))
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
// deadline は予約の到着期限。各候補の到着予定日と期限までの残日数を算出して表示する（US08）。
func routeView(bookingID string, candidates []application.RouteCandidateDTO, deadline time.Time) map[string]any {
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
		view := map[string]any{
			"Index":         i,
			"Legs":          legs,
			"TransitDays":   c.TransitDays,
			"EstimatedCost": c.EstimatedCost,
			"Waypoints":     c.Waypoints,
			"IsDirect":      len(c.Legs) == 1,
		}
		if n := len(c.Legs); n > 0 {
			arrival := c.Legs[n-1].UnloadTime
			view["ArrivalDate"] = arrival.Format(dateLayout)
			if !deadline.IsZero() {
				days := daysUntil(deadline, arrival)
				view["DaysToDeadline"] = days
				view["OverDeadline"] = days < 0
			}
		}
		views = append(views, view)
	}
	data := map[string]any{
		"BookingID":  bookingID,
		"Candidates": views,
		"HasNone":    len(views) == 0,
	}
	if !deadline.IsZero() {
		data["ArrivalDeadline"] = deadline.Format(dateLayout)
	}
	return data
}

// daysUntil は到着日から期限までの残日数（日付単位）を返す。負値は期限超過。
func daysUntil(deadline, arrival time.Time) int {
	d := time.Date(deadline.Year(), deadline.Month(), deadline.Day(), 0, 0, 0, 0, deadline.Location())
	a := time.Date(arrival.Year(), arrival.Month(), arrival.Day(), 0, 0, 0, 0, arrival.Location())
	return int(d.Sub(a).Hours() / 24)
}

// dateLayout は経路候補表示の日付フォーマット。
const dateLayout = "2006-01-02"
