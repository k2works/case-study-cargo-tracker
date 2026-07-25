package web_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	bookingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/interfaces/web"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
	"github.com/stretchr/testify/assert"
)

type stubRouteNotifier struct {
	preview    application.RouteNotificationView
	previewErr error
	sent       []application.SentNotificationView
	notified   string
	notifyErr  error
}

func (s *stubRouteNotifier) Preview(_ context.Context, _ domain.BookingId) (application.RouteNotificationView, error) {
	return s.preview, s.previewErr
}
func (s *stubRouteNotifier) Notify(_ context.Context, id domain.BookingId) error {
	s.notified = id.Value()
	return s.notifyErr
}
func (s *stubRouteNotifier) SentNotifications(_ context.Context, _ domain.BookingId) ([]application.SentNotificationView, error) {
	return s.sent, nil
}

func newNotifyServer(t *testing.T, notifier bookingweb.RouteNotifier) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := bookingweb.NewNotifyHandler(renderer, notifier)
	r := chi.NewRouter()
	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
			ctx := sharedweb.WithCurrentUser(req.Context(), sharedweb.CurrentUser{Username: "sales", Roles: []string{"ROLE_SALES"}})
			next.ServeHTTP(w, req.WithContext(ctx))
		})
	})
	h.Register(r)
	return r
}

func TestNotifyHandler_Preview(t *testing.T) {
	notifier := &stubRouteNotifier{preview: application.RouteNotificationView{
		BookingID: "BKG-0001", ShipperCode: "SHP-0001",
		Origin: "JPTYO", Destination: "USLAX", Waypoints: []string{"SGSIN"},
		TransitDays: 12, ArrivalDate: "2026-10-13", AmountValue: 100000, AmountCurr: "JPY",
	}}
	srv := newNotifyServer(t, notifier)
	req := httptest.NewRequest(http.MethodGet, "/bookings/BKG-0001/notify", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	// US12: 通知内容の情報充足（経由港・所要日数・到着予定日・料金概算）
	assert.Contains(t, body, "SGSIN")
	assert.Contains(t, body, "12 日")
	assert.Contains(t, body, "2026-10-13")
	assert.Contains(t, body, "100,000 JPY")
	assert.Contains(t, body, "send-notification")
}

func TestNotifyHandler_Send_PRG(t *testing.T) {
	notifier := &stubRouteNotifier{}
	srv := newNotifyServer(t, notifier)
	req := httptest.NewRequest(http.MethodPost, "/bookings/BKG-0001/notify", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "/bookings/BKG-0001", rec.Header().Get("Location"))
	assert.Equal(t, "BKG-0001", notifier.notified)
}

func TestNotifyHandler_Send_NotRouted(t *testing.T) {
	notifier := &stubRouteNotifier{notifyErr: domain.ErrNotRoutedForNotification}
	srv := newNotifyServer(t, notifier)
	req := httptest.NewRequest(http.MethodPost, "/bookings/BKG-0001/notify", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusUnprocessableEntity, rec.Code)
}
