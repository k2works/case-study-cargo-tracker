// Package web は Shipper Context の画面ハンドラ（html/template + PRG）を提供する。
package web

import (
	"context"
	"errors"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/application"
)

// Register は荷主登録ユースケースの入力ポート（interfaces 視点）。
type Register interface {
	Register(ctx context.Context, cmd application.RegisterShipperCommand) (shared.ShipperId, error)
}

// Query は荷主照会ユースケースの入力ポート。
type Query interface {
	List(ctx context.Context) ([]application.ShipperListItem, error)
}

// ShipperHandler は荷主画面のハンドラ。
type ShipperHandler struct {
	renderer *sharedweb.Renderer
	register Register
	query    Query
}

// NewShipperHandler は ShipperHandler を生成する。
func NewShipperHandler(renderer *sharedweb.Renderer, register Register, query Query) *ShipperHandler {
	return &ShipperHandler{renderer: renderer, register: register, query: query}
}

// Register はルートを chi ルーターに登録する。
func (h *ShipperHandler) Register(r chi.Router) {
	r.Get("/shippers", h.list)
	r.Get("/shippers/new", h.newForm)
	r.Post("/shippers", h.create)
}

func (h *ShipperHandler) list(w http.ResponseWriter, r *http.Request) {
	items, err := h.query.List(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	h.renderer.RenderPage(w, r, "templates/shippers/list.html", items)
}

func (h *ShipperHandler) newForm(w http.ResponseWriter, r *http.Request) {
	h.renderer.RenderPage(w, r, "templates/shippers/new.html", nil)
}

func (h *ShipperHandler) create(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}

	cmd := application.RegisterShipperCommand{
		Name:        r.FormValue("name"),
		Email:       r.FormValue("email"),
		ShipperType: r.FormValue("shipperType"),
	}
	if v := r.FormValue("phone"); v != "" {
		cmd.Phone = &v
	}
	if v := r.FormValue("address"); v != "" {
		cmd.Address = &v
	}
	if v := r.FormValue("contractNumber"); v != "" {
		cmd.ContractNumber = &v
	}
	if v := r.FormValue("discountPercent"); v != "" {
		if pct, err := strconv.ParseFloat(v, 64); err == nil {
			rate := pct / 100.0
			cmd.DiscountRate = &rate
		}
	}

	if _, err := h.register.Register(r.Context(), cmd); err != nil {
		msg := "登録に失敗しました。入力内容を確認してください。"
		if errors.Is(err, application.ErrEmailAlreadyRegistered) {
			msg = "このメールアドレスは既に荷主として登録されています。既存の荷主をご利用ください。"
		}
		w.WriteHeader(http.StatusUnprocessableEntity)
		h.renderer.RenderPageWithError(w, r, "templates/shippers/new.html", nil, msg)
		return
	}

	// PRG: 登録成功で一覧へリダイレクト
	http.Redirect(w, r, "/shippers", http.StatusSeeOther)
}
