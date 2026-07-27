package domain_test

import (
	"testing"
	"time"

	handling "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
)

func TestHandlingActivity_IsValidFor_Customs_AlwaysValid(t *testing.T) {
	snap := fixtureSnapshot(t)
	// CUSTOMS は場所チェックなし。旅程外の港でも妥当。
	a := newActivity(t, handling.HandlingTypeCustoms, "USLAX", "", "")
	r := a.IsValidFor(snap)
	assert.True(t, r.Matched())
	assert.False(t, r.Warning())
	assert.False(t, r.Misrouted())
}

func TestCargoSnapshot_Accessors(t *testing.T) {
	snap := fixtureSnapshot(t)
	assert.Equal(t, "CARGO-001", snap.BookingId())
	assert.Equal(t, "JPTYO", snap.Origin().UnLocode())
	assert.Equal(t, "DEHAM", snap.Destination().UnLocode())

	legs := snap.Legs()
	assert.Len(t, legs, 2)
	assert.Equal(t, "JPTYO", legs[0].LoadLocation().UnLocode())
	assert.Equal(t, "SGSIN", legs[0].UnloadLocation().UnLocode())
	assert.Equal(t, "V001", legs[0].VoyageNumber())

	// Legs() は防御的コピーを返す（内部状態を破壊しない）。
	legs[0] = handling.NewLegSnapshot(loc(t, "USLAX"), loc(t, "USLAX"), "X")
	assert.Equal(t, "JPTYO", snap.Legs()[0].LoadLocation().UnLocode())
}

func TestHandlingActivity_EmptyBookingId(t *testing.T) {
	_, err := handling.NewHandlingActivity(
		"  ", handling.HandlingTypeReceive, loc(t, "JPTYO"),
		time.Now(), "", "", "作業員A",
	)
	assert.ErrorIs(t, err, handling.ErrEmptyCargoBookingId)
}

func TestHandlingActivity_ZeroLocation(t *testing.T) {
	_, err := handling.NewHandlingActivity(
		"CARGO-001", handling.HandlingTypeReceive, shared.Location{},
		time.Now(), "", "", "作業員A",
	)
	assert.ErrorIs(t, err, handling.ErrZeroLocation)
}

func TestHandlingActivity_ConfirmationAndCompletionTime(t *testing.T) {
	ct := time.Date(2026, 7, 20, 10, 0, 0, 0, time.UTC)
	a, err := handling.NewHandlingActivity(
		"CARGO-001", handling.HandlingTypeClaim, loc(t, "DEHAM"),
		ct, "", "確認コード:AB123", "作業員B",
	)
	assert.NoError(t, err)
	assert.Equal(t, "確認コード:AB123", a.ConsigneeConfirmation())
	assert.Equal(t, ct, a.CompletionTime())
}
