package web_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/application"
	dpweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/interfaces/web"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type stubCommand struct {
	registerErr error
	updateErr   error
	gotRegister application.RegisterCommand
	gotID       string
	gotUpdate   application.UpdateCommand
}

func (s *stubCommand) Register(_ context.Context, c application.RegisterCommand) (string, error) {
	s.gotRegister = c
	return "POL-1", s.registerErr
}
func (s *stubCommand) Update(_ context.Context, id string, c application.UpdateCommand) error {
	s.gotID, s.gotUpdate = id, c
	return s.updateErr
}

type stubQuery struct {
	list []application.PolicyView
	find application.PolicyView
	err  error
}

func (s stubQuery) List(_ context.Context) ([]application.PolicyView, error) { return s.list, nil }
func (s stubQuery) Find(_ context.Context, _ string) (application.PolicyView, error) {
	return s.find, s.err
}

func newServer(t *testing.T, cmd dpweb.Command, q dpweb.Query) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := dpweb.NewDiscountPolicyHandler(renderer, cmd, q)
	r := chi.NewRouter()
	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
			ctx := sharedweb.WithCurrentUser(req.Context(), sharedweb.CurrentUser{Username: "admin", Roles: []string{"ROLE_ADMIN"}})
			next.ServeHTTP(w, req.WithContext(ctx))
		})
	})
	h.Register(r)
	return r
}

func sampleView() application.PolicyView {
	return application.PolicyView{
		ID: "POL-1", PolicyType: "CORPORATE_STANDARD", PolicyTypeJa: "法人標準割引",
		RatePercent: 15, ValidFrom: "2026-01-01", ValidUntil: "無期限", Description: "法人標準",
	}
}

func TestHandler_List(t *testing.T) {
	srv := newServer(t, &stubCommand{}, stubQuery{list: []application.PolicyView{sampleView()}})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/admin/discount-policies", nil))
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "法人標準割引")
	assert.Contains(t, rec.Body.String(), "15.0%")
}

func TestHandler_NewForm(t *testing.T) {
	srv := newServer(t, &stubCommand{}, stubQuery{})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/admin/discount-policies/new", nil))
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), `data-testid="policy-form"`)
	assert.Contains(t, rec.Body.String(), "ボリューム割引")
}

func TestHandler_Create_Success_RedirectsAndConverts(t *testing.T) {
	cmd := &stubCommand{}
	srv := newServer(t, cmd, stubQuery{})
	form := url.Values{
		"policyType":  {"SEASONAL"},
		"ratePercent": {"5"},
		"validFrom":   {"2026-03-01"},
		"validUntil":  {"2026-06-30"},
		"description": {"春季"},
	}
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/admin/discount-policies", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	srv.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "/admin/discount-policies", rec.Header().Get("Location"))
	assert.Equal(t, "SEASONAL", cmd.gotRegister.PolicyType)
	assert.InDelta(t, 0.05, cmd.gotRegister.Rate, 1e-9) // % → 小数へ変換
	require.NotNil(t, cmd.gotRegister.ValidUntil)
}

func TestHandler_Create_InvalidRate_ShowsError(t *testing.T) {
	srv := newServer(t, &stubCommand{}, stubQuery{})
	form := url.Values{
		"policyType": {"SEASONAL"}, "ratePercent": {"abc"}, "validFrom": {"2026-03-01"},
	}
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/admin/discount-policies", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusUnprocessableEntity, rec.Code)
	assert.Contains(t, rec.Body.String(), "入力内容を確認してください")
}

func TestHandler_EditForm(t *testing.T) {
	srv := newServer(t, &stubCommand{}, stubQuery{find: sampleView()})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/admin/discount-policies/POL-1/edit", nil))
	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "割引ポリシー編集")
	assert.Contains(t, body, `value="15"`) // ratePercent 再表示
	assert.Contains(t, body, `value="2026-01-01"`)
}

func TestHandler_Update_Success(t *testing.T) {
	cmd := &stubCommand{}
	srv := newServer(t, cmd, stubQuery{})
	form := url.Values{
		"policyType": {"NONE"}, "ratePercent": {"0"}, "validFrom": {"2026-01-01"},
	}
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/admin/discount-policies/POL-1", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "POL-1", cmd.gotID)
	assert.Equal(t, "NONE", cmd.gotUpdate.PolicyType)
}

func TestHandler_Update_NotFound(t *testing.T) {
	srv := newServer(t, &stubCommand{updateErr: application.ErrPolicyNotFound}, stubQuery{})
	form := url.Values{"policyType": {"NONE"}, "ratePercent": {"0"}, "validFrom": {"2026-01-01"}}
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/admin/discount-policies/MISSING", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusNotFound, rec.Code)
}
