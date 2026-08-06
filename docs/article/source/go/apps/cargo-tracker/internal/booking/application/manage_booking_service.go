package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
)

// ErrCargoNotFound は指定予約が存在しない場合に返される（domain のセンチネルを再エクスポート）。
var ErrCargoNotFound = domain.ErrCargoNotFound

// ManageBookingService は予約ライフサイクル（確定・キャンセル・差し戻し）を実行するコマンドサービス（US13）。
type ManageBookingService struct {
	repo BookingLifecycleRepository
}

// NewManageBookingService は ManageBookingService を生成する。
func NewManageBookingService(repo BookingLifecycleRepository) *ManageBookingService {
	return &ManageBookingService{repo: repo}
}

// Confirm は予約を確定する（US13）。
func (s *ManageBookingService) Confirm(ctx context.Context, bookingID string) error {
	return s.transition(ctx, bookingID, func(c *domain.Cargo) error { return c.Confirm() })
}

// Cancel は予約をキャンセルする（US13）。
func (s *ManageBookingService) Cancel(ctx context.Context, bookingID string) error {
	return s.transition(ctx, bookingID, func(c *domain.Cargo) error { return c.Cancel() })
}

// SendBackToRouting は予約を経路再設計へ差し戻す（US13）。
func (s *ManageBookingService) SendBackToRouting(ctx context.Context, bookingID string) error {
	return s.transition(ctx, bookingID, func(c *domain.Cargo) error { return c.SendBackToRouting() })
}

// AssignToRouting は予約を経路設計者へ引き渡す（US06）。
func (s *ManageBookingService) AssignToRouting(ctx context.Context, bookingID string) error {
	return s.transition(ctx, bookingID, func(c *domain.Cargo) error { return c.AssignToRouting() })
}

// transition は予約を取得し、指定の状態遷移を適用して永続化する共通処理。
func (s *ManageBookingService) transition(ctx context.Context, rawBookingID string, apply func(*domain.Cargo) error) error {
	bookingID, err := domain.NewBookingId(rawBookingID)
	if err != nil {
		return err
	}
	cargo, err := s.repo.FindByBookingID(ctx, bookingID)
	if err != nil {
		return err
	}
	if err := apply(cargo); err != nil {
		return err
	}
	return s.repo.UpdateStatus(ctx, cargo)
}
