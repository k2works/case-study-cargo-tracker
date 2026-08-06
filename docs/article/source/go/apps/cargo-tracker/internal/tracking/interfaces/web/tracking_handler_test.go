package web_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/go-chi/chi/v5"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/application"
	trackingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/interfaces/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
	"github.com/stretchr/testify/assert"
)

type stubTrackingQuery struct {
	view application.TrackingView
	err  error
}

func (s stubTrackingQuery) FindByTrackingNumber(_ context.Context, _ string) (application.TrackingView, error) {
	return s.view, s.err
}

func newTrackingServer(t *testing.T, q trackingweb.TrackingQuery) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := trackingweb.NewTrackingHandler(renderer, q)
	r := chi.NewRouter()
	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
			ctx := sharedweb.WithCurrentUser(req.Context(), sharedweb.CurrentUser{Username: "shipper", Roles: []string{"ROLE_SHIPPER"}})
			next.ServeHTTP(w, req.WithContext(ctx))
		})
	})
	h.Register(r)
	h.RegisterPublic(r)
	return r
}

func TestTrackingHandler_Input(t *testing.T) {
	srv := newTrackingServer(t, stubTrackingQuery{})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/tracking", nil))
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "tracking-id-input")
}

func TestTrackingHandler_InvalidFormat(t *testing.T) {
	srv := newTrackingServer(t, stubTrackingQuery{})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/tracking?trackingNumber=INVALID", nil))
	assert.Equal(t, http.StatusBadRequest, rec.Code)
	assert.Contains(t, rec.Body.String(), "形式が正しくありません")
}

func TestTrackingHandler_ValidRedirect(t *testing.T) {
	srv := newTrackingServer(t, stubTrackingQuery{})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/tracking?trackingNumber=TRK-20260720-0001", nil))
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "/tracking/TRK-20260720-0001", rec.Header().Get("Location"))
}

func TestTrackingHandler_Detail(t *testing.T) {
	view := application.TrackingView{
		TrackingNumber: "TRK-20260720-0001", BookingID: "CARGO-001",
		StatusJa: "受領済", CurrentLocation: "JPTYO",
		Events: []application.TrackingEventView{{TypeJa: "受領", Location: "JPTYO", StatusJa: "受領済", CompletionTime: "2026-07-20 10:00"}},
	}
	srv := newTrackingServer(t, stubTrackingQuery{view: view})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/tracking/TRK-20260720-0001", nil))
	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "TRK-20260720-0001")
	assert.Contains(t, body, "受領済")
	assert.Contains(t, body, "JPTYO")
}

func TestTrackingHandler_NotFound(t *testing.T) {
	srv := newTrackingServer(t, stubTrackingQuery{err: application.ErrTrackingNotFound})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/tracking/TRK-20260720-9999", nil))
	assert.Equal(t, http.StatusNotFound, rec.Code)
	assert.Contains(t, rec.Body.String(), "追跡番号が見つかりません")
}

func TestTrackingHandler_PublicDetail(t *testing.T) {
	view := application.TrackingView{TrackingNumber: "TRK-20260720-0001", StatusJa: "積込済", CurrentLocation: "SGSIN"}
	srv := newTrackingServer(t, stubTrackingQuery{view: view})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/public/tracking/TRK-20260720-0001", nil))
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "積込済")
}
