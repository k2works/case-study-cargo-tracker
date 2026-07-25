// Package web は Routing Context の画面ハンドラ（html/template + PRG）を提供する。
package web

import (
	"context"
	"errors"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/application"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
)

// Register は航海登録ユースケースの入力ポート。
type Register interface {
	Register(ctx context.Context, cmd application.RegisterVoyageCommand) error
}

// Update は航海更新ユースケースの入力ポート。
type Update interface {
	Update(ctx context.Context, cmd application.UpdateVoyageCommand) error
}

// Query は航海一覧・検索の入力ポート。
type Query interface {
	List(ctx context.Context) ([]application.VoyageView, error)
	Search(ctx context.Context, c application.SearchVoyageCriteria) ([]application.VoyageView, error)
}

// VoyageHandler は航海スケジュール画面のハンドラ。
type VoyageHandler struct {
	renderer *sharedweb.Renderer
	register Register
	update   Update
	query    Query
}

// NewVoyageHandler は VoyageHandler を生成する。
func NewVoyageHandler(renderer *sharedweb.Renderer, register Register, update Update, query Query) *VoyageHandler {
	return &VoyageHandler{renderer: renderer, register: register, update: update, query: query}
}

// Register はルートを chi ルーターに登録する。
func (h *VoyageHandler) Register(r chi.Router) {
	r.Get("/voyages", h.list)
	r.Get("/voyages/new", h.newForm)
	r.Post("/voyages", h.create)
	r.Get("/voyages/search", h.search)
	r.Get("/voyages/{voyageNumber}/edit", h.editForm)
	r.Post("/voyages/{voyageNumber}", h.updateVoyage)
}

func (h *VoyageHandler) list(w http.ResponseWriter, r *http.Request) {
	views, err := h.query.List(r.Context())
	if err != nil {
		http.Error(w, "航路一覧の取得に失敗しました", http.StatusInternalServerError)
		return
	}
	h.renderer.RenderPage(w, r, "templates/voyages/list.html", views)
}

func (h *VoyageHandler) newForm(w http.ResponseWriter, r *http.Request) {
	h.renderer.RenderPage(w, r, "templates/voyages/new.html", nil)
}

func (h *VoyageHandler) create(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	cmd := application.RegisterVoyageCommand{
		VoyageNumber:        r.FormValue("voyageNumber"),
		VesselName:          r.FormValue("vesselName"),
		Carrier:             r.FormValue("carrier"),
		SupportedCargoTypes: r.Form["supportedCargoTypes"],
		Movements:           parseMovements(r),
	}
	if err := h.register.Register(r.Context(), cmd); err != nil {
		msg := "航海スケジュールの登録に失敗しました。入力内容を確認してください。"
		if errors.Is(err, application.ErrVoyageAlreadyExists) {
			msg = "同一の航海番号が既に登録されています。"
		}
		w.WriteHeader(http.StatusUnprocessableEntity)
		h.renderer.RenderPageWithError(w, r, "templates/voyages/new.html", nil, msg)
		return
	}
	http.Redirect(w, r, "/voyages", http.StatusSeeOther)
}

func (h *VoyageHandler) editForm(w http.ResponseWriter, r *http.Request) {
	number := chi.URLParam(r, "voyageNumber")
	views, err := h.query.List(r.Context())
	if err != nil {
		http.Error(w, "取得に失敗しました", http.StatusInternalServerError)
		return
	}
	for _, v := range views {
		if v.VoyageNumber == number {
			h.renderer.RenderPage(w, r, "templates/voyages/edit.html", v)
			return
		}
	}
	http.NotFound(w, r)
}

func (h *VoyageHandler) updateVoyage(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	number := chi.URLParam(r, "voyageNumber")
	cmd := application.UpdateVoyageCommand{
		VoyageNumber:        number,
		VesselName:          r.FormValue("vesselName"),
		Carrier:             r.FormValue("carrier"),
		SupportedCargoTypes: r.Form["supportedCargoTypes"],
		Movements:           parseMovements(r),
	}
	if err := h.update.Update(r.Context(), cmd); err != nil {
		http.Error(w, "航海スケジュールの更新に失敗しました: 入力内容を確認してください", http.StatusUnprocessableEntity)
		return
	}
	http.Redirect(w, r, "/voyages", http.StatusSeeOther)
}

func (h *VoyageHandler) search(w http.ResponseWriter, r *http.Request) {
	c := application.SearchVoyageCriteria{
		OriginUnLocode:      r.URL.Query().Get("origin"),
		DestinationUnLocode: r.URL.Query().Get("destination"),
		CargoType:           r.URL.Query().Get("cargoType"),
	}
	if v := r.URL.Query().Get("departureFrom"); v != "" {
		if d, err := time.Parse("2006-01-02", v); err == nil {
			c.DepartureFrom = d
		}
	}
	if v := r.URL.Query().Get("departureTo"); v != "" {
		if d, err := time.Parse("2006-01-02", v); err == nil {
			c.DepartureTo = d
		}
	}
	var (
		views []application.VoyageView
		err   error
	)
	searched := r.URL.Query().Has("origin") || r.URL.Query().Has("destination") || r.URL.Query().Has("cargoType")
	if searched {
		views, err = h.query.Search(r.Context(), c)
	}
	if err != nil {
		http.Error(w, "検索に失敗しました", http.StatusInternalServerError)
		return
	}
	h.renderer.RenderPage(w, r, "templates/voyages/search.html", map[string]any{
		"Results":  views,
		"Searched": searched,
		"Criteria": c,
	})
}

// parseMovements はインデックス付きフォーム値から運送区間の並びを構築する。
// departure[i]・arrival[i]・departureDate[i]・arrivalDate[i] を seq=i+1 で読み取る。
func parseMovements(r *http.Request) []application.MovementInput {
	deps := r.Form["departure"]
	arrs := r.Form["arrival"]
	depDates := r.Form["departureDate"]
	arrDates := r.Form["arrivalDate"]
	n := len(deps)
	movements := make([]application.MovementInput, 0, n)
	for i := 0; i < n; i++ {
		if deps[i] == "" {
			continue
		}
		m := application.MovementInput{
			DepartureUnLocode: deps[i],
			ArrivalUnLocode:   at(arrs, i),
			Seq:               len(movements) + 1,
		}
		if d, err := time.Parse("2006-01-02", at(depDates, i)); err == nil {
			m.DepartureTime = d
		}
		if d, err := time.Parse("2006-01-02", at(arrDates, i)); err == nil {
			m.ArrivalTime = d
		}
		movements = append(movements, m)
	}
	return movements
}

func at(s []string, i int) string {
	if i < len(s) {
		return s[i]
	}
	return ""
}
