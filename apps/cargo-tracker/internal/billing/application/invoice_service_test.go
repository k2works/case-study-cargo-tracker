package application_test

import (
	"context"
	"testing"
	"time"

	app "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type memInvoiceRepo struct {
	byNumber  map[string]*domain.Invoice
	byBooking map[string]bool
}

func newRepo() *memInvoiceRepo {
	return &memInvoiceRepo{byNumber: map[string]*domain.Invoice{}, byBooking: map[string]bool{}}
}
func (m *memInvoiceRepo) Save(_ context.Context, inv *domain.Invoice) error {
	m.byNumber[inv.InvoiceId().Value()] = inv
	m.byBooking[inv.BookingId().Value()] = true
	return nil
}
func (m *memInvoiceRepo) FindByInvoiceNumber(_ context.Context, n string) (*domain.Invoice, error) {
	inv, ok := m.byNumber[n]
	if !ok {
		return nil, app.ErrInvoiceNotFound
	}
	return inv, nil
}
func (m *memInvoiceRepo) ExistsByBookingID(_ context.Context, id string) (bool, error) {
	return m.byBooking[id], nil
}
func (m *memInvoiceRepo) List(_ context.Context) ([]*domain.Invoice, error) {
	out := make([]*domain.Invoice, 0, len(m.byNumber))
	for _, v := range m.byNumber {
		out = append(out, v)
	}
	return out, nil
}

type cargoStub struct{ snap app.CargoBillingSnapshot }

func (c cargoStub) FetchBillingSnapshot(_ context.Context, _ string) (app.CargoBillingSnapshot, error) {
	return c.snap, nil
}

type shipperStub struct{ contract app.ShipperContract }

func (s shipperStub) FetchContract(_ context.Context, _ string) (app.ShipperContract, error) {
	return s.contract, nil
}

type issuerStub struct{ n string }

func (i issuerStub) Next(_ context.Context, _ time.Time) (string, error) { return i.n, nil }

type notifierStub struct{ shipper, accountant int }

func (n *notifierStub) NotifyShipper(_ context.Context, _, _ string) error { n.shipper++; return nil }
func (n *notifierStub) NotifyAccountant(_ context.Context, _, _ string) error {
	n.accountant++
	return nil
}

type settlerStub struct{ settled string }

func (s *settlerStub) MarkSettled(_ context.Context, bookingID string) error {
	s.settled = bookingID
	return nil
}

func clockAt(t time.Time) shared.Clock { return shared.FixedClock{Fixed: t} }

func claimedSnap() app.CargoBillingSnapshot {
	return app.CargoBillingSnapshot{
		BookingID: "CARGO-001", ShipperCode: "SHP-ABCDEF01", WeightKg: 1000,
		CargoType: "GENERAL", TransportStatus: "CLAIMED", DistanceFactor: 100,
	}
}

func TestGenerate_CorporateDiscount(t *testing.T) {
	repo := newRepo()
	notifier := &notifierStub{}
	svc := app.NewGenerateInvoiceService(repo, cargoStub{claimedSnap()},
		shipperStub{app.ShipperContract{ShipperType: "CORPORATE", DiscountRate: 0.10}},
		issuerStub{"INV-20260720-0001"}, notifier, clockAt(time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC)))

	number, err := svc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})
	require.NoError(t, err)
	assert.Equal(t, "INV-20260720-0001", number)
	// 基本 100000 → 割引 10% → 90000 → 税 9000 → 99000
	inv, _ := repo.FindByInvoiceNumber(context.Background(), number)
	assert.Equal(t, int64(99000), inv.FinalAmount().Amount())
	assert.Equal(t, 1, notifier.shipper)
}

func TestGenerate_IndividualNoDiscount(t *testing.T) {
	repo := newRepo()
	svc := app.NewGenerateInvoiceService(repo, cargoStub{claimedSnap()},
		shipperStub{app.ShipperContract{ShipperType: "INDIVIDUAL", DiscountRate: 0.0}},
		issuerStub{"INV-20260720-0001"}, &notifierStub{}, clockAt(time.Now()))
	number, err := svc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})
	require.NoError(t, err)
	inv, _ := repo.FindByInvoiceNumber(context.Background(), number)
	assert.Equal(t, int64(110000), inv.FinalAmount().Amount()) // 割引なし 100000 + 税 10000
}

func TestGenerate_NotClaimed(t *testing.T) {
	snap := claimedSnap()
	snap.TransportStatus = "LOADED"
	svc := app.NewGenerateInvoiceService(newRepo(), cargoStub{snap},
		shipperStub{}, issuerStub{"INV-20260720-0001"}, &notifierStub{}, clockAt(time.Now()))
	_, err := svc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})
	assert.ErrorIs(t, err, app.ErrCargoNotClaimed)
}

func TestGenerate_AlreadyInvoiced(t *testing.T) {
	repo := newRepo()
	repo.byBooking["CARGO-001"] = true
	svc := app.NewGenerateInvoiceService(repo, cargoStub{claimedSnap()},
		shipperStub{}, issuerStub{"INV-20260720-0001"}, &notifierStub{}, clockAt(time.Now()))
	_, err := svc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})
	assert.ErrorIs(t, err, app.ErrAlreadyInvoiced)
}

func TestConfirmPayment_SettlesBooking(t *testing.T) {
	repo := newRepo()
	genSvc := app.NewGenerateInvoiceService(repo, cargoStub{claimedSnap()},
		shipperStub{app.ShipperContract{ShipperType: "CORPORATE", DiscountRate: 0.10}},
		issuerStub{"INV-20260720-0001"}, &notifierStub{}, clockAt(time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC)))
	number, _ := genSvc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})

	settler := &settlerStub{}
	confirmSvc := app.NewConfirmPaymentService(repo, settler, &notifierStub{}, clockAt(time.Date(2026, 8, 1, 0, 0, 0, 0, time.UTC)))
	require.NoError(t, confirmSvc.Confirm(context.Background(), number))

	inv, _ := repo.FindByInvoiceNumber(context.Background(), number)
	assert.Equal(t, domain.PaymentStatusConfirmed, inv.PaymentStatus())
	assert.Equal(t, "CARGO-001", settler.settled) // 予約が SETTLED に
}

func TestGenerate_InvalidCargoType(t *testing.T) {
	snap := claimedSnap()
	snap.CargoType = "BOGUS"
	svc := app.NewGenerateInvoiceService(newRepo(), cargoStub{snap},
		shipperStub{}, issuerStub{"INV-20260720-0001"}, &notifierStub{}, clockAt(time.Now()))
	_, err := svc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})
	assert.Error(t, err)
}

func TestGenerate_InvalidDiscountRate(t *testing.T) {
	svc := app.NewGenerateInvoiceService(newRepo(), cargoStub{claimedSnap()},
		shipperStub{app.ShipperContract{ShipperType: "CORPORATE", DiscountRate: 0.5}}, // 範囲外
		issuerStub{"INV-20260720-0001"}, &notifierStub{}, clockAt(time.Now()))
	_, err := svc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})
	assert.ErrorIs(t, err, domain.ErrDiscountRateOutOfRange)
}

func TestConfirmPayment_NotFound(t *testing.T) {
	svc := app.NewConfirmPaymentService(newRepo(), &settlerStub{}, &notifierStub{}, clockAt(time.Now()))
	err := svc.Confirm(context.Background(), "INV-20260720-9999")
	assert.ErrorIs(t, err, app.ErrInvoiceNotFound)
}

func TestMarkOverdue_WithinDue_NoNotify(t *testing.T) {
	repo := newRepo()
	genSvc := app.NewGenerateInvoiceService(repo, cargoStub{claimedSnap()},
		shipperStub{app.ShipperContract{ShipperType: "INDIVIDUAL"}},
		issuerStub{"INV-20260720-0001"}, &notifierStub{}, clockAt(time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC)))
	number, _ := genSvc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})
	notifier := &notifierStub{}
	overdueSvc := app.NewMarkOverdueService(repo, notifier, clockAt(time.Date(2026, 8, 1, 0, 0, 0, 0, time.UTC)))
	require.NoError(t, overdueSvc.MarkOverdue(context.Background(), number))
	assert.Equal(t, 0, notifier.accountant) // 期限内は通知しない

	list, err := repo.List(context.Background())
	require.NoError(t, err)
	assert.Len(t, list, 1)
}

type errCargo struct{}

func (errCargo) FetchBillingSnapshot(_ context.Context, _ string) (app.CargoBillingSnapshot, error) {
	return app.CargoBillingSnapshot{}, errAny
}

type errShipper struct{}

func (errShipper) FetchContract(_ context.Context, _ string) (app.ShipperContract, error) {
	return app.ShipperContract{}, errAny
}

var errAny = context.DeadlineExceeded

func TestGenerate_CargoFetchError(t *testing.T) {
	svc := app.NewGenerateInvoiceService(newRepo(), errCargo{}, shipperStub{},
		issuerStub{"INV-20260720-0001"}, &notifierStub{}, clockAt(time.Now()))
	_, err := svc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})
	assert.Error(t, err)
}

func TestGenerate_ShipperFetchError(t *testing.T) {
	svc := app.NewGenerateInvoiceService(newRepo(), cargoStub{claimedSnap()}, errShipper{},
		issuerStub{"INV-20260720-0001"}, &notifierStub{}, clockAt(time.Now()))
	_, err := svc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})
	assert.Error(t, err)
}

func TestMarkOverdue_NotFound(t *testing.T) {
	svc := app.NewMarkOverdueService(newRepo(), &notifierStub{}, clockAt(time.Now()))
	err := svc.MarkOverdue(context.Background(), "INV-20260720-9999")
	assert.ErrorIs(t, err, app.ErrInvoiceNotFound)
}

func TestConfirmPayment_AlreadyConfirmed(t *testing.T) {
	repo := newRepo()
	genSvc := app.NewGenerateInvoiceService(repo, cargoStub{claimedSnap()},
		shipperStub{app.ShipperContract{ShipperType: "INDIVIDUAL"}},
		issuerStub{"INV-20260720-0001"}, &notifierStub{}, clockAt(time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC)))
	number, _ := genSvc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})
	confirmSvc := app.NewConfirmPaymentService(repo, &settlerStub{}, &notifierStub{}, clockAt(time.Date(2026, 8, 1, 0, 0, 0, 0, time.UTC)))
	require.NoError(t, confirmSvc.Confirm(context.Background(), number))
	// 再確定はドメインエラー。
	assert.ErrorIs(t, confirmSvc.Confirm(context.Background(), number), domain.ErrInvalidPaymentTransition)
}

func TestMarkOverdue_NotifiesAccountant(t *testing.T) {
	repo := newRepo()
	genSvc := app.NewGenerateInvoiceService(repo, cargoStub{claimedSnap()},
		shipperStub{app.ShipperContract{ShipperType: "INDIVIDUAL"}},
		issuerStub{"INV-20260720-0001"}, &notifierStub{}, clockAt(time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC)))
	number, _ := genSvc.Generate(context.Background(), app.GenerateInvoiceCommand{BookingID: "CARGO-001"})

	notifier := &notifierStub{}
	// 期限（8/19）超過の 8/20 に判定。
	overdueSvc := app.NewMarkOverdueService(repo, notifier, clockAt(time.Date(2026, 8, 20, 0, 0, 0, 0, time.UTC)))
	require.NoError(t, overdueSvc.MarkOverdue(context.Background(), number))
	inv, _ := repo.FindByInvoiceNumber(context.Background(), number)
	assert.Equal(t, domain.PaymentStatusOverdue, inv.PaymentStatus())
	assert.Equal(t, 1, notifier.accountant)
}
