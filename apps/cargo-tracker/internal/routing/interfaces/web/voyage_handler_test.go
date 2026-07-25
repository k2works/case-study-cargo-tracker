package web_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/application"
	routingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/interfaces/web"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
	"github.com/stretchr/testify/assert"
)

type stubRegister struct {
	cmd application.RegisterVoyageCommand
	err error
}

func (s *stubRegister) Register(_ context.Context, cmd application.RegisterVoyageCommand) error {
	s.cmd = cmd
	return s.err
}

type stubUpdate struct{ err error }

func (s *stubUpdate) Update(_ context.Context, _ application.UpdateVoyageCommand) error { return s.err }

type stubQuery struct {
	list   []application.VoyageView
	search []application.VoyageView
}

func (s *stubQuery) List(_ context.Context) ([]application.VoyageView, error) { return s.list, nil }
func (s *stubQuery) Search(_ context.Context, _ application.SearchVoyageCriteria) ([]application.VoyageView, error) {
	return s.search, nil
}

func newServer(t *testing.T, reg routingweb.Register, upd routingweb.Update, q routingweb.Query) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := routingweb.NewVoyageHandler(renderer, reg, upd, q)
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

func TestVoyageHandler_List(t *testing.T) {
	q := &stubQuery{list: []application.VoyageView{{VoyageNumber: "V0001", VesselName: "Ever Given", Carrier: "Evergreen", Origin: "JPTYO", Destination: "USLAX", CargoTypes: []string{"GENERAL"}}}}
	srv := newServer(t, &stubRegister{}, &stubUpdate{}, q)
	req := httptest.NewRequest(http.MethodGet, "/voyages", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "航路一覧")
	assert.Contains(t, body, "V0001")
	assert.Contains(t, body, "/voyages/new")
}

func TestVoyageHandler_NewForm(t *testing.T) {
	srv := newServer(t, &stubRegister{}, &stubUpdate{}, &stubQuery{})
	req := httptest.NewRequest(http.MethodGet, "/voyages/new", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "航海スケジュール登録")
}

func voyageForm() url.Values {
	form := url.Values{}
	form.Set("voyageNumber", "V0001")
	form.Set("vesselName", "Ever Given")
	form.Set("carrier", "Evergreen")
	form.Add("supportedCargoTypes", "GENERAL")
	form.Add("departure", "JPTYO")
	form.Add("arrival", "USLAX")
	form.Add("departureDate", "2026-09-01")
	form.Add("arrivalDate", "2026-09-10")
	return form
}

func TestVoyageHandler_Create_PRG(t *testing.T) {
	reg := &stubRegister{}
	srv := newServer(t, reg, &stubUpdate{}, &stubQuery{})
	req := httptest.NewRequest(http.MethodPost, "/voyages", strings.NewReader(voyageForm().Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "/voyages", rec.Header().Get("Location"))
	assert.Equal(t, "V0001", reg.cmd.VoyageNumber)
	assert.Len(t, reg.cmd.Movements, 1)
}

func TestVoyageHandler_Create_Duplicate(t *testing.T) {
	reg := &stubRegister{err: application.ErrVoyageAlreadyExists}
	srv := newServer(t, reg, &stubUpdate{}, &stubQuery{})
	req := httptest.NewRequest(http.MethodPost, "/voyages", strings.NewReader(voyageForm().Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusUnprocessableEntity, rec.Code)
	assert.Contains(t, rec.Body.String(), "既に登録")
}

func TestVoyageHandler_Search(t *testing.T) {
	q := &stubQuery{search: []application.VoyageView{{VoyageNumber: "R0001", Carrier: "Evergreen"}}}
	srv := newServer(t, &stubRegister{}, &stubUpdate{}, q)
	req := httptest.NewRequest(http.MethodGet, "/voyages/search?cargoType=REFRIGERATED", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "R0001")
}

func TestVoyageHandler_EditForm(t *testing.T) {
	q := &stubQuery{list: []application.VoyageView{{VoyageNumber: "V0001", VesselName: "Ever Given", Carrier: "Evergreen", Origin: "JPTYO", Destination: "USLAX"}}}
	srv := newServer(t, &stubRegister{}, &stubUpdate{}, q)
	req := httptest.NewRequest(http.MethodGet, "/voyages/V0001/edit", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "航海スケジュール更新")
}

func TestVoyageHandler_Update_PRG(t *testing.T) {
	srv := newServer(t, &stubRegister{}, &stubUpdate{}, &stubQuery{})
	form := voyageForm()
	req := httptest.NewRequest(http.MethodPost, "/voyages/V0001", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "/voyages", rec.Header().Get("Location"))
}
