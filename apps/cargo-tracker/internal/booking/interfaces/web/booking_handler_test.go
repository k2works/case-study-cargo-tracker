package web_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"time"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	bookingweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/interfaces/web"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
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

type stubManage struct {
	called   string
	calledID string
	err      error
}

func (s *stubManage) Confirm(_ context.Context, id string) error {
	s.called, s.calledID = "confirm", id
	return s.err
}
func (s *stubManage) Cancel(_ context.Context, id string) error {
	s.called, s.calledID = "cancel", id
	return s.err
}
func (s *stubManage) SendBackToRouting(_ context.Context, id string) error {
	s.called, s.calledID = "send-back", id
	return s.err
}

type stubFinder struct {
	cargo *domain.Cargo
	err   error
}

func (s *stubFinder) FindByBookingID(_ context.Context, _ domain.BookingId) (*domain.Cargo, error) {
	return s.cargo, s.err
}

type stubQuery struct {
	items []application.CargoListItem
	err   error
}

func (s *stubQuery) List(_ context.Context) ([]application.CargoListItem, error) {
	return s.items, s.err
}

func newServer(t *testing.T, reg bookingweb.Register) http.Handler {
	return newServerFull(t, reg, &stubManage{}, &stubFinder{})
}

func newServerFull(t *testing.T, reg bookingweb.Register, manage bookingweb.Manage, finder bookingweb.BookingFinder) http.Handler {
	return newServerWithQuery(t, reg, manage, finder, &stubQuery{})
}

func newServerWithQuery(t *testing.T, reg bookingweb.Register, manage bookingweb.Manage, finder bookingweb.BookingFinder, query bookingweb.Query) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := bookingweb.NewBookingHandler(renderer, reg, manage, finder, query)
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
	assert.Equal(t, "/bookings/confirm/BKG-0001", rec.Header().Get("Location"))
	assert.Equal(t, "SHP-ABCDEF12", reg.lastCmd.ShipperCode)
	assert.Equal(t, "JPTYO", reg.lastCmd.OriginUnLocode)
	require.InDelta(t, 1200.5, reg.lastCmd.WeightKg, 0.001)
}

// 貨物予約一覧に新規登録リンクと予約行が表示される（ナビ導線）。
func TestBookingHandler_List(t *testing.T) {
	query := &stubQuery{items: []application.CargoListItem{
		{BookingID: "BKG-0001", ShipperCode: "SHP-ABCDEF12", Origin: "JPTYO", Destination: "DEHAM", CargoType: "GENERAL", StatusJa: "仮受付"},
	}}
	srv := newServerWithQuery(t, &stubRegister{}, &stubManage{}, &stubFinder{}, query)

	req := httptest.NewRequest(http.MethodGet, "/bookings", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "貨物予約一覧")
	assert.Contains(t, body, "/bookings/new")
	assert.Contains(t, body, "BKG-0001")
}

// US05: 登録フォームに特殊貨物フィールドが含まれる。
func TestBookingHandler_NewForm_SpecialCargoFields(t *testing.T) {
	srv := newServer(t, &stubRegister{})
	req := httptest.NewRequest(http.MethodGet, "/bookings/new", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)
	body := rec.Body.String()
	assert.Contains(t, body, "hazardous-fields")
	assert.Contains(t, body, "refrigerated-fields")
	assert.Contains(t, body, "un-number")
	assert.Contains(t, body, "min-temperature")
}

// US05: 危険物予約の登録でコマンドに危険物申告が渡る。
func TestBookingHandler_Create_Hazardous(t *testing.T) {
	reg := &stubRegister{}
	srv := newServer(t, reg)

	form := url.Values{}
	form.Set("shipperCode", "SHP-ABCDEF12")
	form.Set("originUnLocode", "JPTYO")
	form.Set("destUnLocode", "DEHAM")
	form.Set("cargoType", "HAZARDOUS")
	form.Set("weightKg", "500")
	form.Set("hazardClass", "3")
	form.Set("unNumber", "UN1203")
	form.Set("properShippingName", "Gasoline")

	req := httptest.NewRequest(http.MethodPost, "/bookings", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "HAZARDOUS", reg.lastCmd.CargoType)
	assert.Equal(t, "UN1203", reg.lastCmd.UNNumber)
	assert.Equal(t, "Gasoline", reg.lastCmd.ProperShippingName)
}

// US13: 予約詳細の表示。
func TestBookingHandler_Detail(t *testing.T) {
	cargo := detailCargoFixture(t)
	srv := newServerFull(t, &stubRegister{}, &stubManage{}, &stubFinder{cargo: cargo})

	req := httptest.NewRequest(http.MethodGet, "/bookings/BKG-0001", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	body := rec.Body.String()
	assert.Contains(t, body, "BKG-0001")
	assert.Contains(t, body, "confirm-booking")
}

// US13: 予約確定アクションが PRG で詳細へリダイレクト。
func TestBookingHandler_ConfirmBooking_PRG(t *testing.T) {
	manage := &stubManage{}
	srv := newServerFull(t, &stubRegister{}, manage, &stubFinder{})

	req := httptest.NewRequest(http.MethodPost, "/bookings/BKG-0001/confirm", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "/bookings/BKG-0001", rec.Header().Get("Location"))
	assert.Equal(t, "confirm", manage.called)
	assert.Equal(t, "BKG-0001", manage.calledID)
}

// US13: キャンセル・差し戻しのアクションが正しいユースケースを呼ぶ。
func TestBookingHandler_CancelAndSendBack(t *testing.T) {
	for _, tc := range []struct {
		path string
		want string
	}{
		{"/bookings/BKG-0001/cancel", "cancel"},
		{"/bookings/BKG-0001/send-back", "send-back"},
	} {
		manage := &stubManage{}
		srv := newServerFull(t, &stubRegister{}, manage, &stubFinder{})
		req := httptest.NewRequest(http.MethodPost, tc.path, nil)
		rec := httptest.NewRecorder()
		srv.ServeHTTP(rec, req)
		assert.Equal(t, http.StatusSeeOther, rec.Code)
		assert.Equal(t, tc.want, manage.called)
	}
}

func detailCargoFixture(t *testing.T) *domain.Cargo {
	t.Helper()
	shipperCode, _ := shared.NewShipperCode("SHP-ABCDEF12")
	bookingID, _ := domain.NewBookingId("BKG-0001")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	spec, _ := domain.NewRouteSpecification(origin, dest, time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC))
	weight, _ := domain.NewWeight(500)
	c, err := domain.RegisterCargo(bookingID, shipperCode, spec, domain.CargoTypeGeneral, weight, domain.NewMoney(0, "JPY"), nil, nil)
	require.NoError(t, err)
	return c
}
