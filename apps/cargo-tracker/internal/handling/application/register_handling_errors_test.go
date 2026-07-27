package application_test

import (
	"context"
	"errors"
	"testing"
	"time"

	app "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/application"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestRegisterHandlingActivity_InvalidLocation(t *testing.T) {
	svc := app.NewRegisterHandlingActivityService(&repoStub{}, snapshotStub{snap: fixtureSnap()}, &pubStub{})
	_, err := svc.Register(context.Background(), app.RegisterHandlingActivityCommand{
		BookingID:        "CARGO-001",
		HandlingType:     "RECEIVE",
		LocationUnLocode: "bad", // 不正な UN/LOCODE
		CompletionTime:   time.Now(),
	})
	assert.Error(t, err)
}

func TestRegisterHandlingActivity_SnapshotError(t *testing.T) {
	svc := app.NewRegisterHandlingActivityService(&repoStub{}, snapshotStub{err: errors.New("not found")}, &pubStub{})
	_, err := svc.Register(context.Background(), app.RegisterHandlingActivityCommand{
		BookingID:        "CARGO-001",
		HandlingType:     "RECEIVE",
		LocationUnLocode: "JPTYO",
		CompletionTime:   time.Now(),
	})
	assert.Error(t, err)
}

func TestRegisterHandlingActivity_RepoError(t *testing.T) {
	svc := app.NewRegisterHandlingActivityService(&repoStub{err: errors.New("db")}, snapshotStub{snap: fixtureSnap()}, &pubStub{})
	_, err := svc.Register(context.Background(), app.RegisterHandlingActivityCommand{
		BookingID:        "CARGO-001",
		HandlingType:     "RECEIVE",
		LocationUnLocode: "JPTYO",
		CompletionTime:   time.Now(),
	})
	assert.Error(t, err)
}

func TestRegisterHandlingActivity_ListByBookingID(t *testing.T) {
	repo := &repoStub{}
	svc := app.NewRegisterHandlingActivityService(repo, snapshotStub{snap: fixtureSnap()}, &pubStub{})
	_, err := svc.Register(context.Background(), app.RegisterHandlingActivityCommand{
		BookingID:        "CARGO-001",
		HandlingType:     "RECEIVE",
		LocationUnLocode: "JPTYO",
		CompletionTime:   time.Now(),
	})
	require.NoError(t, err)

	list, err := svc.ListByBookingID(context.Background(), "CARGO-001")
	require.NoError(t, err)
	assert.Len(t, list, 1)
}
