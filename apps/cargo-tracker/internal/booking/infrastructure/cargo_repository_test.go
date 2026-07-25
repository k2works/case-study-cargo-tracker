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
			filepath.Join(migrations, "000005_add_cargo_special_cargo.up.sql"),
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

	cargo, err := domain.RegisterCargo(domain.CargoParams{BookingID: bookingID, ShipperCode: shipperCode, RouteSpec: spec, CargoType: domain.CargoTypeGeneral, Weight: weight, BookingAmount: domain.NewMoney(0, "JPY"), Hazardous: nil, Temperature: nil})
	require.NoError(t, err)

	t.Run("貨物予約を保存できる", func(t *testing.T) {
		err := repo.Save(ctx, cargo)
		require.NoError(t, err)
	})
}

// T5: 特殊貨物（冷凍）の Save→Find ラウンドトリップ検証。
func TestCargoRepository_SaveAndFindRefrigerated(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewCargoRepository(pool)
	ctx := context.Background()

	shipperCode, _ := shared.NewShipperCode("SHP-REEFER01")
	bookingID, _ := domain.NewBookingId("BKG-REEFER01")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	spec, _ := domain.NewRouteSpecification(origin, dest, time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC))
	weight, _ := domain.NewWeight(800.25)
	temp, _ := domain.NewTemperatureRequirement(-20.5, -5.0, domain.TemperatureUnitCelsius)

	cargo, err := domain.RegisterCargo(domain.CargoParams{BookingID: bookingID, ShipperCode: shipperCode, RouteSpec: spec, CargoType: domain.CargoTypeRefrigerated, Weight: weight, BookingAmount: domain.NewMoney(0, "JPY"), Hazardous: nil, Temperature: &temp})
	require.NoError(t, err)
	require.NoError(t, repo.Save(ctx, cargo))

	got, err := repo.FindByBookingID(ctx, bookingID)
	require.NoError(t, err)
	assert.Equal(t, domain.CargoTypeRefrigerated, got.CargoType())
	require.NotNil(t, got.TemperatureRequirement())
	assert.InDelta(t, -20.5, got.TemperatureRequirement().MinTemperature(), 0.001)
	assert.InDelta(t, -5.0, got.TemperatureRequirement().MaxTemperature(), 0.001)
	assert.Equal(t, domain.TemperatureUnitCelsius, got.TemperatureRequirement().Unit())
	assert.Nil(t, got.HazardousDeclaration())
}

// US13: 状態更新（確定）の永続化検証。
func TestCargoRepository_UpdateStatus(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewCargoRepository(pool)
	ctx := context.Background()

	shipperCode, _ := shared.NewShipperCode("SHP-CONFIRM1")
	bookingID, _ := domain.NewBookingId("BKG-CONFIRM1")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	spec, _ := domain.NewRouteSpecification(origin, dest, time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC))
	weight, _ := domain.NewWeight(500)

	cargo, _ := domain.RegisterCargo(domain.CargoParams{BookingID: bookingID, ShipperCode: shipperCode, RouteSpec: spec, CargoType: domain.CargoTypeGeneral, Weight: weight, BookingAmount: domain.NewMoney(0, "JPY"), Hazardous: nil, Temperature: nil})
	require.NoError(t, repo.Save(ctx, cargo))

	require.NoError(t, cargo.Confirm())
	require.NoError(t, repo.UpdateStatus(ctx, cargo))

	got, err := repo.FindByBookingID(ctx, bookingID)
	require.NoError(t, err)
	assert.Equal(t, domain.BookingStatusConfirmed, got.Status())
}

// CargoQuery（CQRS 読み取り）の一覧取得を検証する。
func TestCargoQuery_ListCargos(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewCargoRepository(pool)
	query := infrastructure.NewCargoQuery(pool)
	ctx := context.Background()

	shipperCode, _ := shared.NewShipperCode("SHP-LIST0001")
	bookingID, _ := domain.NewBookingId("BKG-LIST0001")
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("DEHAM")
	spec, _ := domain.NewRouteSpecification(origin, dest, time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC))
	weight, _ := domain.NewWeight(500)
	cargo, _ := domain.RegisterCargo(domain.CargoParams{BookingID: bookingID, ShipperCode: shipperCode, RouteSpec: spec, CargoType: domain.CargoTypeGeneral, Weight: weight, BookingAmount: domain.NewMoney(0, "JPY")})
	require.NoError(t, repo.Save(ctx, cargo))

	items, err := query.ListCargos(ctx)
	require.NoError(t, err)
	require.NotEmpty(t, items)
	found := false
	for _, it := range items {
		if it.BookingID == "BKG-LIST0001" {
			found = true
			assert.Equal(t, "SHP-LIST0001", it.ShipperCode)
			assert.Equal(t, "JPTYO", it.Origin)
			assert.Equal(t, "DEHAM", it.Destination)
			assert.Equal(t, "仮受付", it.StatusJa)
		}
	}
	assert.True(t, found, "登録した予約が一覧に含まれる")
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
