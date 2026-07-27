package web_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
	"github.com/stretchr/testify/assert"
)

type stubSummaryProvider struct {
	summary sharedweb.DashboardSummary
}

func (s stubSummaryProvider) Summarize(_ context.Context) sharedweb.DashboardSummary {
	return s.summary
}

func newDashboardServer(t *testing.T, user sharedweb.CurrentUser, summary sharedweb.DashboardSummary) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	handler := sharedweb.NewDashboardHandler(renderer, stubSummaryProvider{summary: summary})
	return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		ctx := sharedweb.WithCurrentUser(req.Context(), user)
		handler(w, req.WithContext(ctx))
	})
}

func TestDashboardHandler_ShowsSummaryCardsForRole(t *testing.T) {
	user := sharedweb.CurrentUser{Username: "sales", Roles: []string{"ROLE_SALES"}}
	summary := sharedweb.DashboardSummary{Bookings: 3, Shippers: 5, Estimates: 2}
	srv := newDashboardServer(t, user, summary)

	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))

	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "ダッシュボード")
	// 営業担当者は貨物予約・見積のカードを見られる。
	assert.Contains(t, body, `data-testid="card-bookings"`)
	assert.Contains(t, body, `data-testid="card-estimates"`)
	assert.Contains(t, body, "3") // 貨物予約数
}

func TestDashboardHandler_HidesCardsOutsideRole(t *testing.T) {
	user := sharedweb.CurrentUser{Username: "billing", Roles: []string{"ROLE_BILLING"}}
	srv := newDashboardServer(t, user, sharedweb.DashboardSummary{Invoices: 4, UnpaidInvoices: 1})

	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))

	body := rec.Body.String()
	// 経理担当者は請求カードを見られるが、見積カードは見えない。
	assert.Contains(t, body, `data-testid="card-billing"`)
	assert.NotContains(t, body, `data-testid="card-estimates"`)
}

func TestDashboardHandler_AdminSeesAllCards(t *testing.T) {
	user := sharedweb.CurrentUser{Username: "admin", Roles: []string{"ROLE_ADMIN"}}
	srv := newDashboardServer(t, user, sharedweb.DashboardSummary{})

	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))

	body := rec.Body.String()
	assert.Contains(t, body, `data-testid="card-bookings"`)
	assert.Contains(t, body, `data-testid="card-billing"`)
	assert.Contains(t, body, `data-testid="card-admin"`)
}
