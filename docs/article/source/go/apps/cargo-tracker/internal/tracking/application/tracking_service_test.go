package application_test

import (
	"context"
	"errors"
	"testing"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	app "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// インメモリ追跡リポジトリ。
type memRepo struct {
	byNumber  map[string]*domain.TrackingActivity
	byBooking map[string]*domain.TrackingActivity
}

func newMemRepo() *memRepo {
	return &memRepo{byNumber: map[string]*domain.TrackingActivity{}, byBooking: map[string]*domain.TrackingActivity{}}
}
func (m *memRepo) Save(_ context.Context, a *domain.TrackingActivity) error {
	m.byNumber[a.TrackingNumber().Value()] = a
	m.byBooking[a.BookingId()] = a
	return nil
}
func (m *memRepo) FindByTrackingNumber(_ context.Context, n string) (*domain.TrackingActivity, error) {
	a, ok := m.byNumber[n]
	if !ok {
		return nil, app.ErrTrackingNotFound
	}
	return a, nil
}
func (m *memRepo) FindByBookingID(_ context.Context, id string) (*domain.TrackingActivity, error) {
	a, ok := m.byBooking[id]
	if !ok {
		return nil, app.ErrTrackingNotFound
	}
	return a, nil
}

func TestCreateTrackingActivity(t *testing.T) {
	repo := newMemRepo()
	svc := app.NewTrackingCommandService(repo)
	err := svc.CreateTracking(context.Background(), "TRK-20260720-0001", "CARGO-001")
	require.NoError(t, err)

	got, err := repo.FindByTrackingNumber(context.Background(), "TRK-20260720-0001")
	require.NoError(t, err)
	assert.Equal(t, shared.TransportStatusNotReceived, got.CurrentStatus())
}

func TestRecordHandlingEvent_UpdatesStatus(t *testing.T) {
	repo := newMemRepo()
	svc := app.NewTrackingCommandService(repo)
	require.NoError(t, svc.CreateTracking(context.Background(), "TRK-20260720-0001", "CARGO-001"))

	err := svc.RecordHandlingEvent(context.Background(), app.RecordHandlingEventCommand{
		BookingID:        "CARGO-001",
		HandlingType:     "RECEIVE",
		LocationUnLocode: "JPTYO",
		VoyageNumber:     "",
		TransportStatus:  "RECEIVED",
		CompletionTime:   time.Now(),
	})
	require.NoError(t, err)

	got, _ := repo.FindByBookingID(context.Background(), "CARGO-001")
	assert.Equal(t, shared.TransportStatusReceived, got.CurrentStatus())
	assert.Equal(t, "JPTYO", got.CurrentLocation().UnLocode())
}

func TestRecordHandlingEvent_CustomsDoesNotRegressStatus(t *testing.T) {
	repo := newMemRepo()
	svc := app.NewTrackingCommandService(repo)
	require.NoError(t, svc.CreateTracking(context.Background(), "TRK-20260720-0001", "CARGO-001"))
	require.NoError(t, svc.RecordHandlingEvent(context.Background(), app.RecordHandlingEventCommand{
		BookingID: "CARGO-001", HandlingType: "LOAD", LocationUnLocode: "JPTYO",
		TransportStatus: "LOADED", CompletionTime: time.Now(),
	}))
	// CUSTOMS は UNKNOWN を運ぶが、現状態（LOADED）を退行させない。
	require.NoError(t, svc.RecordHandlingEvent(context.Background(), app.RecordHandlingEventCommand{
		BookingID: "CARGO-001", HandlingType: "CUSTOMS", LocationUnLocode: "JPTYO",
		TransportStatus: "UNKNOWN", CompletionTime: time.Now(),
	}))
	got, _ := repo.FindByBookingID(context.Background(), "CARGO-001")
	assert.Equal(t, shared.TransportStatusLoaded, got.CurrentStatus())
	assert.Len(t, got.Events(), 2) // 履歴には CUSTOMS も残る
}

func TestRecordHandlingEvent_MisroutedReflectsException(t *testing.T) {
	repo := newMemRepo()
	svc := app.NewTrackingCommandService(repo)
	require.NoError(t, svc.CreateTracking(context.Background(), "TRK-20260720-0001", "CARGO-001"))
	require.NoError(t, svc.RecordHandlingEvent(context.Background(), app.RecordHandlingEventCommand{
		BookingID: "CARGO-001", HandlingType: "LOAD", LocationUnLocode: "USLAX",
		TransportStatus: "LOADED", Misrouted: true, CompletionTime: time.Now(),
	}))
	got, _ := repo.FindByBookingID(context.Background(), "CARGO-001")
	// 荷主・荷受人の照会にも例外として反映される。
	assert.Equal(t, shared.TransportStatusException, got.CurrentStatus())
}

func TestRecordHandlingEvent_UnknownBooking(t *testing.T) {
	repo := newMemRepo()
	svc := app.NewTrackingCommandService(repo)
	err := svc.RecordHandlingEvent(context.Background(), app.RecordHandlingEventCommand{
		BookingID:        "NOPE",
		HandlingType:     "RECEIVE",
		TransportStatus:  "RECEIVED",
		LocationUnLocode: "JPTYO",
		CompletionTime:   time.Now(),
	})
	assert.ErrorIs(t, err, app.ErrTrackingNotFound)
}

func TestTrackingQuery_FindByTrackingNumber(t *testing.T) {
	repo := newMemRepo()
	cmd := app.NewTrackingCommandService(repo)
	require.NoError(t, cmd.CreateTracking(context.Background(), "TRK-20260720-0001", "CARGO-001"))
	require.NoError(t, cmd.RecordHandlingEvent(context.Background(), app.RecordHandlingEventCommand{
		BookingID: "CARGO-001", HandlingType: "RECEIVE", LocationUnLocode: "JPTYO",
		TransportStatus: "RECEIVED", CompletionTime: time.Date(2026, 7, 20, 10, 0, 0, 0, time.UTC),
	}))

	q := app.NewTrackingQueryService(repo)
	view, err := q.FindByTrackingNumber(context.Background(), "TRK-20260720-0001")
	require.NoError(t, err)
	assert.Equal(t, "TRK-20260720-0001", view.TrackingNumber)
	assert.Equal(t, "CARGO-001", view.BookingID)
	assert.Equal(t, "受領済", view.StatusJa)
	assert.Equal(t, "JPTYO", view.CurrentLocation)
	require.Len(t, view.Events, 1)
	assert.Equal(t, "受領", view.Events[0].TypeJa)
}

func TestTrackingQuery_NotFound(t *testing.T) {
	q := app.NewTrackingQueryService(newMemRepo())
	_, err := q.FindByTrackingNumber(context.Background(), "TRK-20260720-9999")
	assert.ErrorIs(t, err, app.ErrTrackingNotFound)
}

func TestCreateTracking_InvalidNumber(t *testing.T) {
	svc := app.NewTrackingCommandService(newMemRepo())
	err := svc.CreateTracking(context.Background(), "BADNUMBER", "CARGO-001")
	assert.Error(t, err)
}

func TestRecordHandlingEvent_RepoSaveError(t *testing.T) {
	svc := app.NewTrackingCommandService(errRepo{})
	err := svc.CreateTracking(context.Background(), "TRK-20260720-0001", "CARGO-001")
	assert.Error(t, err)
}

type errRepo struct{}

func (errRepo) Save(context.Context, *domain.TrackingActivity) error { return errors.New("db") }
func (errRepo) FindByTrackingNumber(context.Context, string) (*domain.TrackingActivity, error) {
	return nil, errors.New("db")
}
func (errRepo) FindByBookingID(context.Context, string) (*domain.TrackingActivity, error) {
	return nil, errors.New("db")
}
