package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
)

// CargoTrackingRepository は追跡番号発行に伴う貨物の取得・更新ポート（US14）。
type CargoTrackingRepository interface {
	FindByBookingID(ctx context.Context, bookingID domain.BookingId) (*domain.Cargo, error)
	// UpdateTracking は予約状態・輸送状態・追跡番号を更新する。
	UpdateTracking(ctx context.Context, cargo *domain.Cargo) error
}

// TrackingNumberIssuer は一意の追跡番号（TRK-YYYYMMDD-NNNN）を採番する出力ポート。
// 採番の実体は合成ルートで注入する（Tracking の日次連番を利用）。
type TrackingNumberIssuer interface {
	Next(ctx context.Context) (string, error)
}

// TrackingActivityCreator は追跡レコードの新規作成を Tracking Context へ委譲する ACL ポート。
// Booking は Tracking に直接依存せず、合成ルートで TrackingCommandService.CreateTracking を配線する。
type TrackingActivityCreator interface {
	Create(ctx context.Context, trackingNumber, bookingID string) error
}

// AssignTrackingNumberService は追跡番号発行ユースケース（US14）。
type AssignTrackingNumberService struct {
	repo     CargoTrackingRepository
	issuer   TrackingNumberIssuer
	creator  TrackingActivityCreator
	notifier NotificationPort
}

// NewAssignTrackingNumberService は AssignTrackingNumberService を生成する。
func NewAssignTrackingNumberService(repo CargoTrackingRepository, issuer TrackingNumberIssuer, creator TrackingActivityCreator, notifier NotificationPort) *AssignTrackingNumberService {
	return &AssignTrackingNumberService{repo: repo, issuer: issuer, creator: creator, notifier: notifier}
}

// Assign は確定済み予約に追跡番号を発行し、追跡レコードを作成して荷主へ通知する。
// 発行された追跡番号を返す。
func (s *AssignTrackingNumberService) Assign(ctx context.Context, bookingID string) (string, error) {
	bid, err := domain.NewBookingId(bookingID)
	if err != nil {
		return "", err
	}
	cargo, err := s.repo.FindByBookingID(ctx, bid)
	if err != nil {
		return "", err
	}
	trackingNumber, err := s.issuer.Next(ctx)
	if err != nil {
		return "", err
	}
	if err := cargo.IssueTrackingNumber(trackingNumber); err != nil {
		return "", err
	}
	if err := s.repo.UpdateTracking(ctx, cargo); err != nil {
		return "", err
	}
	if err := s.creator.Create(ctx, trackingNumber, bookingID); err != nil {
		return "", err
	}
	if err := s.notifier.Notify(ctx, cargo.ShipperCode(), "追跡番号を発行しました: "+trackingNumber); err != nil {
		return "", err
	}
	return trackingNumber, nil
}
