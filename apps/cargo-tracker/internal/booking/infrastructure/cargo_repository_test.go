//go:build integration

package infrastructure_test

import (
	"context"
	"path/filepath"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/infrastructure"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
)

func setupPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	ctx := context.Background()
	migrations, err := filepath.Abs("../../../db/migrations")
	require.NoError(t, err)

	container, err := postgres.Run(ctx,
		"postgres:16-alpine",
		postgres.WithDatabase("cargo_tracker"),
		postgres.WithUsername("cargo"),
		postgres.WithPassword("cargo"),
		postgres.WithInitScripts(
			filepath.Join(migrations, "000001_create_shipper.up.sql"),
			filepath.Join(migrations, "000002_create_cargo.up.sql"),
		),
		postgres.BasicWaitStrategies(),
	)
	require.NoError(t, err)
	t.Cleanup(func() { _ = container.Terminate(ctx) })

	dsn, err := container.ConnectionString(ctx, "sslmode=disable")
	require.NoError(t, err)
	pool, err := pgxpool.New(ctx, dsn)
	require.NoError(t, err)
	t.Cleanup(pool.Close)
	return pool
}

func TestCargoRepository_Save(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewCargoRepository(pool)
	ctx := context.Background()

	shipperCode, _ := shared.NewShipperCode("SHP-ABCDEF12")
	bookingID, _ := domain.NewBookingId("BKG-00000001")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	spec, _ := domain.NewRouteSpecification(origin, dest, time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC))
	weight, _ := domain.NewWeight(1200.5)

	cargo, err := domain.RegisterCargo(bookingID, shipperCode, spec, domain.CargoTypeGeneral, weight, domain.NewMoney(0, "JPY"), nil, nil)
	require.NoError(t, err)

	t.Run("貨物予約を保存できる", func(t *testing.T) {
		err := repo.Save(ctx, cargo)
		require.NoError(t, err)
	})
}

func TestShipperExistenceAdapter_Exists(t *testing.T) {
	pool := setupPool(t)
	ctx := context.Background()

	// shipper を直接投入
	_, err := pool.Exec(ctx, `INSERT INTO shipper (shipper_code, shipper_type, name, email) VALUES ($1,$2,$3,$4)`,
		"SHP-ABCDEF12", "INDIVIDUAL", "山田太郎", "taro@example.com")
	require.NoError(t, err)

	adapter := infrastructure.NewShipperExistenceAdapter(pool)

	t.Run("登録済み荷主コードは存在する", func(t *testing.T) {
		id, _ := shared.NewShipperCode("SHP-ABCDEF12")
		exists, err := adapter.Exists(ctx, id)
		require.NoError(t, err)
		assert.True(t, exists)
	})

	t.Run("未登録荷主コードは存在しない", func(t *testing.T) {
		id, _ := shared.NewShipperCode("SHP-UNKNOWN0")
		exists, err := adapter.Exists(ctx, id)
		require.NoError(t, err)
		assert.False(t, exists)
	})
}
