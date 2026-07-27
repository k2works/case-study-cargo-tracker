package domain_test

import (
	"testing"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	tracking "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseExceptionType(t *testing.T) {
	for _, v := range []string{"DELAY", "DAMAGE", "LOST", "CUSTOMS_HOLD"} {
		et, err := tracking.ParseExceptionType(v)
		require.NoError(t, err)
		assert.Equal(t, v, et.String())
	}
	_, err := tracking.ParseExceptionType("UNKNOWN")
	assert.ErrorIs(t, err, tracking.ErrUnknownExceptionType)
}

func TestExceptionTypeJa(t *testing.T) {
	assert.Equal(t, "遅延", tracking.ExceptionTypeDelay.Ja())
	assert.Equal(t, "破損", tracking.ExceptionTypeDamage.Ja())
	assert.Equal(t, "紛失", tracking.ExceptionTypeLost.Ja())
	assert.Equal(t, "税関保留", tracking.ExceptionTypeCustomsHold.Ja())
}

func occurred() time.Time { return time.Date(2026, 7, 1, 0, 0, 0, 0, time.UTC) }

func TestEscalationPolicy_Lost_Immediate(t *testing.T) {
	// 紛失は即時エスカレーション（経過時間に依らず true）。
	p := tracking.EscalationPolicy{}
	assert.True(t, p.RequiresEscalation(tracking.ExceptionTypeLost, occurred(), occurred()))
}

func TestEscalationPolicy_Delay_48hBoundary(t *testing.T) {
	p := tracking.EscalationPolicy{}
	tests := []struct {
		name     string
		now      time.Time
		expected bool
	}{
		{"47時間59分は不要", occurred().Add(47*time.Hour + 59*time.Minute), false},
		{"ちょうど48時間は不要（超過が条件）", occurred().Add(48 * time.Hour), false},
		{"48時間1分でエスカレーション", occurred().Add(48*time.Hour + time.Minute), true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, p.RequiresEscalation(tracking.ExceptionTypeDelay, occurred(), tt.now))
		})
	}
}

func TestEscalationPolicy_Damage_NoEscalation(t *testing.T) {
	p := tracking.EscalationPolicy{}
	assert.False(t, p.RequiresEscalation(tracking.ExceptionTypeDamage, occurred(), occurred().Add(100*time.Hour)))
}

func newTrackingWithEvent(t *testing.T) *tracking.TrackingActivity {
	t.Helper()
	tn, err := tracking.NewTrackingNumber("TRK-20260720-0001")
	require.NoError(t, err)
	ta, err := tracking.NewTrackingActivity(tn, "CARGO-001")
	require.NoError(t, err)
	loc, _ := shared.NewLocation("JPTYO")
	ta.AddEvent(tracking.NewTrackingActivityEvent("LOAD", loc, occurred(), "V001", shared.TransportStatusLoaded))
	return &ta
}

func TestTrackingActivity_AddException_SetsExceptionStatus(t *testing.T) {
	ta := newTrackingWithEvent(t)
	require.Equal(t, shared.TransportStatusLoaded, ta.CurrentStatus())

	loc, _ := shared.NewLocation("SGSIN")
	ex := tracking.NewTrackingExceptionEvent(tracking.ExceptionTypeDelay, loc, occurred(), "台風による遅延", false)
	ta.AddException(ex)

	assert.True(t, ta.HasActiveException())
	// 未解決例外がある間は EXCEPTION。
	assert.Equal(t, shared.TransportStatusException, ta.CurrentStatus())
	assert.Len(t, ta.Exceptions(), 1)
}

func TestTrackingActivity_ResolveException_RestoresStatus(t *testing.T) {
	ta := newTrackingWithEvent(t)
	loc, _ := shared.NewLocation("SGSIN")
	ta.AddException(tracking.NewTrackingExceptionEvent(tracking.ExceptionTypeDelay, loc, occurred(), "遅延", false))
	require.Equal(t, shared.TransportStatusException, ta.CurrentStatus())

	resolvedAt := occurred().Add(72 * time.Hour)
	require.NoError(t, ta.ResolveException(0, "代替便手配で解消", resolvedAt))

	assert.False(t, ta.HasActiveException())
	// 解決で発生前状態（LOADED）に復帰。
	assert.Equal(t, shared.TransportStatusLoaded, ta.CurrentStatus())
	assert.True(t, ta.Exceptions()[0].IsResolved())
	assert.Equal(t, "代替便手配で解消", ta.Exceptions()[0].ResolutionNotes())
}

func TestTrackingActivity_ResolveException_InvalidIndex(t *testing.T) {
	ta := newTrackingWithEvent(t)
	err := ta.ResolveException(5, "notes", occurred())
	assert.ErrorIs(t, err, tracking.ErrExceptionNotFound)
}

func TestTrackingActivity_ResolveException_DoubleResolveRejected(t *testing.T) {
	ta := newTrackingWithEvent(t)
	loc, _ := shared.NewLocation("SGSIN")
	ta.AddException(tracking.NewTrackingExceptionEvent(tracking.ExceptionTypeDelay, loc, occurred(), "遅延", false))
	require.NoError(t, ta.ResolveException(0, "解消", occurred().Add(time.Hour)))
	// 二重解決は拒否される。
	err := ta.ResolveException(0, "再解決", occurred().Add(2*time.Hour))
	assert.ErrorIs(t, err, tracking.ErrExceptionAlreadyResolved)
}

func TestTrackingActivity_MultipleExceptions_PartialResolve(t *testing.T) {
	ta := newTrackingWithEvent(t)
	loc, _ := shared.NewLocation("SGSIN")
	ta.AddException(tracking.NewTrackingExceptionEvent(tracking.ExceptionTypeDelay, loc, occurred(), "遅延", false))
	ta.AddException(tracking.NewTrackingExceptionEvent(tracking.ExceptionTypeDamage, loc, occurred(), "破損", false))
	// index=1 のみ解決 → index=0 が未解決で残るため依然 EXCEPTION。
	require.NoError(t, ta.ResolveException(1, "破損補償", occurred().Add(time.Hour)))
	assert.True(t, ta.HasActiveException())
	assert.Equal(t, shared.TransportStatusException, ta.CurrentStatus())
	assert.False(t, ta.Exceptions()[0].IsResolved())
	assert.True(t, ta.Exceptions()[1].IsResolved())
}

func TestTrackingExceptionEvent_Accessors(t *testing.T) {
	loc, _ := shared.NewLocation("SGSIN")
	ex := tracking.NewTrackingExceptionEvent(tracking.ExceptionTypeLost, loc, occurred(), "コンテナ紛失", true)
	assert.Equal(t, tracking.ExceptionTypeLost, ex.ExceptionType())
	assert.Equal(t, "SGSIN", ex.Location().UnLocode())
	assert.Equal(t, occurred(), ex.OccurredAt())
	assert.Equal(t, "コンテナ紛失", ex.Description())
	assert.True(t, ex.EscalationFlag())
	assert.False(t, ex.IsResolved())
}
