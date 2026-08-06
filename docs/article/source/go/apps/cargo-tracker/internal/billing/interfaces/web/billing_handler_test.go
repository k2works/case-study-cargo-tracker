package web_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/application"
	billingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/interfaces/web"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
	"github.com/stretchr/testify/assert"
)

type stubCommand struct {
	number     string
	err        error
	confirmErr error
	gotBooking string
	confirmed  string
}

func (s *stubCommand) Generate(_ context.Context, c application.GenerateInvoiceCommand) (string, error) {
	s.gotBooking = c.BookingID
	return s.number, s.err
}
func (s *stubCommand) Confirm(_ context.Context, n string) error {
	s.confirmed = n
	return s.confirmErr
}

type stubQuery struct {
	list   []application.InvoiceView
	detail application.InvoiceView
	err    error
}

func (s stubQuery) List(_ context.Context) ([]application.InvoiceView, error) { return s.list, nil }
func (s stubQuery) Find(_ context.Context, _ string) (application.InvoiceView, error) {
	return s.detail, s.err
}

func newServer(t *testing.T, cmd billingweb.InvoiceCommand, q billingweb.InvoiceQuery) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := billingweb.NewBillingHandler(renderer, cmd, q)
	r := chi.NewRouter()
	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
			ctx := sharedweb.WithCurrentUser(req.Context(), sharedweb.CurrentUser{Username: "billing", Roles: []string{"ROLE_BILLING"}})
			next.ServeHTTP(w, req.WithContext(ctx))
		})
	})
	h.Register(r)
	return r
}

func detailView() application.InvoiceView {
	return application.InvoiceView{
		InvoiceNumber: "INV-20260720-0001", BookingID: "CARGO-001", ShipperCode: "SHP-1", IsCorporate: true,
		BaseAmount: 100000, DiscountRatePct: 10, DiscountAmount: 10000, TaxAmount: 9000, FinalAmount: 99000,
		Currency: "JPY", StatusJa: "未払い", StatusCode: "PENDING", IssuedAt: "2026-07-20", DueDate: "2026-08-19",
	}
}

func TestBillingHandler_List(t *testing.T) {
	srv := newServer(t, &stubCommand{}, stubQuery{list: []application.InvoiceView{detailView()}})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/billing/invoices", nil))
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "INV-20260720-0001")
}

func TestBillingHandler_Detail_Breakdown(t *testing.T) {
	srv := newServer(t, &stubCommand{}, stubQuery{detail: detailView()})
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/billing/invoices/INV-20260720-0001", nil))
	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "99000")           // 合計
	assert.Contains(t, body, "confirm-payment") // PENDING は支払確認フォーム表示
}

func TestBillingHandler_Generate_PRG(t *testing.T) {
	cmd := &stubCommand{number: "INV-20260720-0001"}
	srv := newServer(t, cmd, stubQuery{})
	form := url.Values{"bookingId": {"CARGO-001"}}
	req := httptest.NewRequest(http.MethodPost, "/billing/invoices", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "/billing/invoices/INV-20260720-0001", rec.Header().Get("Location"))
	assert.Equal(t, "CARGO-001", cmd.gotBooking)
}

func TestBillingHandler_Generate_NotClaimed(t *testing.T) {
	cmd := &stubCommand{err: application.ErrCargoNotClaimed}
	srv := newServer(t, cmd, stubQuery{})
	form := url.Values{"bookingId": {"CARGO-001"}}
	req := httptest.NewRequest(http.MethodPost, "/billing/invoices", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusUnprocessableEntity, rec.Code)
	assert.Contains(t, rec.Body.String(), "引取済み")
}

func TestBillingHandler_Confirm_PRG(t *testing.T) {
	cmd := &stubCommand{}
	srv := newServer(t, cmd, stubQuery{})
	req := httptest.NewRequest(http.MethodPost, "/billing/invoices/INV-20260720-0001/confirm", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "INV-20260720-0001", cmd.confirmed)
}
