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
)

type stubAssigner struct {
	candidates  []application.RouteCandidateDTO
	candErr     error
	assignErr   error
	assignedIdx int
	assignedID  string
}

func (s *stubAssigner) Candidates(_ context.Context, _ domain.BookingId) ([]application.RouteCandidateDTO, error) {
	return s.candidates, s.candErr
}
func (s *stubAssigner) Assign(_ context.Context, id domain.BookingId, index int) error {
	s.assignedID, s.assignedIdx = id.Value(), index
	return s.assignErr
}

func newRouteServer(t *testing.T, assigner bookingweb.RouteAssigner) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := bookingweb.NewRouteHandler(renderer, assigner, &stubFinder{})
	r := chi.NewRouter()
	r.Use(func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
			ctx := sharedweb.WithCurrentUser(req.Context(), sharedweb.CurrentUser{Username: "designer", Roles: []string{"ROLE_ROUTE_DESIGNER"}})
			next.ServeHTTP(w, req.WithContext(ctx))
		})
	})
	h.Register(r)
	return r
}

func directCandidateDTO() application.RouteCandidateDTO {
	return application.RouteCandidateDTO{
		Legs: []application.RouteLegDTO{
			{VoyageNumber: "V-DIRECT", LoadUnLocode: "JPTYO", UnloadUnLocode: "USLAX"},
		},
		TransitDays: 12, EstimatedCost: 160000,
	}
}

func TestRouteHandler_Form_ShowsCandidates(t *testing.T) {
	srv := newRouteServer(t, &stubAssigner{candidates: []application.RouteCandidateDTO{directCandidateDTO()}})
	req := httptest.NewRequest(http.MethodGet, "/bookings/BKG-0001/route", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "経路割り当て")
	assert.Contains(t, body, "V-DIRECT")
	assert.Contains(t, body, "直行")
}

func TestRouteHandler_Form_NoCandidates(t *testing.T) {
	srv := newRouteServer(t, &stubAssigner{candidates: nil})
	req := httptest.NewRequest(http.MethodGet, "/bookings/BKG-0001/route", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "到達可能な経路候補が見つかりません")
}

func TestRouteHandler_Assign_PRG(t *testing.T) {
	assigner := &stubAssigner{candidates: []application.RouteCandidateDTO{directCandidateDTO()}}
	srv := newRouteServer(t, assigner)
	form := url.Values{}
	form.Set("candidateIndex", "0")
	req := httptest.NewRequest(http.MethodPost, "/bookings/BKG-0001/route", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "/bookings/BKG-0001", rec.Header().Get("Location"))
	assert.Equal(t, 0, assigner.assignedIdx)
	assert.Equal(t, "BKG-0001", assigner.assignedID)
}

func TestRouteHandler_Assign_OutOfRange(t *testing.T) {
	assigner := &stubAssigner{assignErr: application.ErrCandidateOutOfRange}
	srv := newRouteServer(t, assigner)
	form := url.Values{}
	form.Set("candidateIndex", "9")
	req := httptest.NewRequest(http.MethodPost, "/bookings/BKG-0001/route", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusBadRequest, rec.Code)
}

func TestRouteHandler_Assign_InvalidState(t *testing.T) {
	assigner := &stubAssigner{assignErr: domain.ErrInvalidStatusTransition}
	srv := newRouteServer(t, assigner)
	form := url.Values{}
	form.Set("candidateIndex", "0")
	req := httptest.NewRequest(http.MethodPost, "/bookings/BKG-0001/route", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusUnprocessableEntity, rec.Code)
}

func TestRouteHandler_Assign_MissingIndex(t *testing.T) {
	srv := newRouteServer(t, &stubAssigner{})
	req := httptest.NewRequest(http.MethodPost, "/bookings/BKG-0001/route", strings.NewReader(""))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusBadRequest, rec.Code)
}
