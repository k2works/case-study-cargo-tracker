package web

import (
	"context"
	"errors"
	"net/http"
	"net/url"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
)

// RouteNotifier は確定経路通知の入力ポート（US12）。
type RouteNotifier interface {
	Preview(ctx context.Context, bookingID domain.BookingId) (application.RouteNotificationView, error)
	Notify(ctx context.Context, bookingID domain.BookingId) error
	SentNotifications(ctx context.Context, bookingID domain.BookingId) ([]application.SentNotificationView, error)
}

// NotifyHandler は確定経路通知画面（/bookings/{bookingId}/notify）のハンドラ。
type NotifyHandler struct {
	renderer *sharedweb.Renderer
	notifier RouteNotifier
}

// NewNotifyHandler は NotifyHandler を生成する。
func NewNotifyHandler(renderer *sharedweb.Renderer, notifier RouteNotifier) *NotifyHandler {
	return &NotifyHandler{renderer: renderer, notifier: notifier}
}

// Register はルートを chi ルーターに登録する。
func (h *NotifyHandler) Register(r chi.Router) {
	r.Get("/bookings/{bookingId}/notify", h.notifyForm)
	r.Post("/bookings/{bookingId}/notify", h.send)
}

// notifyForm は通知内容プレビューと送信済み記録を表示する（US12）。
func (h *NotifyHandler) notifyForm(w http.ResponseWriter, r *http.Request) {
	bookingID, ok := parseBookingID(w, r)
	if !ok {
		return
	}
	view, err := h.notifier.Preview(r.Context(), bookingID)
	if err != nil {
		h.handleError(w, err)
		return
	}
	sent, err := h.notifier.SentNotifications(r.Context(), bookingID)
	if err != nil {
		h.handleError(w, err)
		return
	}
	h.renderer.RenderPage(w, r, "templates/bookings/notify.html", map[string]any{
		"Preview": view,
		"Sent":    sent,
	})
}

// send は荷主へ確定経路を通知し記録する（US12・PRG で予約詳細へ）。
func (h *NotifyHandler) send(w http.ResponseWriter, r *http.Request) {
	bookingID, ok := parseBookingID(w, r)
	if !ok {
		return
	}
	if err := h.notifier.Notify(r.Context(), bookingID); err != nil {
		h.handleError(w, err)
		return
	}
	http.Redirect(w, r, "/bookings/"+url.PathEscape(bookingID.Value()), http.StatusSeeOther)
}

// handleError は通知のエラーを HTTP ステータスへ写像する。
func (h *NotifyHandler) handleError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, domain.ErrCargoNotFound):
		http.Error(w, "対象の予約が見つかりません。", http.StatusNotFound)
	case errors.Is(err, domain.ErrNotRoutedForNotification):
		http.Error(w, "経路が確定していないため通知できません。先に経路を割り当ててください。", http.StatusUnprocessableEntity)
	default:
		http.Error(w, "経路通知に失敗しました。", http.StatusUnprocessableEntity)
	}
}
