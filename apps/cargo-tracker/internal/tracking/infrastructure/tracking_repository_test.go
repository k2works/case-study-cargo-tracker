//go:build integration

package infrastructure_test

import (
	"context"
	"path/filepath"
	"sort"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	trackingapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/infrastructure"
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

	container, err := postgres.Run(ctx,
		"postgres:16-alpine",
		postgres.WithDatabase("cargo_tracker"),
		postgres.WithUsername("cargo"),
		postgres.WithPassword("cargo"),
		postgres.WithInitScripts(files...),
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

func tn(t *testing.T, v string) domain.TrackingNumber {
	t.Helper()
	n, err := domain.NewTrackingNumber(v)
	require.NoError(t, err)
	return n
}

func loc(t *testing.T, code string) shared.Location {
	t.Helper()
	l, err := shared.NewLocation(code)
	require.NoError(t, err)
	return l
}

func TestTrackingRepository_CreateAndAppendEvents(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewTrackingActivityRepository(pool)
	ctx := context.Background()

	// 追跡レコード作成（イベント無し・受領待ち）。
	act, err := domain.NewTrackingActivity(tn(t, "TRK-20260720-0001"), "CARGO-001")
	require.NoError(t, err)
	require.NoError(t, repo.Save(ctx, &act))

	got, err := repo.FindByTrackingNumber(ctx, "TRK-20260720-0001")
	require.NoError(t, err)
	assert.Equal(t, shared.TransportStatusNotReceived, got.CurrentStatus())
	assert.Empty(t, got.Events())

	// イベント追記（受領→積込）。既存分は追記されず seq 継続。
	got.AddEvent(domain.NewTrackingActivityEvent("RECEIVE", loc(t, "JPTYO"),
		time.Date(2026, 7, 20, 10, 0, 0, 0, time.UTC), "", shared.TransportStatusReceived))
	require.NoError(t, repo.Save(ctx, got))
	got.AddEvent(domain.NewTrackingActivityEvent("LOAD", loc(t, "JPTYO"),
		time.Date(2026, 7, 21, 9, 0, 0, 0, time.UTC), "V001", shared.TransportStatusLoaded))
	require.NoError(t, repo.Save(ctx, got))

	// 予約 ID で再構築し時系列・状態を検証。
	reloaded, err := repo.FindByBookingID(ctx, "CARGO-001")
	require.NoError(t, err)
	require.Len(t, reloaded.Events(), 2)
	assert.Equal(t, shared.TransportStatusLoaded, reloaded.CurrentStatus())
	assert.Equal(t, "JPTYO", reloaded.CurrentLocation().UnLocode())
	assert.Equal(t, "RECEIVE", reloaded.Events()[0].EventType())
	assert.Equal(t, "LOAD", reloaded.Events()[1].EventType())
	assert.Equal(t, "V001", reloaded.Events()[1].VoyageNumber())
}

func TestTrackingRepository_NextTrackingNumber(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewTrackingActivityRepository(pool)
	ctx := context.Background()
	day := time.Date(2026, 7, 20, 12, 0, 0, 0, time.UTC)

	first, err := repo.NextTrackingNumber(ctx, day)
	require.NoError(t, err)
	assert.Equal(t, "TRK-20260720-0001", first)

	act, err := domain.NewTrackingActivity(tn(t, first), "CARGO-001")
	require.NoError(t, err)
	require.NoError(t, repo.Save(ctx, &act))

	// 発行済み 1 件 → 次は 0002（日次連番の一意採番）。
	second, err := repo.NextTrackingNumber(ctx, day)
	require.NoError(t, err)
	assert.Equal(t, "TRK-20260720-0002", second)
}

func TestTrackingRepository_NotFound(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewTrackingActivityRepository(pool)
	_, err := repo.FindByTrackingNumber(context.Background(), "TRK-20260720-9999")
	assert.ErrorIs(t, err, trackingapp.ErrTrackingNotFound)
}

func TestTrackingRepository_ExceptionLifecycle(t *testing.T) {
	pool := setupPool(t)
	repo := infrastructure.NewTrackingActivityRepository(pool)
	ctx := context.Background()

	act, err := domain.NewTrackingActivity(tn(t, "TRK-20260720-0001"), "CARGO-001")
	require.NoError(t, err)
	act.AddEvent(domain.NewTrackingActivityEvent("LOAD", loc(t, "JPTYO"), time.Now(), "V001", shared.TransportStatusLoaded))
	require.NoError(t, repo.Save(ctx, &act))

	// 例外登録（紛失・緊急フラグ）→ EXCEPTION。
	loaded, err := repo.FindByTrackingNumber(ctx, "TRK-20260720-0001")
	require.NoError(t, err)
	loaded.AddException(domain.NewTrackingExceptionEvent(
		domain.ExceptionTypeLost, loc(t, "SGSIN"), time.Now(), "コンテナ紛失", true))
	require.NoError(t, repo.Save(ctx, loaded))

	afterEx, err := repo.FindByTrackingNumber(ctx, "TRK-20260720-0001")
	require.NoError(t, err)
	require.Len(t, afterEx.Exceptions(), 1)
	assert.Equal(t, shared.TransportStatusException, afterEx.CurrentStatus())
	assert.True(t, afterEx.Exceptions()[0].EscalationFlag())
	assert.True(t, afterEx.Exceptions()[0].ID() > 0)

	// 例外解決 → 発生前状態（LOADED）に復帰。
	require.NoError(t, afterEx.ResolveException(0, "代替便手配で解消", time.Now()))
	require.NoError(t, repo.Save(ctx, afterEx))

	resolved, err := repo.FindByTrackingNumber(ctx, "TRK-20260720-0001")
	require.NoError(t, err)
	assert.False(t, resolved.HasActiveException())
	assert.Equal(t, shared.TransportStatusLoaded, resolved.CurrentStatus())
	assert.Equal(t, "代替便手配で解消", resolved.Exceptions()[0].ResolutionNotes())
}
