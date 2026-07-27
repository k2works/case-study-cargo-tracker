// Package web は Discount Policy Context の画面ハンドラ（html/template + PRG）を提供する。
package web

import (
	"context"
	"errors"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/domain"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
)

const (
	policiesPath = "/admin/discount-policies"
	dateLayout   = "2006-01-02"
	invalidMsg   = "入力内容を確認してください（割引率は 0〜30%、有効終了日は開始日以降）。"
	newTemplate  = "templates/admin/discount_policies/new.html"
	editTemplate = "templates/admin/discount_policies/edit.html"
	listTemplate = "templates/admin/discount_policies/list.html"
)

// Command は割引ポリシー登録・編集の入力ポート。
type Command interface {
	Register(ctx context.Context, cmd application.RegisterCommand) (string, error)
	Update(ctx context.Context, id string, cmd application.UpdateCommand) error
}

// Query は割引ポリシー照会の入力ポート。
type Query interface {
	List(ctx context.Context) ([]application.PolicyView, error)
	Find(ctx context.Context, id string) (application.PolicyView, error)
}

// DiscountPolicyHandler は割引ポリシー管理画面のハンドラ（ROLE_ADMIN 想定）。
type DiscountPolicyHandler struct {
	renderer *sharedweb.Renderer
	command  Command
	query    Query
}

// NewDiscountPolicyHandler は DiscountPolicyHandler を生成する。
func NewDiscountPolicyHandler(renderer *sharedweb.Renderer, command Command, query Query) *DiscountPolicyHandler {
	return &DiscountPolicyHandler{renderer: renderer, command: command, query: query}
}

// formData はフォーム画面へ渡す表示データ（種別選択肢と再入力値を含む）。
type formData struct {
	Title       string
	Action      string
	PolicyTypes []domain.PolicyType
	ID          string
	PolicyType  string
	RatePercent string
	ValidFrom   string
	ValidUntil  string
	Description string
}

// Register はルートを chi ルーターに登録する。
func (h *DiscountPolicyHandler) Register(r chi.Router) {
	r.Get(policiesPath, h.list)
	r.Get(policiesPath+"/new", h.newForm)
	r.Post(policiesPath, h.create)
	r.Get(policiesPath+"/{id}/edit", h.editForm)
	r.Post(policiesPath+"/{id}", h.update)
}

func (h *DiscountPolicyHandler) list(w http.ResponseWriter, r *http.Request) {
	views, err := h.query.List(r.Context())
	if err != nil {
		http.Error(w, "割引ポリシー一覧の取得に失敗しました", http.StatusInternalServerError)
		return
	}
	h.renderer.RenderPage(w, r, listTemplate, views)
}

func (h *DiscountPolicyHandler) newForm(w http.ResponseWriter, r *http.Request) {
	h.renderer.RenderPage(w, r, newTemplate, formData{
		Title: "割引ポリシー登録", Action: policiesPath, PolicyTypes: domain.AllPolicyTypes(),
	})
}

func (h *DiscountPolicyHandler) create(w http.ResponseWriter, r *http.Request) {
	cmd, form, ok := h.parseForm(w, r)
	if !ok {
		return
	}
	form.Title, form.Action = "割引ポリシー登録", policiesPath
	if _, err := h.command.Register(r.Context(), application.RegisterCommand(cmd)); err != nil {
		h.renderFormError(w, r, newTemplate, form, err)
		return
	}
	http.Redirect(w, r, policiesPath, http.StatusSeeOther)
}

func (h *DiscountPolicyHandler) editForm(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	view, err := h.query.Find(r.Context(), id)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	h.renderer.RenderPage(w, r, editTemplate, formData{
		Title:       "割引ポリシー編集",
		Action:      policiesPath + "/" + id,
		PolicyTypes: domain.AllPolicyTypes(),
		ID:          view.ID,
		PolicyType:  view.PolicyType,
		RatePercent: strconv.FormatFloat(view.RatePercent, 'f', -1, 64),
		ValidFrom:   view.ValidFrom,
		ValidUntil:  editableUntil(view.ValidUntil),
		Description: view.Description,
	})
}

func (h *DiscountPolicyHandler) update(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	cmd, form, ok := h.parseForm(w, r)
	if !ok {
		return
	}
	form.Title, form.Action, form.ID = "割引ポリシー編集", policiesPath+"/"+id, id
	if err := h.command.Update(r.Context(), id, application.UpdateCommand(cmd)); err != nil {
		if errors.Is(err, application.ErrPolicyNotFound) {
			http.NotFound(w, r)
			return
		}
		h.renderFormError(w, r, editTemplate, form, err)
		return
	}
	http.Redirect(w, r, policiesPath, http.StatusSeeOther)
}

// parsedCommand は Register/Update 双方に代入可能な共通フィールド。
type parsedCommand struct {
	PolicyType  string
	Rate        float64
	ValidFrom   time.Time
	ValidUntil  *time.Time
	Description string
}

// parseForm はフォームを解析してコマンドと再表示用フォームデータを返す。
// 形式エラー時は 422 でフォームを再描画し ok=false を返す。
func (h *DiscountPolicyHandler) parseForm(w http.ResponseWriter, r *http.Request) (parsedCommand, formData, bool) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return parsedCommand{}, formData{}, false
	}
	form := formData{
		PolicyTypes: domain.AllPolicyTypes(),
		PolicyType:  r.FormValue("policyType"),
		RatePercent: r.FormValue("ratePercent"),
		ValidFrom:   r.FormValue("validFrom"),
		ValidUntil:  r.FormValue("validUntil"),
		Description: r.FormValue("description"),
	}

	pct, err := strconv.ParseFloat(r.FormValue("ratePercent"), 64)
	if err != nil {
		h.renderFormError(w, r, formTemplate(r), form, domain.ErrInvalidRate)
		return parsedCommand{}, formData{}, false
	}
	validFrom, err := time.Parse(dateLayout, r.FormValue("validFrom"))
	if err != nil {
		h.renderFormError(w, r, formTemplate(r), form, domain.ErrInvalidPeriod)
		return parsedCommand{}, formData{}, false
	}
	var until *time.Time
	if v := r.FormValue("validUntil"); v != "" {
		t, err := time.Parse(dateLayout, v)
		if err != nil {
			h.renderFormError(w, r, formTemplate(r), form, domain.ErrInvalidPeriod)
			return parsedCommand{}, formData{}, false
		}
		until = &t
	}
	return parsedCommand{
		PolicyType:  r.FormValue("policyType"),
		Rate:        pct / 100.0,
		ValidFrom:   validFrom,
		ValidUntil:  until,
		Description: r.FormValue("description"),
	}, form, true
}

func (h *DiscountPolicyHandler) renderFormError(w http.ResponseWriter, r *http.Request, tmpl string, form formData, err error) {
	w.WriteHeader(http.StatusUnprocessableEntity)
	h.renderer.RenderPageWithError(w, r, tmpl, form, errorMessage(err))
}

// errorMessage はドメインエラーを利用者向けの具体的な日本語メッセージへ写像する。
func errorMessage(err error) string {
	switch {
	case errors.Is(err, domain.ErrInvalidRate):
		return domain.ErrInvalidRate.Error() + "。"
	case errors.Is(err, domain.ErrInvalidPeriod):
		return domain.ErrInvalidPeriod.Error() + "。"
	case errors.Is(err, domain.ErrInvalidPolicyType):
		return domain.ErrInvalidPolicyType.Error() + "。"
	default:
		return invalidMsg
	}
}

// formTemplate は編集フォーム（URL に /new を含まない）か新規フォームかを判定する。
func formTemplate(r *http.Request) string {
	if chi.URLParam(r, "id") != "" {
		return editTemplate
	}
	return newTemplate
}

func editableUntil(s string) string {
	if s == "無期限" {
		return ""
	}
	return s
}
