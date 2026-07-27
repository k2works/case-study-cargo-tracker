package web

import (
	"context"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/application"
)

// ExceptionCommand は例外処理・状態手動更新の入力ポート（US17/US19/US20）。
type ExceptionCommand interface {
	RegisterException(ctx context.Context, cmd application.RegisterExceptionCommand) error
	ResolveException(ctx context.Context, cmd application.ResolveExceptionCommand) error
	ManualUpdateStatus(ctx context.Context, cmd application.ManualUpdateStatusCommand) error
}

// ExceptionHandler は例外登録/解決・貨物状態手動更新のハンドラ。
type ExceptionHandler struct {
	renderer *sharedweb.Renderer
	command  ExceptionCommand
	query    TrackingQuery
}

// NewExceptionHandler は ExceptionHandler を生成する。
func NewExceptionHandler(renderer *sharedweb.Renderer, command ExceptionCommand, query TrackingQuery) *ExceptionHandler {
	return &ExceptionHandler{renderer: renderer, command: command, query: query}
}

// Register はルートを chi ルーターに登録する（ROLE_TRACKER 想定）。
func (h *ExceptionHandler) Register(r chi.Router) {
	r.Get("/tracking/{trackingNumber}/exceptions", h.exceptionForm)
	r.Post("/tracking/{trackingNumber}/exceptions", h.registerException)
	r.Post("/tracking/{trackingNumber}/exceptions/{index}/resolve", h.resolveException)
	r.Get("/tracking/{trackingNumber}/status-update", h.statusForm)
	r.Post("/tracking/{trackingNumber}/status-update", h.updateStatus)
}

func (h *ExceptionHandler) exceptionForm(w http.ResponseWriter, r *http.Request) {
	h.renderView(w, r, "templates/tracking/exceptions.html")
}

func (h *ExceptionHandler) statusForm(w http.ResponseWriter, r *http.Request) {
	h.renderView(w, r, "templates/tracking/status_update.html")
}

func (h *ExceptionHandler) renderView(w http.ResponseWriter, r *http.Request, page string) {
	number := chi.URLParam(r, "trackingNumber")
	view, err := h.query.FindByTrackingNumber(r.Context(), number)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	h.renderer.RenderPage(w, r, page, view)
}

func (h *ExceptionHandler) registerException(w http.ResponseWriter, r *http.Request) {
	number := chi.URLParam(r, "trackingNumber")
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	cmd := application.RegisterExceptionCommand{
		TrackingNumber:   number,
		ExceptionType:    r.FormValue("exceptionType"),
		LocationUnLocode: strings.TrimSpace(r.FormValue("location")),
		Description:      strings.TrimSpace(r.FormValue("description")),
		OccurredAt:       parseTimeOrNow(r.FormValue("occurredAt")),
	}
	if err := h.command.RegisterException(r.Context(), cmd); err != nil {
		h.handleCommandError(w, r, err, "例外の登録に失敗しました。入力内容を確認してください。")
		return
	}
	h.redirectDetail(w, r, number)
}

func (h *ExceptionHandler) resolveException(w http.ResponseWriter, r *http.Request) {
	number := chi.URLParam(r, "trackingNumber")
	index, err := strconv.Atoi(chi.URLParam(r, "index"))
	if err != nil {
		http.Error(w, "invalid index", http.StatusBadRequest)
		return
	}
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	cmd := application.ResolveExceptionCommand{
		TrackingNumber:  number,
		Index:           index,
		ResolutionNotes: strings.TrimSpace(r.FormValue("resolutionNotes")),
	}
	if err := h.command.ResolveException(r.Context(), cmd); err != nil {
		h.handleCommandError(w, r, err, "例外対応の記録に失敗しました。")
		return
	}
	h.redirectDetail(w, r, number)
}

func (h *ExceptionHandler) updateStatus(w http.ResponseWriter, r *http.Request) {
	number := chi.URLParam(r, "trackingNumber")
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	cmd := application.ManualUpdateStatusCommand{
		TrackingNumber:   number,
		TransportStatus:  r.FormValue("transportStatus"),
		LocationUnLocode: strings.TrimSpace(r.FormValue("location")),
		CompletionTime:   parseTimeOrNow(r.FormValue("completionTime")),
	}
	if err := h.command.ManualUpdateStatus(r.Context(), cmd); err != nil {
		h.handleCommandError(w, r, err, "貨物状態の更新に失敗しました。")
		return
	}
	h.redirectDetail(w, r, number)
}

func (h *ExceptionHandler) handleCommandError(w http.ResponseWriter, r *http.Request, err error, msg string) {
	if errors.Is(err, application.ErrTrackingNotFound) {
		http.NotFound(w, r)
		return
	}
	w.WriteHeader(http.StatusUnprocessableEntity)
	view, qerr := h.query.FindByTrackingNumber(r.Context(), chi.URLParam(r, "trackingNumber"))
	if qerr != nil {
		http.Error(w, msg, http.StatusUnprocessableEntity)
		return
	}
	h.renderer.RenderPageWithError(w, r, "templates/tracking/exceptions.html", view, msg)
}

func (h *ExceptionHandler) redirectDetail(w http.ResponseWriter, r *http.Request, number string) {
	if !trackingNumberPattern.MatchString(number) {
		http.Error(w, "invalid tracking number", http.StatusBadRequest)
		return
	}
	// number は trackingNumberPattern で TRK 形式に検証済み。
	http.Redirect(w, r, "/tracking/"+number, http.StatusSeeOther) //nolint:gosec // validated tracking number
}

func parseTimeOrNow(v string) time.Time {
	if v == "" {
		return time.Now()
	}
	if t, err := time.Parse("2006-01-02T15:04", v); err == nil {
		return t
	}
	return time.Now()
}
