package application_test

import (
	"context"
	"testing"
	"time"

	app "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func mustLoc(code string) shared.Location {
	l, _ := shared.NewLocation(code)
	return l
}

// スタブ実装。
type repoStub struct {
	saved []domain.HandlingActivity
	err   error
}

func (r *repoStub) Save(_ context.Context, a domain.HandlingActivity) error {
	if r.err != nil {
		return r.err
	}
	r.saved = append(r.saved, a)
	return nil
}
func (r *repoStub) ListByBookingID(_ context.Context, _ string) ([]domain.HandlingActivity, error) {
	return r.saved, nil
}

type snapshotStub struct {
	snap domain.CargoSnapshot
	err  error
}

func (s snapshotStub) FetchSnapshot(_ context.Context, _ string) (domain.CargoSnapshot, error) {
	return s.snap, s.err
}

type pubStub struct {
	events []app.HandlingActivityRegisteredEvent
}

func (p *pubStub) Publish(_ context.Context, e app.HandlingActivityRegisteredEvent) error {
	p.events = append(p.events, e)
	return nil
}

func fixtureSnap() domain.CargoSnapshot {
	legs := []domain.LegSnapshot{
		domain.NewLegSnapshot(mustLoc("JPTYO"), mustLoc("SGSIN"), "V001"),
		domain.NewLegSnapshot(mustLoc("SGSIN"), mustLoc("DEHAM"), "V002"),
	}
	return domain.NewCargoSnapshot("CARGO-001", mustLoc("JPTYO"), mustLoc("DEHAM"), legs, shared.RoutingStatusRouted)
}

func TestRegisterHandlingActivity_Success(t *testing.T) {
	repo := &repoStub{}
	pub := &pubStub{}
	svc := app.NewRegisterHandlingActivityService(repo, snapshotStub{snap: fixtureSnap()}, pub)

	result, err := svc.Register(context.Background(), app.RegisterHandlingActivityCommand{
		BookingID:        "CARGO-001",
		HandlingType:     "RECEIVE",
		LocationUnLocode: "JPTYO",
		CompletionTime:   time.Date(2026, 7, 20, 10, 0, 0, 0, time.UTC),
		OperatorName:     "作業員A",
	})

	require.NoError(t, err)
	assert.Len(t, repo.saved, 1)
	assert.False(t, result.Warning)
	assert.False(t, result.Misrouted)
	// 状態同期イベントが発行される。
	require.Len(t, pub.events, 1)
	assert.Equal(t, "RECEIVED", pub.events[0].TransportStatus)
	assert.Equal(t, "CARGO-001", pub.events[0].BookingID)
}

func TestRegisterHandlingActivity_LoadWrongPort_Misrouted(t *testing.T) {
	repo := &repoStub{}
	pub := &pubStub{}
	svc := app.NewRegisterHandlingActivityService(repo, snapshotStub{snap: fixtureSnap()}, pub)

	result, err := svc.Register(context.Background(), app.RegisterHandlingActivityCommand{
		BookingID:        "CARGO-001",
		HandlingType:     "LOAD",
		LocationUnLocode: "USLAX", // 旅程外
		VoyageNumber:     "V999",
		CompletionTime:   time.Now(),
		OperatorName:     "作業員A",
	})

	require.NoError(t, err)
	assert.True(t, result.Misrouted)
	require.Len(t, pub.events, 1)
	assert.True(t, pub.events[0].Misrouted)
	assert.Equal(t, "LOADED", pub.events[0].TransportStatus)
}

func TestRegisterHandlingActivity_UnknownType(t *testing.T) {
	svc := app.NewRegisterHandlingActivityService(&repoStub{}, snapshotStub{snap: fixtureSnap()}, &pubStub{})
	_, err := svc.Register(context.Background(), app.RegisterHandlingActivityCommand{
		BookingID:        "CARGO-001",
		HandlingType:     "CUSTOMS_CLEARANCE",
		LocationUnLocode: "JPTYO",
		CompletionTime:   time.Now(),
	})
	assert.ErrorIs(t, err, domain.ErrUnknownHandlingType)
}

func TestRegisterHandlingActivity_ClaimRequiresConfirmation(t *testing.T) {
	svc := app.NewRegisterHandlingActivityService(&repoStub{}, snapshotStub{snap: fixtureSnap()}, &pubStub{})
	_, err := svc.Register(context.Background(), app.RegisterHandlingActivityCommand{
		BookingID:        "CARGO-001",
		HandlingType:     "CLAIM",
		LocationUnLocode: "DEHAM",
		CompletionTime:   time.Now(),
	})
	assert.ErrorIs(t, err, domain.ErrConsigneeConfirmationRequired)
}
