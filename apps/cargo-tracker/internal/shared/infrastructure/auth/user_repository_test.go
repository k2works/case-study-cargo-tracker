//go:build integration

package auth_test

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/jackc/pgx/v5/pgxpool"
	authinfra "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/auth"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
)

func setupPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	ctx := context.Background()
	migrations, err := filepath.Abs("../../../../db/migrations")
	require.NoError(t, err)

	container, err := postgres.Run(ctx,
		"postgres:16-alpine",
		postgres.WithDatabase("cargo_tracker"),
		postgres.WithUsername("cargo"),
		postgres.WithPassword("cargo"),
		postgres.WithInitScripts(
			filepath.Join(migrations, "000001_create_shipper.up.sql"),
			filepath.Join(migrations, "000002_create_cargo.up.sql"),
			filepath.Join(migrations, "000003_create_users.up.sql"),
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

func TestUserRepository_FindByUsername(t *testing.T) {
	pool := setupPool(t)
	repo := authinfra.NewUserRepository(pool)
	ctx := context.Background()

	t.Run("シードされた admin をロール付きで取得できる", func(t *testing.T) {
		u, found, err := repo.FindByUsername(ctx, "admin")
		require.NoError(t, err)
		require.True(t, found)
		assert.Equal(t, "admin", u.Username())
		assert.True(t, u.IsEnabled())
		assert.True(t, u.HasRole("ROLE_ADMIN"))
	})

	t.Run("sales は複数ロールを持つ", func(t *testing.T) {
		u, found, err := repo.FindByUsername(ctx, "sales")
		require.NoError(t, err)
		require.True(t, found)
		assert.True(t, u.HasRole("ROLE_SALES"))
		assert.True(t, u.HasRole("ROLE_SHIPPER"))
	})

	t.Run("存在しないユーザーは found=false", func(t *testing.T) {
		_, found, err := repo.FindByUsername(ctx, "nobody")
		require.NoError(t, err)
		assert.False(t, found)
	})
}
