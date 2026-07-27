package application_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func time0() time.Time { return time.Time{} }

func confirmedCargoFixture(t *testing.T) *domain.Cargo {
	t.Helper()
	bid, _ := domain.NewBookingId("CARGO-001")
	sc, _ := shared.NewShipperCode("SHP-ABCDEF01")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	spec, _ := domain.NewRouteSpecification(origin, dest, time0())
	w, _ := domain.NewWeight(1000)
	c, err := domain.RegisterCargo(domain.CargoParams{
		BookingID: bid, ShipperCode: sc, RouteSpec: spec,
		CargoType: domain.CargoTypeGeneral, Weight: w, BookingAmount: domain.NewMoney(1000, "JPY"),
	})
	require.NoError(t, err)
	require.NoError(t, c.Confirm())
	return c
}

// スタブ。
type trackingRepoStub struct {
	cargo   *domain.Cargo
	updated *domain.Cargo
	err     error
}

func (r *trackingRepoStub) FindByBookingID(_ context.Context, _ domain.BookingId) (*domain.Cargo, error) {
	return r.cargo, r.err
}
func (r *trackingRepoStub) UpdateTracking(_ context.Context, c *domain.Cargo) error {
	r.updated = c
	return nil
}

type issuerStub struct{ n string }

func (i issuerStub) Next(_ context.Context) (string, error) { return i.n, nil }

type creatorStub struct{ number, booking string }

func (c *creatorStub) Create(_ context.Context, number, booking string) error {
	c.number, c.booking = number, booking
	return nil
}

type notifierStub struct{ called bool }

func (n *notifierStub) Notify(_ context.Context, _ shared.ShipperCode, _ string) error {
	n.called = true
	return nil
}

func TestAssignTrackingNumber_Success(t *testing.T) {
	cargo := confirmedCargoFixture(t)
	repo := &trackingRepoStub{cargo: cargo}
	creator := &creatorStub{}
	notifier := &notifierStub{}
	svc := application.NewAssignTrackingNumberService(repo, issuerStub{n: "TRK-20260720-0001"}, creator, notifier)

	tn, err := svc.Assign(context.Background(), "CARGO-001")
	require.NoError(t, err)
	assert.Equal(t, "TRK-20260720-0001", tn)
	assert.Equal(t, domain.BookingStatusTrackingIssued, repo.updated.Status())
	assert.Equal(t, "TRK-20260720-0001", creator.number)
	assert.Equal(t, "CARGO-001", creator.booking)
	assert.True(t, notifier.called)
}

func TestAssignTrackingNumber_NotConfirmed(t *testing.T) {
	bid, _ := domain.NewBookingId("CARGO-001")
	sc, _ := shared.NewShipperCode("SHP-ABCDEF01")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	spec, _ := domain.NewRouteSpecification(origin, dest, time0())
	w, _ := domain.NewWeight(1000)
	cargo, _ := domain.RegisterCargo(domain.CargoParams{
		BookingID: bid, ShipperCode: sc, RouteSpec: spec,
		CargoType: domain.CargoTypeGeneral, Weight: w, BookingAmount: domain.NewMoney(1000, "JPY"),
	})
	svc := application.NewAssignTrackingNumberService(&trackingRepoStub{cargo: cargo}, issuerStub{n: "TRK-20260720-0001"}, &creatorStub{}, &notifierStub{})
	_, err := svc.Assign(context.Background(), "CARGO-001")
	assert.ErrorIs(t, err, domain.ErrInvalidStatusTransition)
}

func TestAssignTrackingNumber_NotFound(t *testing.T) {
	svc := application.NewAssignTrackingNumberService(&trackingRepoStub{err: errors.New("nf")}, issuerStub{n: "TRK-20260720-0001"}, &creatorStub{}, &notifierStub{})
	_, err := svc.Assign(context.Background(), "NOPE")
	assert.Error(t, err)
}
