package domain_test

import (
	"testing"
	"time"

	handling "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func loc(t *testing.T, code string) shared.Location {
	t.Helper()
	l, err := shared.NewLocation(code)
	require.NoError(t, err)
	return l
}

// 東京(JPTYO)→シンガポール(SGSIN)→ハンブルク(DEHAM) の 2 区間旅程スナップショット。
func fixtureSnapshot(t *testing.T) handling.CargoSnapshot {
	t.Helper()
	legs := []handling.LegSnapshot{
		handling.NewLegSnapshot(loc(t, "JPTYO"), loc(t, "SGSIN"), "V001"),
		handling.NewLegSnapshot(loc(t, "SGSIN"), loc(t, "DEHAM"), "V002"),
	}
	return handling.NewCargoSnapshot("CARGO-001", loc(t, "JPTYO"), loc(t, "DEHAM"), legs, shared.RoutingStatusRouted)
}

func newActivity(t *testing.T, ht handling.HandlingType, code, voyage, confirmation string) handling.HandlingActivity {
	t.Helper()
	a, err := handling.NewHandlingActivity(
		"CARGO-001", ht, loc(t, code),
		time.Date(2026, 7, 20, 10, 0, 0, 0, time.UTC),
		voyage, confirmation, "作業員A",
	)
	require.NoError(t, err)
	return a
}

func TestNewHandlingActivity_VoyageRequired(t *testing.T) {
	// LOAD は VoyageNumber 必須。
	_, err := handling.NewHandlingActivity(
		"CARGO-001", handling.HandlingTypeLoad, loc(t, "JPTYO"),
		time.Now(), "", "", "作業員A",
	)
	assert.ErrorIs(t, err, handling.ErrVoyageNumberRequired)
}

func TestNewHandlingActivity_ConsigneeConfirmationRequiredForClaim(t *testing.T) {
	// CLAIM は荷受人確認（署名/確認コード）必須（US16）。
	_, err := handling.NewHandlingActivity(
		"CARGO-001", handling.HandlingTypeClaim, loc(t, "DEHAM"),
		time.Now(), "", "", "作業員A",
	)
	assert.ErrorIs(t, err, handling.ErrConsigneeConfirmationRequired)
}

func TestHandlingActivity_IsValidFor_ReceiveAtOrigin(t *testing.T) {
	snap := fixtureSnapshot(t)
	// 出発港で受領 → 妥当。
	r := newActivity(t, handling.HandlingTypeReceive, "JPTYO", "", "").IsValidFor(snap)
	assert.True(t, r.Matched())
	assert.False(t, r.Misrouted())
	assert.False(t, r.Warning())
}

func TestHandlingActivity_IsValidFor_ReceiveAtWrongPort_Warning(t *testing.T) {
	snap := fixtureSnapshot(t)
	// 出発港でない場所で受領 → 警告（MISROUTED ではない）。
	r := newActivity(t, handling.HandlingTypeReceive, "USLAX", "", "").IsValidFor(snap)
	assert.False(t, r.Matched())
	assert.True(t, r.Warning())
	assert.False(t, r.Misrouted())
}

func TestHandlingActivity_IsValidFor_LoadAtItineraryPort(t *testing.T) {
	snap := fixtureSnapshot(t)
	// 積込港（Leg.loadLocation）で積込 → 妥当。
	r := newActivity(t, handling.HandlingTypeLoad, "JPTYO", "V001", "").IsValidFor(snap)
	assert.True(t, r.Matched())
	assert.False(t, r.Misrouted())
}

func TestHandlingActivity_IsValidFor_LoadAtWrongPort_Misrouted(t *testing.T) {
	snap := fixtureSnapshot(t)
	// 旅程外の港で積込 → MISROUTED。
	r := newActivity(t, handling.HandlingTypeLoad, "USLAX", "V999", "").IsValidFor(snap)
	assert.False(t, r.Matched())
	assert.True(t, r.Misrouted())
}

func TestHandlingActivity_IsValidFor_UnloadAtWrongPort_Misrouted(t *testing.T) {
	snap := fixtureSnapshot(t)
	r := newActivity(t, handling.HandlingTypeUnload, "USLAX", "V999", "").IsValidFor(snap)
	assert.True(t, r.Misrouted())
}

func TestHandlingActivity_IsValidFor_ClaimAtDestination(t *testing.T) {
	snap := fixtureSnapshot(t)
	r := newActivity(t, handling.HandlingTypeClaim, "DEHAM", "", "署名:山田").IsValidFor(snap)
	assert.True(t, r.Matched())
	assert.False(t, r.Warning())
}

func TestHandlingActivity_IsValidFor_ClaimAtWrongPort_Warning(t *testing.T) {
	snap := fixtureSnapshot(t)
	r := newActivity(t, handling.HandlingTypeClaim, "SGSIN", "", "署名:山田").IsValidFor(snap)
	assert.True(t, r.Warning())
	assert.False(t, r.Misrouted())
}

func TestHandlingActivity_Accessors(t *testing.T) {
	a := newActivity(t, handling.HandlingTypeLoad, "JPTYO", "V001", "")
	assert.Equal(t, "CARGO-001", a.CargoBookingId())
	assert.Equal(t, handling.HandlingTypeLoad, a.Type())
	assert.Equal(t, "JPTYO", a.Location().UnLocode())
	assert.Equal(t, "V001", a.VoyageNumber())
	assert.Equal(t, "作業員A", a.OperatorName())
	assert.Equal(t, shared.TransportStatusLoaded, a.ResultingTransportStatus())
}
