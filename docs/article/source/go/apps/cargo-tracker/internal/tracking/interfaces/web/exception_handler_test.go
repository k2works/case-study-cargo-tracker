package web_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/go-chi/chi/v5"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/application"
	trackingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/interfaces/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
	"github.com/stretchr/testify/assert"
)

type stubExceptionCommand struct {
	registered application.RegisterExceptionCommand
	resolved   application.ResolveExceptionCommand
	updated    application.ManualUpdateStatusCommand
	err        error
}

func (s *stubExceptionCommand) RegisterException(_ context.Context, c application.RegisterExceptionCommand) error {
	s.registered = c
	return s.err
}
func (s *stubExceptionCommand) ResolveException(_ context.Context, c application.ResolveExceptionCommand) error {
	s.resolved = c
	return s.err
}
func (s *stubExceptionCommand) ManualUpdateStatus(_ context.Context, c application.ManualUpdateStatusCommand) error {
	s.updated = c
	return s.err
}

func newExceptionServer(t *testing.T, cmd trackingweb.ExceptionCommand, q trackingweb.TrackingQuery) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := trackingweb.NewExceptionHandler(renderer, cmd, q)
	r := chi.NewRouter()
	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
			ctx := sharedweb.WithCurrentUser(req.Context(), sharedweb.CurrentUser{Username: "tracker", Roles: []string{"ROLE_TRACKER"}})
			next.ServeHTTP(w, req.WithContext(ctx))
		})
	})
	h.Register(r)
	return r
}

func exView() application.TrackingView {
	return application.TrackingView{
		TrackingNumber: "TRK-20260720-0001", BookingID: "CARGO-001", StatusJa: "例外発生",
		Exceptions: []application.TrackingExceptionView{{Index: 0, TypeJa: "遅延", EscalationFlag: false}},
	}
}

func TestExceptionHandler_Form(t *testing.T) {
	srv := newExceptionServer(t, &stubExceptionCommand{}, stubTrackingQuery{view: exView()})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/tracking/TRK-20260720-0001/exceptions", nil))
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "exception-form")
}

func TestExceptionHandler_Register_PRG(t *testing.T) {
	cmd := &stubExceptionCommand{}
	srv := newExceptionServer(t, cmd, stubTrackingQuery{view: exView()})
	form := url.Values{"exceptionType": {"DELAY"}, "location": {"SGSIN"}, "description": {"台風"}}
	req := httptest.NewRequest(http.MethodPost, "/tracking/TRK-20260720-0001/exceptions", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "/tracking/TRK-20260720-0001", rec.Header().Get("Location"))
	assert.Equal(t, "DELAY", cmd.registered.ExceptionType)
}

func TestExceptionHandler_Resolve_PRG(t *testing.T) {
	cmd := &stubExceptionCommand{}
	srv := newExceptionServer(t, cmd, stubTrackingQuery{view: exView()})
	form := url.Values{"resolutionNotes": {"代替便手配"}}
	req := httptest.NewRequest(http.MethodPost, "/tracking/TRK-20260720-0001/exceptions/0/resolve", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, 0, cmd.resolved.Index)
	assert.Equal(t, "代替便手配", cmd.resolved.ResolutionNotes)
}

func TestExceptionHandler_ManualUpdate_PRG(t *testing.T) {
	cmd := &stubExceptionCommand{}
	srv := newExceptionServer(t, cmd, stubTrackingQuery{view: exView()})
	form := url.Values{"transportStatus": {"ONBOARD_CARRIER"}, "location": {"SGSIN"}}
	req := httptest.NewRequest(http.MethodPost, "/tracking/TRK-20260720-0001/status-update", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "ONBOARD_CARRIER", cmd.updated.TransportStatus)
}
