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
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/domain"
	shipperweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/interfaces/web"
	webassets "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/web"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// stubRegister は登録サービスのスタブ。
type stubRegister struct {
	lastCmd application.RegisterShipperCommand
	err     error
}

func (s *stubRegister) Register(_ context.Context, cmd application.RegisterShipperCommand) (domain.ShipperId, error) {
	s.lastCmd = cmd
	if s.err != nil {
		return domain.ShipperId{}, s.err
	}
	id, _ := domain.NewShipperId("generated-id")
	return id, nil
}

// stubQuery は照会サービスのスタブ。
type stubQuery struct{ items []application.ShipperListItem }

func (s *stubQuery) List(_ context.Context) ([]application.ShipperListItem, error) {
	return s.items, nil
}

func newTestServer(t *testing.T, reg shipperweb.Register, qry shipperweb.Query) http.Handler {
	t.Helper()
	renderer := sharedweb.NewRenderer(webassets.Templates(), "templates/layout.html")
	h := shipperweb.NewShipperHandler(renderer, reg, qry)
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

func TestShipperHandler_List(t *testing.T) {
	qry := &stubQuery{items: []application.ShipperListItem{{Code: "SHP-ABCDEF12", Name: "山田太郎", Type: "INDIVIDUAL", Email: "taro@example.com"}}}
	srv := newTestServer(t, &stubRegister{}, qry)

	req := httptest.NewRequest(http.MethodGet, "/shippers", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "SHP-ABCDEF12")
	assert.Contains(t, rec.Body.String(), "山田太郎")
}

func TestShipperHandler_NewForm(t *testing.T) {
	srv := newTestServer(t, &stubRegister{}, &stubQuery{})

	req := httptest.NewRequest(http.MethodGet, "/shippers/new", nil)
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
	assert.Contains(t, rec.Body.String(), "荷主登録")
}

func TestShipperHandler_Create_PRG(t *testing.T) {
	reg := &stubRegister{}
	srv := newTestServer(t, reg, &stubQuery{})

	form := url.Values{}
	form.Set("shipperType", "CORPORATE")
	form.Set("name", "株式会社サンプル")
	form.Set("email", "corp@example.com")
	form.Set("contractNumber", "CN-0001")
	form.Set("discountPercent", "10")

	req := httptest.NewRequest(http.MethodPost, "/shippers", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	rec := httptest.NewRecorder()
	srv.ServeHTTP(rec, req)

	// PRG: 登録成功で /shippers へリダイレクト
	assert.Equal(t, http.StatusSeeOther, rec.Code)
	assert.Equal(t, "/shippers", rec.Header().Get("Location"))
	assert.Equal(t, "CORPORATE", reg.lastCmd.ShipperType)
	require.NotNil(t, reg.lastCmd.DiscountRate)
	assert.InDelta(t, 0.10, *reg.lastCmd.DiscountRate, 0.00001)
}
