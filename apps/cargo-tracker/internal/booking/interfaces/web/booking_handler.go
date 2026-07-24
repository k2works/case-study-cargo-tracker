// Package web は Booking Context の画面ハンドラ（html/template + PRG）を提供する。
package web

import (
	"context"
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

	if _, err := h.register.Register(r.Context(), cmd); err != nil {
		w.WriteHeader(http.StatusUnprocessableEntity)
		h.renderer.RenderPage(w, r, "templates/bookings/new.html", nil)
		return
	}

	http.Redirect(w, r, "/bookings", http.StatusSeeOther)
}
