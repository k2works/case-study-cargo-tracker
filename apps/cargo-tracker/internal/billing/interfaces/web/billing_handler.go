// Package web は Billing Context の画面ハンドラ（html/template + PRG）を提供する。
package web

import (
	"context"
	"errors"
	"net/http"
	"regexp"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/application"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
)

var invoiceNumberPattern = regexp.MustCompile(`^INV-\d{8}-\d{4}$`)

// InvoiceCommand は請求書発行・入金確認の入力ポート（US21/US22/US23）。
type InvoiceCommand interface {
	Generate(ctx context.Context, cmd application.GenerateInvoiceCommand) (string, error)
	Confirm(ctx context.Context, invoiceNumber string) error
}

// InvoiceQuery は請求書照会の入力ポート。
type InvoiceQuery interface {
	List(ctx context.Context) ([]application.InvoiceView, error)
	Find(ctx context.Context, invoiceNumber string) (application.InvoiceView, error)
}

// BillingHandler は精算書（請求書）画面のハンドラ。
type BillingHandler struct {
	renderer *sharedweb.Renderer
	command  InvoiceCommand
	query    InvoiceQuery
}

// NewBillingHandler は BillingHandler を生成する。
func NewBillingHandler(renderer *sharedweb.Renderer, command InvoiceCommand, query InvoiceQuery) *BillingHandler {
	return &BillingHandler{renderer: renderer, command: command, query: query}
}

// Register はルートを chi ルーターに登録する（ROLE_BILLING 想定）。
func (h *BillingHandler) Register(r chi.Router) {
	r.Get("/billing/invoices", h.list)
	r.Post("/billing/invoices", h.generate)
	r.Get("/billing/invoices/{invoiceNumber}", h.detail)
	r.Post("/billing/invoices/{invoiceNumber}/confirm", h.confirm)
}

func (h *BillingHandler) list(w http.ResponseWriter, r *http.Request) {
	views, err := h.query.List(r.Context())
	if err != nil {
		http.Error(w, "請求書一覧の取得に失敗しました", http.StatusInternalServerError)
		return
	}
	h.renderer.RenderPage(w, r, "templates/billing/list.html", views)
}

func (h *BillingHandler) detail(w http.ResponseWriter, r *http.Request) {
	view, err := h.query.Find(r.Context(), chi.URLParam(r, "invoiceNumber"))
	if err != nil {
		http.NotFound(w, r)
		return
	}
	h.renderer.RenderPage(w, r, "templates/billing/detail.html", view)
}

func (h *BillingHandler) generate(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	bookingID := strings.TrimSpace(r.FormValue("bookingId"))
	number, err := h.command.Generate(r.Context(), application.GenerateInvoiceCommand{BookingID: bookingID})
	if err != nil {
		msg := "精算書の発行に失敗しました。"
		switch {
		case errors.Is(err, application.ErrCargoNotClaimed):
			msg = "引取済み（CLAIMED）の予約のみ精算できます。"
		case errors.Is(err, application.ErrAlreadyInvoiced):
			msg = "この予約は既に精算書が発行されています。"
		}
		w.WriteHeader(http.StatusUnprocessableEntity)
		views, _ := h.query.List(r.Context())
		h.renderer.RenderPageWithError(w, r, "templates/billing/list.html", views, msg)
		return
	}
	h.redirectDetail(w, r, number)
}

func (h *BillingHandler) confirm(w http.ResponseWriter, r *http.Request) {
	number := chi.URLParam(r, "invoiceNumber")
	if err := h.command.Confirm(r.Context(), number); err != nil {
		if errors.Is(err, application.ErrInvoiceNotFound) {
			http.NotFound(w, r)
			return
		}
		http.Error(w, "入金確認に失敗しました", http.StatusUnprocessableEntity)
		return
	}
	h.redirectDetail(w, r, number)
}

func (h *BillingHandler) redirectDetail(w http.ResponseWriter, r *http.Request, number string) {
	if !invoiceNumberPattern.MatchString(number) {
		http.Redirect(w, r, "/billing/invoices", http.StatusSeeOther)
		return
	}
	// number は invoiceNumberPattern で INV 形式に検証済み。
	http.Redirect(w, r, "/billing/invoices/"+number, http.StatusSeeOther) //nolint:gosec // validated invoice number
}
