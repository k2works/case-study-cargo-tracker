// Package web は Handling Context の画面ハンドラ（html/template + PRG）を提供する。
package web

import (
	"context"
	"errors"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/domain"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
)

// HandlingCommand は荷役作業登録の入力ポート（US15/US16）。
type HandlingCommand interface {
	Register(ctx context.Context, cmd application.RegisterHandlingActivityCommand) (application.RegisterHandlingActivityResult, error)
	ListByBookingID(ctx context.Context, bookingID string) ([]domain.HandlingActivity, error)
}

// HandlingHandler は荷役作業画面のハンドラ。
type HandlingHandler struct {
	renderer *sharedweb.Renderer
	command  HandlingCommand
}

// NewHandlingHandler は HandlingHandler を生成する。
func NewHandlingHandler(renderer *sharedweb.Renderer, command HandlingCommand) *HandlingHandler {
	return &HandlingHandler{renderer: renderer, command: command}
}

// Register はルートを chi ルーターに登録する。
func (h *HandlingHandler) Register(r chi.Router) {
	r.Get("/handling", h.list)
	r.Get("/handling/new", h.newForm)
	r.Post("/handling", h.create)
}

type handlingRow struct {
	TypeJa         string
	Location       string
	VoyageNumber   string
	CompletionTime string
	Operator       string
}

type handlingListView struct {
	BookingID string
	Rows      []handlingRow
}

func (h *HandlingHandler) list(w http.ResponseWriter, r *http.Request) {
	bookingID := strings.TrimSpace(r.URL.Query().Get("bookingId"))
	view := handlingListView{BookingID: bookingID}
	if bookingID != "" {
		activities, err := h.command.ListByBookingID(r.Context(), bookingID)
		if err != nil {
			http.Error(w, "荷役作業一覧の取得に失敗しました", http.StatusInternalServerError)
			return
		}
		for _, a := range activities {
			view.Rows = append(view.Rows, handlingRow{
				TypeJa:         a.Type().Ja(),
				Location:       a.Location().UnLocode(),
				VoyageNumber:   a.VoyageNumber(),
				CompletionTime: a.CompletionTime().Format("2006-01-02 15:04"),
				Operator:       a.OperatorName(),
			})
		}
	}
	h.renderer.RenderPage(w, r, "templates/handling/list.html", view)
}

func (h *HandlingHandler) newForm(w http.ResponseWriter, r *http.Request) {
	h.renderer.RenderPage(w, r, "templates/handling/new.html", nil)
}

func (h *HandlingHandler) create(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	cmd := application.RegisterHandlingActivityCommand{
		BookingID:             strings.TrimSpace(r.FormValue("bookingId")),
		HandlingType:          r.FormValue("handlingType"),
		LocationUnLocode:      strings.TrimSpace(r.FormValue("location")),
		VoyageNumber:          strings.TrimSpace(r.FormValue("voyageNumber")),
		ConsigneeConfirmation: strings.TrimSpace(r.FormValue("consigneeConfirmation")),
		OperatorName:          strings.TrimSpace(r.FormValue("operatorName")),
		CompletionTime:        time.Now(),
	}
	if v := r.FormValue("completionTime"); v != "" {
		if t, err := time.Parse("2006-01-02T15:04", v); err == nil {
			cmd.CompletionTime = t
		}
	}
	result, err := h.command.Register(r.Context(), cmd)
	if err != nil {
		msg := "荷役作業の登録に失敗しました。入力内容を確認してください。"
		switch {
		case errors.Is(err, domain.ErrVoyageNumberRequired):
			msg = "積込・荷降しには航海番号が必要です。"
		case errors.Is(err, domain.ErrConsigneeConfirmationRequired):
			msg = "引取には荷受人確認（署名または確認コード）が必要です。"
		}
		w.WriteHeader(http.StatusUnprocessableEntity)
		h.renderer.RenderPageWithError(w, r, "templates/handling/new.html", nil, msg)
		return
	}
	flash := ""
	if result.Misrouted {
		flash = "&warning=経路不整合（MISROUTED）を検出しました。"
	} else if result.Warning {
		flash = "&warning=作業場所が予定ルートと異なります。"
	}
	http.Redirect(w, r, "/handling?bookingId="+url.QueryEscape(cmd.BookingID)+flash, http.StatusSeeOther)
}
