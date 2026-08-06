//go:build integration

package infrastructure_test

import (
	"context"
	"path/filepath"
	"sort"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	billingapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/infrastructure"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
)

func setupPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	ctx := context.Background()
	dir, err := filepath.Abs("../../../db/migrations")
	require.NoError(t, err)
	files, err := filepath.Glob(filepath.Join(dir, "*.up.sql"))
	require.NoError(t, err)
	sort.Strings(files)
	container, err := postgres.Run(ctx, "postgres:16-alpine",
		postgres.WithDatabase("cargo_tracker"), postgres.WithUsername("cargo"), postgres.WithPassword("cargo"),
		postgres.WithInitScripts(files...), postgres.BasicWaitStrategies())
	require.NoError(t, err)
	t.Cleanup(func() { _ = container.Terminate(ctx) })
	dsn, err := container.ConnectionString(ctx, "sslmode=disable")
	require.NoError(t, err)
	pool, err := pgxpool.New(ctx, dsn)
	require.NoError(t, err)
	t.Cleanup(pool.Close)
	return pool
}

func fixtureInvoice(t *testing.T, number string) *domain.Invoice {
	t.Helper()
	id, err := domain.NewInvoiceId(number)
	require.NoError(t, err)
	dr, _ := domain.NewDiscountRate(0.10)
	inv, err := domain.GenerateInvoice(domain.GenerateInvoiceParams{
		InvoiceId: id, BookingId: domain.NewBillingBookingId("CARGO-001"),
		ShipperId:  domain.NewBillingShipperId("SHP-ABCDEF01", "CORPORATE"),
		BaseAmount: domain.NewMoney(100000, "JPY"), DiscountRate: dr, TaxRate: 0.10,
		IssuedAt: time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC), DueDays: 30,
	})
	require.NoError(t, err)
	return inv
}

func TestInvoiceRepository_SaveConfirmAndFind(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewInvoiceRepository(pool)
	ctx := context.Background()

	require.NoError(t, repo.Save(ctx, fixtureInvoice(t, "INV-20260720-0001")))

	got, err := repo.FindByInvoiceNumber(ctx, "INV-20260720-0001")
	require.NoError(t, err)
	assert.Equal(t, int64(99000), got.FinalAmount().Amount()) // 100000 - 10% + 税10%
	assert.InDelta(t, 0.10, got.DiscountRate().Value(), 1e-6)
	assert.Equal(t, domain.PaymentStatusPending, got.PaymentStatus())

	// 二重請求防止（同一 booking_id）。
	exists, err := repo.ExistsByBookingID(ctx, "CARGO-001")
	require.NoError(t, err)
	assert.True(t, exists)

	// 入金確認 → 状態更新（ON CONFLICT UPDATE）。
	require.NoError(t, got.ConfirmPayment(time.Date(2026, 8, 1, 0, 0, 0, 0, time.UTC)))
	require.NoError(t, repo.Save(ctx, got))
	confirmed, err := repo.FindByInvoiceNumber(ctx, "INV-20260720-0001")
	require.NoError(t, err)
	assert.Equal(t, domain.PaymentStatusConfirmed, confirmed.PaymentStatus())
	assert.False(t, confirmed.PaidAt().IsZero())
}

func TestInvoiceRepository_NextInvoiceNumber(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewInvoiceRepository(pool)
	ctx := context.Background()
	day := time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC)
	first, err := repo.NextInvoiceNumber(ctx, day)
	require.NoError(t, err)
	assert.Equal(t, "INV-20260720-0001", first)
	second, err := repo.NextInvoiceNumber(ctx, day)
	require.NoError(t, err)
	assert.Equal(t, "INV-20260720-0002", second)
}

func TestInvoiceRepository_NotFound(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewInvoiceRepository(pool)
	_, err := repo.FindByInvoiceNumber(context.Background(), "INV-20260720-9999")
	assert.ErrorIs(t, err, billingapp.ErrInvoiceNotFound)
}

func TestInvoiceRepository_List(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewInvoiceRepository(pool)
	ctx := context.Background()
	require.NoError(t, repo.Save(ctx, fixtureInvoice(t, "INV-20260720-0001")))

	list, err := repo.List(ctx)
	require.NoError(t, err)
	require.Len(t, list, 1)
	assert.Equal(t, "INV-20260720-0001", list[0].InvoiceId().Value())
	assert.Equal(t, int64(99000), list[0].FinalAmount().Amount())
	assert.True(t, list[0].ShipperId().IsCorporate())
}

func TestInvoiceRepository_ExistsFalse(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewInvoiceRepository(pool)
	exists, err := repo.ExistsByBookingID(context.Background(), "CARGO-NONE")
	require.NoError(t, err)
	assert.False(t, exists)
}

func TestShipperContractAdapter_FetchContract(t *testing.T) {
	pool := setupPool(t)
	// seed shipper (000004 seed demo data で法人荷主が入る想定。無ければ INSERT)。
	ctx := context.Background()
	_, err := pool.Exec(ctx, `INSERT INTO shipper (shipper_code, shipper_type, name, email, discount_rate)
		VALUES ('SHP-TEST01','CORPORATE','テスト商事','test@example.com',0.15)
		ON CONFLICT (shipper_code) DO NOTHING`)
	require.NoError(t, err)
	adapter := infrastructure.NewShipperContractAdapter(pool)
	c, err := adapter.FetchContract(ctx, "SHP-TEST01")
	require.NoError(t, err)
	assert.Equal(t, "CORPORATE", c.ShipperType)
	assert.InDelta(t, 0.15, c.DiscountRate, 1e-6)
}
