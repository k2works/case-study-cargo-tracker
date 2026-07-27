package application

import (
	"context"
	"log/slog"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/domain"
)

// NotificationPort は荷主・管理職への通知を抽象化する出力ポート（US17/US19/US20）。
// 実体は合成ルートでログ実装を注入する（実メール送信は後続の外部連携）。
type NotificationPort interface {
	NotifyShipper(ctx context.Context, bookingID, summary string) error
	NotifyManager(ctx context.Context, bookingID, summary string) error
}

// ExceptionService は例外処理・状態手動更新のユースケース（US17/US19/US20）。
type ExceptionService struct {
	repo     TrackingActivityRepository
	notifier NotificationPort
	clock    shared.Clock
	policy   domain.EscalationPolicy
}

// NewExceptionService は ExceptionService を生成する。
func NewExceptionService(repo TrackingActivityRepository, notifier NotificationPort, clock shared.Clock) *ExceptionService {
	return &ExceptionService{repo: repo, notifier: notifier, clock: clock}
}

// RegisterExceptionCommand は例外登録コマンド（US19/US20）。
type RegisterExceptionCommand struct {
	TrackingNumber   string
	ExceptionType    string
	LocationUnLocode string
	OccurredAt       time.Time
	Description      string
}

// RegisterException は例外を登録し、貨物状態を EXCEPTION に遷移させ荷主に通知する。
// エスカレーション要（紛失 / 遅延 48h 超過）の場合は管理職へエスカレーション通知する。
func (s *ExceptionService) RegisterException(ctx context.Context, cmd RegisterExceptionCommand) error {
	activity, err := s.repo.FindByTrackingNumber(ctx, cmd.TrackingNumber)
	if err != nil {
		return err
	}
	et, err := domain.ParseExceptionType(cmd.ExceptionType)
	if err != nil {
		return err
	}
	location, err := shared.NewLocation(cmd.LocationUnLocode)
	if err != nil {
		return err
	}
	escalate := s.policy.RequiresEscalation(et, cmd.OccurredAt, s.clock.Now())
	activity.AddException(domain.NewTrackingExceptionEvent(et, location, cmd.OccurredAt, cmd.Description, escalate))
	if err := s.repo.Save(ctx, activity); err != nil {
		return err
	}
	// 通知はベストエフォート（永続化はコミット済みのため、通知失敗でユースケースを失敗させない）。
	s.notifyShipper(ctx, activity.BookingId(), "例外が発生しました: "+et.Ja())
	if escalate {
		s.notifyManager(ctx, activity.BookingId(), "エスカレーション: "+et.Ja()+" が発生しました")
	}
	return nil
}

// notifyShipper / notifyManager は通知をベストエフォートで送信し、失敗はログに留める。
func (s *ExceptionService) notifyShipper(ctx context.Context, bookingID, summary string) {
	if err := s.notifier.NotifyShipper(ctx, bookingID, summary); err != nil {
		slog.WarnContext(ctx, "notify shipper failed", "bookingId", bookingID, "error", err)
	}
}

func (s *ExceptionService) notifyManager(ctx context.Context, bookingID, summary string) {
	if err := s.notifier.NotifyManager(ctx, bookingID, summary); err != nil {
		slog.WarnContext(ctx, "notify manager failed", "bookingId", bookingID, "error", err)
	}
}

// ResolveExceptionCommand は例外解決（対応報告）コマンド（US19/US20）。
type ResolveExceptionCommand struct {
	TrackingNumber  string
	Index           int
	ResolutionNotes string
}

// ResolveException は例外を解決し、貨物状態を発生前に復帰させ荷主に対応報告を通知する。
func (s *ExceptionService) ResolveException(ctx context.Context, cmd ResolveExceptionCommand) error {
	activity, err := s.repo.FindByTrackingNumber(ctx, cmd.TrackingNumber)
	if err != nil {
		return err
	}
	if err := activity.ResolveException(cmd.Index, cmd.ResolutionNotes, s.clock.Now()); err != nil {
		return err
	}
	if err := s.repo.Save(ctx, activity); err != nil {
		return err
	}
	s.notifyShipper(ctx, activity.BookingId(), "例外対応を報告します: "+cmd.ResolutionNotes)
	return nil
}

// ManualUpdateStatusCommand は貨物状態手動更新コマンド（US17）。
type ManualUpdateStatusCommand struct {
	TrackingNumber   string
	TransportStatus  string
	LocationUnLocode string
	CompletionTime   time.Time
}

// ManualUpdateStatus は追跡管理者が貨物状態を手動更新し、追跡イベントに記録して荷主に通知する。
func (s *ExceptionService) ManualUpdateStatus(ctx context.Context, cmd ManualUpdateStatusCommand) error {
	activity, err := s.repo.FindByTrackingNumber(ctx, cmd.TrackingNumber)
	if err != nil {
		return err
	}
	status := shared.TransportStatus(cmd.TransportStatus)
	// EXCEPTION は例外エンティティ経由でのみ設定する（手動更新での直接指定は二重管理になるため拒否）。
	if !status.IsValid() || status == shared.TransportStatusException {
		return ErrInvalidTransportStatus
	}
	location, err := shared.NewLocation(cmd.LocationUnLocode)
	if err != nil {
		return err
	}
	activity.AddEvent(domain.NewTrackingActivityEvent(domain.EventTypeManual, location, cmd.CompletionTime, "", status))
	if err := s.repo.Save(ctx, activity); err != nil {
		return err
	}
	s.notifyShipper(ctx, activity.BookingId(), "貨物状態を更新しました: "+status.Ja())
	return nil
}
