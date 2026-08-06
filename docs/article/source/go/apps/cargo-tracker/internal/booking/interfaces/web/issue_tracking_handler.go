package web

import (
	"context"
	"errors"
	"net/http"
	"regexp"

	"github.com/go-chi/chi/v5"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	sharedweb "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/web"
)

// bookingIDPattern は予約 ID の安全な文字集合を検証する（オープンリダイレクト対策）。
var bookingIDPattern = regexp.MustCompile(`^[A-Za-z0-9-]+$`)

// TrackingIssuer は追跡番号発行の入力ポート（US14）。
type TrackingIssuer interface {
	Assign(ctx context.Context, bookingID string) (string, error)
}

// IssueTrackingHandler は追跡番号発行（/bookings/{bookingId}/tracking-number）のハンドラ。
type IssueTrackingHandler struct {
	renderer *sharedweb.Renderer
	issuer   TrackingIssuer
}

// NewIssueTrackingHandler は IssueTrackingHandler を生成する。
func NewIssueTrackingHandler(renderer *sharedweb.Renderer, issuer TrackingIssuer) *IssueTrackingHandler {
	return &IssueTrackingHandler{renderer: renderer, issuer: issuer}
}

// Register はルートを chi ルーターに登録する。
func (h *IssueTrackingHandler) Register(r chi.Router) {
	r.Post("/bookings/{bookingId}/tracking-number", h.issue)
}

// issue は確定済み予約に追跡番号を発行し、予約詳細へ PRG で戻る（US14）。
func (h *IssueTrackingHandler) issue(w http.ResponseWriter, r *http.Request) {
	bookingID := chi.URLParam(r, "bookingId")
	if !bookingIDPattern.MatchString(bookingID) {
		http.Error(w, "invalid booking id", http.StatusBadRequest)
		return
	}
	if _, err := h.issuer.Assign(r.Context(), bookingID); err != nil {
		if errors.Is(err, domain.ErrInvalidStatusTransition) {
			http.Error(w, "確定済み予約のみ追跡番号を発行できます", http.StatusUnprocessableEntity)
			return
		}
		if errors.Is(err, domain.ErrCargoNotFound) {
			http.NotFound(w, r)
			return
		}
		http.Error(w, "追跡番号の発行に失敗しました", http.StatusInternalServerError)
		return
	}
	// bookingID は上の bookingIDPattern で安全な文字集合に検証済み。
	http.Redirect(w, r, "/bookings/"+bookingID, http.StatusSeeOther) //nolint:gosec // bookingID は安全な文字集合に検証済み
}
