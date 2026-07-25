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

// RouteAssigner は経路候補の算出・確定・条件調整の入力ポート（US08/US09/US10）。
type RouteAssigner interface {
	Candidates(ctx context.Context, bookingID domain.BookingId) ([]application.RouteCandidateDTO, error)
	CandidatesWithAdjustment(ctx context.Context, bookingID domain.BookingId, adj application.RouteAdjustment) ([]application.RouteCandidateDTO, error)
	Assign(ctx context.Context, bookingID domain.BookingId, index int) error
	AssignWithAdjustment(ctx context.Context, bookingID domain.BookingId, index int, adj application.RouteAdjustment) error
	Readjust(ctx context.Context, bookingID domain.BookingId) error
}

// NegotiationRequester は条件協議依頼の入力ポート（US10）。
type NegotiationRequester interface {
	Request(ctx context.Context, bookingID domain.BookingId, reason string) error
}

// RouteHandler は経路割り当て画面（/bookings/{bookingId}/route）のハンドラ。
type RouteHandler struct {
	renderer    *sharedweb.Renderer
	assigner    RouteAssigner
	finder      BookingFinder
	negotiation NegotiationRequester
	query       Query
}

// NewRouteHandler は RouteHandler を生成する。
func NewRouteHandler(renderer *sharedweb.Renderer, assigner RouteAssigner, finder BookingFinder, negotiation NegotiationRequester, query Query) *RouteHandler {
	return &RouteHandler{renderer: renderer, assigner: assigner, finder: finder, negotiation: negotiation, query: query}
}

// Register はルートを chi ルーターに登録する。
func (h *RouteHandler) Register(r chi.Router) {
	r.Get("/route-design", h.designList)
	r.Get("/bookings/{bookingId}/route", h.routeForm)
	r.Post("/bookings/{bookingId}/route", h.assign)
	r.Post("/bookings/{bookingId}/route/negotiate", h.negotiate)
	r.Post("/bookings/{bookingId}/route/readjust", h.readjust)
}

// readjust は確定済み経路を MISROUTED にして再調整を開始する（US10・PRG で /route へ）。
func (h *RouteHandler) readjust(w http.ResponseWriter, r *http.Request) {
	bookingID, ok := parseBookingID(w, r)
	if !ok {
		return
	}
	if err := h.assigner.Readjust(r.Context(), bookingID); err != nil {
		h.handleError(w, err)
		return
	}
	http.Redirect(w, r, bookingsPath+url.PathEscape(bookingID.Value())+"/route", http.StatusSeeOther)
}

// parseAdjustment はフォームから条件調整（期限延長）を解釈する（US10）。
func parseAdjustment(r *http.Request) application.RouteAdjustment {
	var adj application.RouteAdjustment
	if v := r.FormValue("overrideDeadline"); v != "" {
		if d, err := time.Parse(dateLayout, v); err == nil {
			adj.OverrideDeadline = d
		}
	}
	return adj
}

// designList は経路設計対象（経路提案中）の予約一覧を表示する（経路設計者の作業入口）。
func (h *RouteHandler) designList(w http.ResponseWriter, r *http.Request) {
	items, err := h.query.List(r.Context())
	if err != nil {
		http.Error(w, "経路設計対象の取得に失敗しました", http.StatusInternalServerError)
		return
	}
	rows := make([]map[string]any, 0, len(items))
	for _, it := range items {
		// 経路提案中（ROUTE_PROPOSED）の予約を経路設計の対象とする。
		if it.Status != string(domain.BookingStatusRouteProposed) {
			continue
		}
		rows = append(rows, map[string]any{
			"BookingID":       it.BookingID,
			"Origin":          it.Origin,
			"Destination":     it.Destination,
			"CargoType":       it.CargoType,
			"RoutingStatusJa": it.RoutingStatusJa,
		})
	}
	h.renderer.RenderPage(w, r, "templates/bookings/route_design.html", rows)
}

// routeForm は経路候補一覧を表示する（US08）。クエリで条件調整（期限延長）を受け再算出（US10）。
func (h *RouteHandler) routeForm(w http.ResponseWriter, r *http.Request) {
	bookingID, ok := parseBookingID(w, r)
	if !ok {
		return
	}
	adj := parseAdjustment(r)
	candidates, err := h.assigner.CandidatesWithAdjustment(r.Context(), bookingID, adj)
	if err != nil {
		h.handleError(w, err)
		return
	}
	// 現在の制約条件（出発地・目的地・貨物種別・期限）を表示し、期限充足を判断できるようにする（US08/US10）。
	var current *domain.Cargo
	if cargo, ferr := h.finder.FindByBookingID(r.Context(), bookingID); ferr == nil {
		current = cargo
	}
	h.renderer.RenderPage(w, r, "templates/bookings/route.html", routeView(bookingID.Value(), candidates, current, adj))
}

// assign は選択された経路候補を確定する（US09/US10・PRG で予約詳細へ）。
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
	if err := h.assigner.AssignWithAdjustment(r.Context(), bookingID, index, parseAdjustment(r)); err != nil {
		h.handleError(w, err)
		return
	}
	http.Redirect(w, r, bookingsPath+url.PathEscape(bookingID.Value()), http.StatusSeeOther)
}

// negotiate は候補ゼロ時に営業担当者へ条件協議を依頼する（US10・PRG で予約詳細へ）。
func (h *RouteHandler) negotiate(w http.ResponseWriter, r *http.Request) {
	bookingID, ok := parseBookingID(w, r)
	if !ok {
		return
	}
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	reason := r.FormValue("reason")
	if reason == "" {
		reason = "期限内に到達可能な経路候補が見つかりません"
	}
	if err := h.negotiation.Request(r.Context(), bookingID, reason); err != nil {
		h.handleError(w, err)
		return
	}
	http.Redirect(w, r, bookingsPath+url.PathEscape(bookingID.Value()), http.StatusSeeOther)
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
// current は現在の予約（制約条件表示用）、adj は適用中の条件調整（US10）。
// 有効期限（調整があれば調整後）で各候補の到着予定日・残日数を算出する。
func routeView(bookingID string, candidates []application.RouteCandidateDTO, current *domain.Cargo, adj application.RouteAdjustment) map[string]any {
	var deadline time.Time
	if current != nil {
		deadline = current.RouteSpec().ArrivalDeadline()
	}
	if !adj.OverrideDeadline.IsZero() {
		deadline = adj.OverrideDeadline
	}
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
				view["ShowDeadline"] = true
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
		"Adjusted":   !adj.OverrideDeadline.IsZero(),
	}
	if !deadline.IsZero() {
		data["ArrivalDeadline"] = deadline.Format(dateLayout)
	}
	if current != nil {
		spec := current.RouteSpec()
		data["Origin"] = spec.Origin().UnLocode()
		data["Destination"] = spec.Destination().UnLocode()
		data["CargoType"] = string(current.CargoType())
		data["OriginalDeadline"] = spec.ArrivalDeadline().Format(dateLayout)
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

// bookingsPath は予約詳細への PRG リダイレクトの基底パス。
const bookingsPath = "/bookings/"
