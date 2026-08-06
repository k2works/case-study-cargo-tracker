package domain_test

import (
	"testing"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	tracking "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestTrackingActivityEvent_Accessors(t *testing.T) {
	loc, _ := shared.NewLocation("SGSIN")
	ct := time.Date(2026, 7, 21, 8, 0, 0, 0, time.UTC)
	ev := tracking.NewTrackingActivityEvent("LOAD", loc, ct, "V001", shared.TransportStatusLoaded)
	assert.Equal(t, "LOAD", ev.EventType())
	assert.Equal(t, "SGSIN", ev.Location().UnLocode())
	assert.Equal(t, ct, ev.CompletionTime())
	assert.Equal(t, "V001", ev.VoyageNumber())
	assert.Equal(t, shared.TransportStatusLoaded, ev.TransportStatus())
}

func TestNewTrackingActivity_EmptyBookingId(t *testing.T) {
	tn, err := tracking.NewTrackingNumber("TRK-20260720-0001")
	require.NoError(t, err)
	_, err = tracking.NewTrackingActivity(tn, "  ")
	assert.ErrorIs(t, err, tracking.ErrEmptyTrackingBookingId)
}

func TestReconstructTrackingActivity(t *testing.T) {
	tn, _ := tracking.NewTrackingNumber("TRK-20260720-0002")
	loc, _ := shared.NewLocation("JPTYO")
	events := []tracking.TrackingActivityEvent{
		tracking.NewTrackingActivityEvent("RECEIVE", loc, time.Now(), "", shared.TransportStatusReceived),
	}
	ta := tracking.ReconstructTrackingActivity(tn, "CARGO-002", events, nil)
	assert.Equal(t, "TRK-20260720-0002", ta.TrackingNumber().Value())
	assert.Equal(t, "CARGO-002", ta.BookingId())
	assert.Equal(t, shared.TransportStatusReceived, ta.CurrentStatus())

	// 再構築は防御的コピー（元スライスの変更が集約に波及しない）。
	events[0] = tracking.NewTrackingActivityEvent("LOAD", loc, time.Now(), "V9", shared.TransportStatusLoaded)
	assert.Equal(t, shared.TransportStatusReceived, ta.CurrentStatus())
}

func TestTrackingActivity_CurrentLocation_NoEvents(t *testing.T) {
	ta := newTracking(t)
	assert.True(t, ta.CurrentLocation().IsZero())
}
