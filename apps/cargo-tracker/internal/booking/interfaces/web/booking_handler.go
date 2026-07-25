// Package web は Booking Context の画面ハンドラ（html/template + PRG）を提供する。
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

// Register は貨物予約登録ユースケースの入力ポート。
type Register interface {
	Register(ctx context.Context, cmd application.RegisterCargoCommand) (domain.BookingId, error)
}

// Manage は予約ライフサイクル（確定・キャンセル・差し戻し）の入力ポート（US13）。
type Manage interface {
	Confirm(ctx context.Context, bookingID string) error
	Cancel(ctx context.Context, bookingID string) error
	SendBackToRouting(ctx context.Context, bookingID string) error
}

// BookingFinder は予約詳細表示のための読み取りポート（US13）。
type BookingFinder interface {
	FindByBookingID(ctx context.Context, bookingID domain.BookingId) (*domain.Cargo, error)
}

// Query は貨物予約一覧の読み取りポート（CQRS クエリ側）。
type Query interface {
	List(ctx context.Context) ([]application.CargoListItem, error)
}

// BookingHandler は貨物予約画面のハンドラ。
type BookingHandler struct {
	renderer *sharedweb.Renderer
	register Register
	manage   Manage
	finder   BookingFinder
	query    Query
}

// NewBookingHandler は BookingHandler を生成する。
func NewBookingHandler(renderer *sharedweb.Renderer, register Register, manage Manage, finder BookingFinder, query Query) *BookingHandler {
	return &BookingHandler{renderer: renderer, register: register, manage: manage, finder: finder, query: query}
}

// Register はルートを chi ルーターに登録する。
func (h *BookingHandler) Register(r chi.Router) {
	r.Get("/bookings", h.list)
	r.Get("/bookings/new", h.newForm)
	r.Post("/bookings", h.create)
	r.Get("/bookings/confirm/{bookingId}", h.confirm)
	r.Get("/bookings/{bookingId}", h.detail)
	r.Post("/bookings/{bookingId}/confirm", h.confirmBooking)
	r.Post("/bookings/{bookingId}/cancel", h.cancelBooking)
	r.Post("/bookings/{bookingId}/send-back", h.sendBackBooking)
}

// list は貨物予約の一覧を表示する。
func (h *BookingHandler) list(w http.ResponseWriter, r *http.Request) {
	items, err := h.query.List(r.Context())
	if err != nil {
		http.Error(w, "貨物予約一覧の取得に失敗しました", http.StatusInternalServerError)
		return
	}
	h.renderer.RenderPage(w, r, "templates/bookings/list.html", items)
}

func (h *BookingHandler) newForm(w http.ResponseWriter, r *http.Request) {
	h.renderer.RenderPage(w, r, "templates/bookings/new.html", nil)
}

func (h *BookingHandler) create(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}

	cmd := buildRegisterCommand(r)

	bookingID, err := h.register.Register(r.Context(), cmd)
	if err != nil {
		msg := "予約登録に失敗しました。入力内容を確認してください。"
		if errors.Is(err, application.ErrShipperNotFound) {
			msg = "指定された荷主コードの荷主が見つかりません。荷主コードを確認してください。"
		}
		w.WriteHeader(http.StatusUnprocessableEntity)
		h.renderer.RenderPageWithError(w, r, "templates/bookings/new.html", nil, msg)
		return
	}

	// PRG: 予約確認画面へ（予約番号・状態を表示）。
	// bookingID はドメイン生成の BKG- コードだが、内部固定パスへのセグメントとして
	// PathEscape し外部リダイレクト（open redirect）の余地を残さない。
	http.Redirect(w, r, "/bookings/confirm/"+url.PathEscape(bookingID.Value()), http.StatusSeeOther)
}

// buildRegisterCommand はリクエストフォームから貨物予約登録コマンドを構築する。
// US05 の特殊貨物情報も貨物種別に依らず読み取り、採用可否は application/domain に委ねる。
func buildRegisterCommand(r *http.Request) application.RegisterCargoCommand {
	cmd := application.RegisterCargoCommand{
		ShipperCode:        r.FormValue("shipperCode"),
		OriginUnLocode:     r.FormValue("originUnLocode"),
		DestUnLocode:       r.FormValue("destUnLocode"),
		CargoType:          r.FormValue("cargoType"),
		HazardClass:        r.FormValue("hazardClass"),
		UNNumber:           r.FormValue("unNumber"),
		ProperShippingName: r.FormValue("properShippingName"),
		TemperatureUnit:    r.FormValue("temperatureUnit"),
	}
	if kg, ok := parseFloat(r.FormValue("weightKg")); ok {
		cmd.WeightKg = kg
	}
	if v := r.FormValue("arrivalDeadline"); v != "" {
		if d, err := time.Parse("2006-01-02", v); err == nil {
			cmd.ArrivalDeadline = d
		}
	}
	if t, ok := parseFloat(r.FormValue("minTemperature")); ok {
		cmd.MinTemperature = &t
	}
	if t, ok := parseFloat(r.FormValue("maxTemperature")); ok {
		cmd.MaxTemperature = &t
	}
	return cmd
}

// parseFloat は空でない文字列を float64 に解釈し、成否を返す。
func parseFloat(v string) (float64, bool) {
	if v == "" {
		return 0, false
	}
	f, err := strconv.ParseFloat(v, 64)
	if err != nil {
		return 0, false
	}
	return f, true
}

// confirm は登録直後の予約確認（予約番号・仮受付）を表示する。
func (h *BookingHandler) confirm(w http.ResponseWriter, r *http.Request) {
	bookingID := chi.URLParam(r, "bookingId")
	h.renderer.RenderPage(w, r, "templates/bookings/confirm.html", map[string]string{
		"BookingID": bookingID,
		"Status":    string(domain.BookingStatusPreliminary),
		"StatusJa":  "仮受付",
	})
}

// detail は予約詳細（内容・状態・特殊貨物情報・アクション）を表示する（US13）。
func (h *BookingHandler) detail(w http.ResponseWriter, r *http.Request) {
	raw := chi.URLParam(r, "bookingId")
	bookingID, err := domain.NewBookingId(raw)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	cargo, err := h.finder.FindByBookingID(r.Context(), bookingID)
	if err != nil {
		http.Error(w, "予約が見つかりません", http.StatusNotFound)
		return
	}
	h.renderer.RenderPage(w, r, "templates/bookings/detail.html", detailView(cargo))
}

// detailView は予約集約を詳細画面のテンプレートデータへ変換する。
func detailView(c *domain.Cargo) map[string]any {
	view := map[string]any{
		"BookingID":   c.BookingID().Value(),
		"ShipperCode": c.ShipperCode().Value(),
		"Origin":      c.RouteSpec().Origin().UnLocode(),
		"Destination": c.RouteSpec().Destination().UnLocode(),
		"CargoType":   string(c.CargoType()),
		"Status":      string(c.Status()),
		"StatusJa":    c.Status().Ja(),
		"CanConfirm":  c.CanConfirm(),
		"CanCancel":   c.CanCancel(),
		"CanSendBack": c.CanSendBackToRouting(),
	}
	if h := c.HazardousDeclaration(); h != nil {
		view["Hazardous"] = map[string]string{
			"HazardClass":        h.HazardClass(),
			"UNNumber":           h.UNNumber(),
			"ProperShippingName": h.ProperShippingName(),
		}
	}
	if t := c.TemperatureRequirement(); t != nil {
		view["Temperature"] = map[string]any{
			"Min":  t.MinTemperature(),
			"Max":  t.MaxTemperature(),
			"Unit": string(t.Unit()),
		}
	}
	return view
}

// confirmBooking は予約を確定する（US13）。
func (h *BookingHandler) confirmBooking(w http.ResponseWriter, r *http.Request) {
	h.applyTransition(w, r, h.manage.Confirm)
}

// cancelBooking は予約をキャンセルする（US13）。
func (h *BookingHandler) cancelBooking(w http.ResponseWriter, r *http.Request) {
	h.applyTransition(w, r, h.manage.Cancel)
}

// sendBackBooking は予約を経路再設計へ差し戻す（US13）。
func (h *BookingHandler) sendBackBooking(w http.ResponseWriter, r *http.Request) {
	h.applyTransition(w, r, h.manage.SendBackToRouting)
}

// applyTransition は状態遷移を実行し、PRG で予約詳細へリダイレクトする。
func (h *BookingHandler) applyTransition(w http.ResponseWriter, r *http.Request, action func(context.Context, string) error) {
	raw := chi.URLParam(r, "bookingId")
	if err := action(r.Context(), raw); err != nil {
		status, msg := http.StatusUnprocessableEntity, "予約状態を更新できませんでした。現在の状態では実行できない操作です。"
		switch {
		case errors.Is(err, application.ErrCargoNotFound):
			status, msg = http.StatusNotFound, "対象の予約が見つかりません。"
		case errors.Is(err, domain.ErrInvalidStatusTransition):
			status, msg = http.StatusUnprocessableEntity, "現在の予約状態ではこの操作は実行できません。"
		}
		http.Error(w, msg, status)
		return
	}
	http.Redirect(w, r, "/bookings/"+url.PathEscape(raw), http.StatusSeeOther)
}
