// Package web は Booking Context の画面ハンドラ（html/template + PRG）を提供する。
package web

import (
	"context"
	"errors"
	"net/http"
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

// BookingHandler は貨物予約画面のハンドラ。
type BookingHandler struct {
	renderer *sharedweb.Renderer
	register Register
}

// NewBookingHandler は BookingHandler を生成する。
func NewBookingHandler(renderer *sharedweb.Renderer, register Register) *BookingHandler {
	return &BookingHandler{renderer: renderer, register: register}
}

// Register はルートを chi ルーターに登録する。
func (h *BookingHandler) Register(r chi.Router) {
	r.Get("/bookings/new", h.newForm)
	r.Post("/bookings", h.create)
	r.Get("/bookings/confirm/{bookingId}", h.confirm)
}

func (h *BookingHandler) newForm(w http.ResponseWriter, r *http.Request) {
	h.renderer.RenderPage(w, r, "templates/bookings/new.html", nil)
}

func (h *BookingHandler) create(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}

	cmd := application.RegisterCargoCommand{
		ShipperID:      r.FormValue("shipperCode"),
		OriginUnLocode: r.FormValue("originUnLocode"),
		DestUnLocode:   r.FormValue("destUnLocode"),
		CargoType:      r.FormValue("cargoType"),
	}
	if v := r.FormValue("weightKg"); v != "" {
		if kg, err := strconv.ParseFloat(v, 64); err == nil {
			cmd.WeightKg = kg
		}
	}
	if v := r.FormValue("arrivalDeadline"); v != "" {
		if d, err := time.Parse("2006-01-02", v); err == nil {
			cmd.ArrivalDeadline = d
		}
	}

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

	// PRG: 予約確認画面へ（予約番号・状態を表示）
	http.Redirect(w, r, "/bookings/confirm/"+bookingID.Value(), http.StatusSeeOther)
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
