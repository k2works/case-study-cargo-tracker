package domain_test

import (
	"testing"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	tracking "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewTrackingNumber_Format(t *testing.T) {
	tn, err := tracking.NewTrackingNumber("TRK-20260720-0001")
	require.NoError(t, err)
	assert.Equal(t, "TRK-20260720-0001", tn.Value())

	for _, bad := range []string{"", "TRK-2026-0001", "20260720-0001", "TRK-20260720-ABCD", "trk-20260720-0001"} {
		_, err := tracking.NewTrackingNumber(bad)
		assert.ErrorIs(t, err, tracking.ErrInvalidTrackingNumber, "should reject %q", bad)
	}
}

func TestFormatTrackingNumber(t *testing.T) {
	day := time.Date(2026, 7, 20, 15, 0, 0, 0, time.UTC)
	assert.Equal(t, "TRK-20260720-0007", tracking.FormatTrackingNumber(day, 7))
	assert.Equal(t, "TRK-20260720-0042", tracking.FormatTrackingNumber(day, 42))
}

func newTracking(t *testing.T) tracking.TrackingActivity {
	t.Helper()
	tn, err := tracking.NewTrackingNumber("TRK-20260720-0001")
	require.NoError(t, err)
	ta, err := tracking.NewTrackingActivity(tn, "CARGO-001")
	require.NoError(t, err)
	return ta
}

func TestNewTrackingActivity_InitialStatus(t *testing.T) {
	ta := newTracking(t)
	assert.Equal(t, "TRK-20260720-0001", ta.TrackingNumber().Value())
	assert.Equal(t, "CARGO-001", ta.BookingId())
	// 追跡番号発行直後は受領待ち。
	assert.Equal(t, shared.TransportStatusNotReceived, ta.CurrentStatus())
	assert.Empty(t, ta.Events())
}

func TestTrackingActivity_AddEvent_UpdatesStatus(t *testing.T) {
	ta := newTracking(t)
	loc, err := shared.NewLocation("JPTYO")
	require.NoError(t, err)

	ev := tracking.NewTrackingActivityEvent(
		"RECEIVE", loc, time.Date(2026, 7, 20, 10, 0, 0, 0, time.UTC), "",
		shared.TransportStatusReceived,
	)
	ta.AddEvent(ev)

	assert.Len(t, ta.Events(), 1)
	assert.Equal(t, shared.TransportStatusReceived, ta.CurrentStatus())
	assert.Equal(t, "JPTYO", ta.CurrentLocation().UnLocode())
}

func TestTrackingActivity_CurrentStatus_LatestEvent(t *testing.T) {
	ta := newTracking(t)
	loc, _ := shared.NewLocation("JPTYO")
	ta.AddEvent(tracking.NewTrackingActivityEvent("RECEIVE", loc, time.Now(), "", shared.TransportStatusReceived))
	ta.AddEvent(tracking.NewTrackingActivityEvent("LOAD", loc, time.Now(), "V001", shared.TransportStatusLoaded))
	assert.Equal(t, shared.TransportStatusLoaded, ta.CurrentStatus())
	assert.Len(t, ta.Events(), 2)
}
