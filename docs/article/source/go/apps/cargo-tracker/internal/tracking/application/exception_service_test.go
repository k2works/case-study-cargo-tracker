package application_test

import (
	"context"
	"testing"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	app "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type notifierStub struct {
	shipper []string
	manager []string
}

func (n *notifierStub) NotifyShipper(_ context.Context, _ string, summary string) error {
	n.shipper = append(n.shipper, summary)
	return nil
}
func (n *notifierStub) NotifyManager(_ context.Context, _ string, summary string) error {
	n.manager = append(n.manager, summary)
	return nil
}

func seedTracking(t *testing.T, repo *memRepo) {
	t.Helper()
	tn, _ := domain.NewTrackingNumber("TRK-20260720-0001")
	ta, _ := domain.NewTrackingActivity(tn, "CARGO-001")
	loc, _ := shared.NewLocation("JPTYO")
	ta.AddEvent(domain.NewTrackingActivityEvent("LOAD", loc, time.Now(), "V001", shared.TransportStatusLoaded))
	require.NoError(t, repo.Save(context.Background(), &ta))
}

func fixedClock(t time.Time) shared.Clock { return shared.FixedClock{Fixed: t} }

func TestRegisterException_Delay_Notifies(t *testing.T) {
	repo := newMemRepo()
	seedTracking(t, repo)
	notifier := &notifierStub{}
	occurred := time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC)
	svc := app.NewExceptionService(repo, notifier, fixedClock(occurred.Add(time.Hour)))

	err := svc.RegisterException(context.Background(), app.RegisterExceptionCommand{
		TrackingNumber: "TRK-20260720-0001", ExceptionType: "DELAY",
		LocationUnLocode: "SGSIN", OccurredAt: occurred, Description: "台風",
	})
	require.NoError(t, err)

	got, _ := repo.FindByTrackingNumber(context.Background(), "TRK-20260720-0001")
	assert.Equal(t, shared.TransportStatusException, got.CurrentStatus())
	require.Len(t, notifier.shipper, 1) // 荷主通知
	assert.Empty(t, notifier.manager)   // 48h 未満・遅延はエスカレーションなし
}

func TestRegisterException_Lost_Escalates(t *testing.T) {
	repo := newMemRepo()
	seedTracking(t, repo)
	notifier := &notifierStub{}
	svc := app.NewExceptionService(repo, notifier, fixedClock(time.Now()))

	err := svc.RegisterException(context.Background(), app.RegisterExceptionCommand{
		TrackingNumber: "TRK-20260720-0001", ExceptionType: "LOST",
		LocationUnLocode: "SGSIN", OccurredAt: time.Now(), Description: "コンテナ紛失",
	})
	require.NoError(t, err)

	got, _ := repo.FindByTrackingNumber(context.Background(), "TRK-20260720-0001")
	assert.True(t, got.Exceptions()[0].EscalationFlag())
	assert.Len(t, notifier.manager, 1) // 紛失は管理職エスカレーション
	assert.Len(t, notifier.shipper, 1)
}

func TestRegisterException_Delay48hEscalatesViaService(t *testing.T) {
	repo := newMemRepo()
	seedTracking(t, repo)
	notifier := &notifierStub{}
	occurred := time.Date(2026, 7, 20, 0, 0, 0, 0, time.UTC)
	// 発生から 48 時間 1 分後に登録 → 遅延でも管理職エスカレーション。
	svc := app.NewExceptionService(repo, notifier, fixedClock(occurred.Add(48*time.Hour+time.Minute)))
	require.NoError(t, svc.RegisterException(context.Background(), app.RegisterExceptionCommand{
		TrackingNumber: "TRK-20260720-0001", ExceptionType: "DELAY",
		LocationUnLocode: "SGSIN", OccurredAt: occurred, Description: "長期遅延",
	}))
	got, _ := repo.FindByTrackingNumber(context.Background(), "TRK-20260720-0001")
	assert.True(t, got.Exceptions()[0].EscalationFlag())
	assert.Len(t, notifier.manager, 1)
}

func TestRegisterException_DamageNoEscalation(t *testing.T) {
	repo := newMemRepo()
	seedTracking(t, repo)
	notifier := &notifierStub{}
	svc := app.NewExceptionService(repo, notifier, fixedClock(time.Now()))
	require.NoError(t, svc.RegisterException(context.Background(), app.RegisterExceptionCommand{
		TrackingNumber: "TRK-20260720-0001", ExceptionType: "DAMAGE",
		LocationUnLocode: "SGSIN", OccurredAt: time.Now(), Description: "外装破損",
	}))
	got, _ := repo.FindByTrackingNumber(context.Background(), "TRK-20260720-0001")
	assert.Equal(t, shared.TransportStatusException, got.CurrentStatus())
	assert.False(t, got.Exceptions()[0].EscalationFlag())
	assert.Empty(t, notifier.manager) // 破損はエスカレーションなし
}

func TestManualUpdateStatus_RejectsException(t *testing.T) {
	repo := newMemRepo()
	seedTracking(t, repo)
	svc := app.NewExceptionService(repo, &notifierStub{}, fixedClock(time.Now()))
	err := svc.ManualUpdateStatus(context.Background(), app.ManualUpdateStatusCommand{
		TrackingNumber: "TRK-20260720-0001", TransportStatus: "EXCEPTION",
		LocationUnLocode: "SGSIN", CompletionTime: time.Now(),
	})
	assert.ErrorIs(t, err, app.ErrInvalidTransportStatus)
}

func TestResolveException_RestoresStatus(t *testing.T) {
	repo := newMemRepo()
	seedTracking(t, repo)
	notifier := &notifierStub{}
	svc := app.NewExceptionService(repo, notifier, fixedClock(time.Now()))
	require.NoError(t, svc.RegisterException(context.Background(), app.RegisterExceptionCommand{
		TrackingNumber: "TRK-20260720-0001", ExceptionType: "DELAY",
		LocationUnLocode: "SGSIN", OccurredAt: time.Now(), Description: "遅延",
	}))

	err := svc.ResolveException(context.Background(), app.ResolveExceptionCommand{
		TrackingNumber: "TRK-20260720-0001", Index: 0, ResolutionNotes: "代替便手配",
	})
	require.NoError(t, err)

	got, _ := repo.FindByTrackingNumber(context.Background(), "TRK-20260720-0001")
	assert.Equal(t, shared.TransportStatusLoaded, got.CurrentStatus())
	assert.True(t, got.Exceptions()[0].IsResolved())
	// 対応報告通知（登録時 + 解決時）
	assert.GreaterOrEqual(t, len(notifier.shipper), 2)
}

func TestManualUpdateStatus_AddsEventAndNotifies(t *testing.T) {
	repo := newMemRepo()
	seedTracking(t, repo)
	notifier := &notifierStub{}
	svc := app.NewExceptionService(repo, notifier, fixedClock(time.Now()))

	err := svc.ManualUpdateStatus(context.Background(), app.ManualUpdateStatusCommand{
		TrackingNumber: "TRK-20260720-0001", TransportStatus: "ONBOARD_CARRIER",
		LocationUnLocode: "SGSIN", CompletionTime: time.Now(),
	})
	require.NoError(t, err)
	got, _ := repo.FindByTrackingNumber(context.Background(), "TRK-20260720-0001")
	assert.Equal(t, shared.TransportStatusOnboardCarrier, got.CurrentStatus())
	assert.Len(t, notifier.shipper, 1)
}

func TestRegisterException_UnknownType(t *testing.T) {
	repo := newMemRepo()
	seedTracking(t, repo)
	svc := app.NewExceptionService(repo, &notifierStub{}, fixedClock(time.Now()))
	err := svc.RegisterException(context.Background(), app.RegisterExceptionCommand{
		TrackingNumber: "TRK-20260720-0001", ExceptionType: "FOO",
		LocationUnLocode: "SGSIN", OccurredAt: time.Now(),
	})
	assert.ErrorIs(t, err, domain.ErrUnknownExceptionType)
}

func TestRegisterException_TrackingNotFound(t *testing.T) {
	svc := app.NewExceptionService(newMemRepo(), &notifierStub{}, fixedClock(time.Now()))
	err := svc.RegisterException(context.Background(), app.RegisterExceptionCommand{
		TrackingNumber: "TRK-20260720-9999", ExceptionType: "DELAY",
		LocationUnLocode: "SGSIN", OccurredAt: time.Now(),
	})
	assert.ErrorIs(t, err, app.ErrTrackingNotFound)
}

func TestRegisterException_InvalidLocation(t *testing.T) {
	repo := newMemRepo()
	seedTracking(t, repo)
	svc := app.NewExceptionService(repo, &notifierStub{}, fixedClock(time.Now()))
	err := svc.RegisterException(context.Background(), app.RegisterExceptionCommand{
		TrackingNumber: "TRK-20260720-0001", ExceptionType: "DELAY",
		LocationUnLocode: "bad", OccurredAt: time.Now(),
	})
	assert.Error(t, err)
}

func TestResolveException_NotFound(t *testing.T) {
	svc := app.NewExceptionService(newMemRepo(), &notifierStub{}, fixedClock(time.Now()))
	err := svc.ResolveException(context.Background(), app.ResolveExceptionCommand{
		TrackingNumber: "TRK-20260720-9999", Index: 0, ResolutionNotes: "x",
	})
	assert.ErrorIs(t, err, app.ErrTrackingNotFound)
}

func TestResolveException_InvalidIndex(t *testing.T) {
	repo := newMemRepo()
	seedTracking(t, repo)
	svc := app.NewExceptionService(repo, &notifierStub{}, fixedClock(time.Now()))
	err := svc.ResolveException(context.Background(), app.ResolveExceptionCommand{
		TrackingNumber: "TRK-20260720-0001", Index: 9, ResolutionNotes: "x",
	})
	assert.Error(t, err)
}

func TestManualUpdateStatus_InvalidStatus(t *testing.T) {
	repo := newMemRepo()
	seedTracking(t, repo)
	svc := app.NewExceptionService(repo, &notifierStub{}, fixedClock(time.Now()))
	err := svc.ManualUpdateStatus(context.Background(), app.ManualUpdateStatusCommand{
		TrackingNumber: "TRK-20260720-0001", TransportStatus: "BOGUS",
		LocationUnLocode: "SGSIN", CompletionTime: time.Now(),
	})
	assert.ErrorIs(t, err, app.ErrInvalidTransportStatus)
}

func TestManualUpdateStatus_NotFound(t *testing.T) {
	svc := app.NewExceptionService(newMemRepo(), &notifierStub{}, fixedClock(time.Now()))
	err := svc.ManualUpdateStatus(context.Background(), app.ManualUpdateStatusCommand{
		TrackingNumber: "TRK-20260720-9999", TransportStatus: "RECEIVED",
		LocationUnLocode: "SGSIN", CompletionTime: time.Now(),
	})
	assert.ErrorIs(t, err, app.ErrTrackingNotFound)
}
