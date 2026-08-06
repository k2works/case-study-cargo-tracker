package web_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/domain"
	handlingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/interfaces/web"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
	"github.com/stretchr/testify/assert"
)

type stubHandlingCommand struct {
	result application.RegisterHandlingActivityResult
	err    error
	list   []domain.HandlingActivity
	gotCmd application.RegisterHandlingActivityCommand
}

func (s *stubHandlingCommand) Register(_ context.Context, cmd application.RegisterHandlingActivityCommand) (application.RegisterHandlingActivityResult, error) {
	s.gotCmd = cmd
	return s.result, s.err
}
func (s *stubHandlingCommand) ListByBookingID(_ context.Context, _ string) ([]domain.HandlingActivity, error) {
	return s.list, nil
}

func newHandlingServer(t *testing.T, cmd handlingweb.HandlingCommand) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := handlingweb.NewHandlingHandler(renderer, cmd)
	r := chi.NewRouter()
	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
			ctx := sharedweb.WithCurrentUser(req.Context(), sharedweb.CurrentUser{Username: "handler", Roles: []string{"ROLE_HANDLER"}})
			next.ServeHTTP(w, req.WithContext(ctx))
		})
	})
	h.Register(r)
	return r
}

func TestHandlingHandler_ListEmpty(t *testing.T) {
	srv := newHandlingServer(t, &stubHandlingCommand{})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/handling", nil))
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "荷役作業一覧")
}

func TestHandlingHandler_NewForm(t *testing.T) {
	srv := newHandlingServer(t, &stubHandlingCommand{})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/handling/new", nil))
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "confirmation-input")
}

func TestHandlingHandler_CreateSuccess_PRG(t *testing.T) {
	cmd := &stubHandlingCommand{}
	srv := newHandlingServer(t, cmd)
	form := url.Values{
		"bookingId":    {"CARGO-001"},
		"handlingType": {"RECEIVE"},
		"location":     {"JPTYO"},
		"operatorName": {"作業員A"},
	}
	req := httptest.NewRequest(http.MethodPost, "/handling", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Contains(t, rec.Header().Get("Location"), "/handling?bookingId=CARGO-001")
	assert.Equal(t, "RECEIVE", cmd.gotCmd.HandlingType)
}

func TestHandlingHandler_ClaimRequiresConfirmation(t *testing.T) {
	cmd := &stubHandlingCommand{err: domain.ErrConsigneeConfirmationRequired}
	srv := newHandlingServer(t, cmd)
	form := url.Values{"bookingId": {"CARGO-001"}, "handlingType": {"CLAIM"}, "location": {"DEHAM"}}
	req := httptest.NewRequest(http.MethodPost, "/handling", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusUnprocessableEntity, rec.Code)
	assert.Contains(t, rec.Body.String(), "荷受人確認")
}

func TestHandlingHandler_MisroutedFlash(t *testing.T) {
	cmd := &stubHandlingCommand{result: application.RegisterHandlingActivityResult{Misrouted: true}}
	srv := newHandlingServer(t, cmd)
	form := url.Values{"bookingId": {"CARGO-001"}, "handlingType": {"LOAD"}, "location": {"USLAX"}, "voyageNumber": {"V999"}}
	req := httptest.NewRequest(http.MethodPost, "/handling", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Contains(t, rec.Header().Get("Location"), "MISROUTED")
}
