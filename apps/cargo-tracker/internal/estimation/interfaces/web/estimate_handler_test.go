package web_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/domain"
	estimationweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/interfaces/web"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
	"github.com/stretchr/testify/assert"
)

type stubCreate struct {
	cmd application.CreateEstimateCommand
	err error
}

func (s *stubCreate) Create(_ context.Context, cmd application.CreateEstimateCommand) (domain.EstimateId, error) {
	s.cmd = cmd
	if s.err != nil {
		return domain.EstimateId{}, s.err
	}
	id, _ := domain.NewEstimateId("11111111-2222-3333-4444-555555555555")
	return id, nil
}

type stubQuery struct {
	list   []application.EstimateView
	detail application.EstimateView
	err    error
}

func (s *stubQuery) List(_ context.Context) ([]application.EstimateView, error) { return s.list, nil }
func (s *stubQuery) Find(_ context.Context, _ string) (application.EstimateView, error) {
	return s.detail, s.err
}

func newServer(t *testing.T, create estimationweb.Create, q estimationweb.Query) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := estimationweb.NewEstimateHandler(renderer, create, q)
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

func TestEstimateHandler_List(t *testing.T) {
	q := &stubQuery{list: []application.EstimateView{{EstimateId: "e-1", Origin: "JPTYO", Destination: "USLAX", CargoType: "GENERAL", WeightKg: 1200.5, StatusJa: "作成済み"}}}
	srv := newServer(t, &stubCreate{}, q)
	req := httptest.NewRequest(http.MethodGet, "/estimates", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "見積一覧")
	assert.Contains(t, body, "/estimates/new")
}

func TestEstimateHandler_NewForm(t *testing.T) {
	srv := newServer(t, &stubCreate{}, &stubQuery{})
	req := httptest.NewRequest(http.MethodGet, "/estimates/new", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "見積作成")
}

func estimateForm() url.Values {
	form := url.Values{}
	form.Set("origin", "JPTYO")
	form.Set("destination", "USLAX")
	form.Set("arrivalDeadline", "2026-12-31")
	form.Set("cargoType", "GENERAL")
	form.Set("weightKg", "1200.5")
	return form
}

func TestEstimateHandler_Create_PRG(t *testing.T) {
	create := &stubCreate{}
	srv := newServer(t, create, &stubQuery{})
	req := httptest.NewRequest(http.MethodPost, "/estimates", strings.NewReader(estimateForm().Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Contains(t, rec.Header().Get("Location"), "/estimates/")
	assert.Equal(t, "JPTYO", create.cmd.OriginUnLocode)
}

func TestEstimateHandler_Create_NoRoute(t *testing.T) {
	create := &stubCreate{err: application.ErrNoRouteInDeadline}
	srv := newServer(t, create, &stubQuery{})
	req := httptest.NewRequest(http.MethodPost, "/estimates", strings.NewReader(estimateForm().Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusUnprocessableEntity, rec.Code)
	assert.Contains(t, rec.Body.String(), "希望期限に間に合うルート")
}

func TestEstimateHandler_Detail(t *testing.T) {
	q := &stubQuery{detail: application.EstimateView{
		EstimateId: "e-1", Origin: "JPTYO", Destination: "USLAX", CargoType: "GENERAL", WeightKg: 1200.5, StatusJa: "作成済み",
		Candidates: []application.RouteCandidateView{{VoyageNumber: "V-DIRECT", TransitDays: 12, EstimatedCost: 200000}},
	}}
	srv := newServer(t, &stubCreate{}, q)
	req := httptest.NewRequest(http.MethodGet, "/estimates/e-1", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "見積詳細")
	assert.Contains(t, body, "V-DIRECT")
}
