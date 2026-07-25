package application_test

import (
	"context"
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type stubBookingRepo struct {
	cargo    *domain.Cargo
	updated  *domain.Cargo
	notFound bool
}

func (s *stubBookingRepo) FindByBookingID(_ context.Context, _ domain.BookingId) (*domain.Cargo, error) {
	if s.notFound {
		return nil, application.ErrCargoNotFound
	}
	return s.cargo, nil
}

func (s *stubBookingRepo) UpdateStatus(_ context.Context, c *domain.Cargo) error {
	s.updated = c
	return nil
}

func newPreliminaryCargo(t *testing.T) *domain.Cargo {
	t.Helper()
	shipperCode, _ := shared.NewShipperCode("SHP-00000001")
	bookingID, _ := domain.NewBookingId("BKG-0001")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	spec, _ := domain.NewRouteSpecification(origin, dest, time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC))
	weight, _ := domain.NewWeight(500)
	c, err := domain.RegisterCargo(bookingID, shipperCode, spec, domain.CargoTypeGeneral, weight, domain.NewMoney(0, "JPY"), nil, nil)
	require.NoError(t, err)
	return c
}

func TestManageBookingService_Confirm(t *testing.T) {
	repo := &stubBookingRepo{cargo: newPreliminaryCargo(t)}
	svc := application.NewManageBookingService(repo)

	err := svc.Confirm(context.Background(), "BKG-0001")

	require.NoError(t, err)
	require.NotNil(t, repo.updated)
	assert.Equal(t, domain.BookingStatusConfirmed, repo.updated.Status())
}

func TestManageBookingService_Cancel(t *testing.T) {
	repo := &stubBookingRepo{cargo: newPreliminaryCargo(t)}
	svc := application.NewManageBookingService(repo)

	err := svc.Cancel(context.Background(), "BKG-0001")

	require.NoError(t, err)
	assert.Equal(t, domain.BookingStatusCancelled, repo.updated.Status())
}

func TestManageBookingService_SendBackToRouting(t *testing.T) {
	cargo := newPreliminaryCargo(t)
	require.NoError(t, cargo.Confirm())
	repo := &stubBookingRepo{cargo: cargo}
	svc := application.NewManageBookingService(repo)

	err := svc.SendBackToRouting(context.Background(), "BKG-0001")

	require.NoError(t, err)
	assert.Equal(t, domain.BookingStatusPreliminary, repo.updated.Status())
}

func TestManageBookingService_NotFound(t *testing.T) {
	repo := &stubBookingRepo{notFound: true}
	svc := application.NewManageBookingService(repo)

	err := svc.Confirm(context.Background(), "BKG-XXXX")

	require.ErrorIs(t, err, application.ErrCargoNotFound)
}

func TestManageBookingService_InvalidTransition(t *testing.T) {
	cargo := newPreliminaryCargo(t)
	require.NoError(t, cargo.Cancel()) // CANCELLED からは確定不可
	repo := &stubBookingRepo{cargo: cargo}
	svc := application.NewManageBookingService(repo)

	err := svc.Confirm(context.Background(), "BKG-0001")

	require.ErrorIs(t, err, domain.ErrInvalidStatusTransition)
	assert.Nil(t, repo.updated)
}

func TestManageBookingService_CancelInvalidTransition(t *testing.T) {
	cargo := newPreliminaryCargo(t)
	require.NoError(t, cargo.Cancel()) // CANCELLED からは再キャンセル不可
	repo := &stubBookingRepo{cargo: cargo}
	svc := application.NewManageBookingService(repo)

	err := svc.Cancel(context.Background(), "BKG-0001")

	require.ErrorIs(t, err, domain.ErrInvalidStatusTransition)
	assert.Nil(t, repo.updated, "許容外遷移では永続化を呼ばない")
}

func TestManageBookingService_SendBackInvalidTransition(t *testing.T) {
	repo := &stubBookingRepo{cargo: newPreliminaryCargo(t)} // PRELIMINARY からは差し戻し不可
	svc := application.NewManageBookingService(repo)

	err := svc.SendBackToRouting(context.Background(), "BKG-0001")

	require.ErrorIs(t, err, domain.ErrInvalidStatusTransition)
	assert.Nil(t, repo.updated, "許容外遷移では永続化を呼ばない")
}

func TestManageBookingService_InvalidBookingID(t *testing.T) {
	repo := &stubBookingRepo{cargo: newPreliminaryCargo(t)}
	svc := application.NewManageBookingService(repo)

	err := svc.Confirm(context.Background(), "")

	require.ErrorIs(t, err, domain.ErrEmptyBookingId)
	assert.Nil(t, repo.updated, "不正な予約番号では永続化を呼ばない")
}
