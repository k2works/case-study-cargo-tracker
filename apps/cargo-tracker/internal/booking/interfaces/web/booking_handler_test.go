package web_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	bookingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/interfaces/web"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type stubRegister struct {
	lastCmd application.RegisterCargoCommand
	err     error
}

func (s *stubRegister) Register(_ context.Context, cmd application.RegisterCargoCommand) (domain.BookingId, error) {
	s.lastCmd = cmd
	if s.err != nil {
		return domain.BookingId{}, s.err
	}
	id, _ := domain.NewBookingId("BKG-0001")
	return id, nil
}

func newServer(t *testing.T, reg bookingweb.Register) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := bookingweb.NewBookingHandler(renderer, reg)
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

func TestBookingHandler_NewForm(t *testing.T) {
	srv := newServer(t, &stubRegister{})
	req := httptest.NewRequest(http.MethodGet, "/bookings/new", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "貨物予約登録")
}

func TestBookingHandler_Create_PRG(t *testing.T) {
	reg := &stubRegister{}
	srv := newServer(t, reg)

	form := url.Values{}
	form.Set("shipperCode", "SHP-ABCDEF12")
	form.Set("originUnLocode", "JPTYO")
	form.Set("destUnLocode", "DEHAM")
	form.Set("arrivalDeadline", "2026-09-01")
	form.Set("cargoType", "GENERAL")
	form.Set("weightKg", "1200.5")

	req := httptest.NewRequest(http.MethodPost, "/bookings", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "/bookings", rec.Header().Get("Location"))
	assert.Equal(t, "SHP-ABCDEF12", reg.lastCmd.ShipperID)
	assert.Equal(t, "JPTYO", reg.lastCmd.OriginUnLocode)
	require.InDelta(t, 1200.5, reg.lastCmd.WeightKg, 0.001)
}
