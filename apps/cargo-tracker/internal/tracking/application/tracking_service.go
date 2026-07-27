package application

import (
	"context"
	"errors"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/domain"
)

// ErrTrackingNotFound は追跡レコードが存在しない場合に返される。
var ErrTrackingNotFound = errors.New("tracking activity not found")

// TrackingCommandService は追跡レコードの作成・イベント記録ユースケース。
type TrackingCommandService struct {
	repo TrackingActivityRepository
}

// NewTrackingCommandService は TrackingCommandService を生成する。
func NewTrackingCommandService(repo TrackingActivityRepository) *TrackingCommandService {
	return &TrackingCommandService{repo: repo}
}

// CreateTracking は追跡番号発行（US14）に伴い追跡レコードを新規作成する。
// Booking の AssignTrackingNumber イベントを合成ルートで配線して呼び出す。
func (s *TrackingCommandService) CreateTracking(ctx context.Context, trackingNumber, bookingID string) error {
	tn, err := domain.NewTrackingNumber(trackingNumber)
	if err != nil {
		return err
	}
	activity, err := domain.NewTrackingActivity(tn, bookingID)
	if err != nil {
		return err
	}
	return s.repo.Save(ctx, &activity)
}

// RecordHandlingEventCommand は荷役作業に伴う追跡イベント記録コマンド。
type RecordHandlingEventCommand struct {
	BookingID        string
	HandlingType     string
	LocationUnLocode string
	VoyageNumber     string
	TransportStatus  string
	CompletionTime   time.Time
}

// RecordHandlingEvent は荷役作業登録イベントを受けて追跡イベントを追加し、状態を遷移させる（US15/US16）。
func (s *TrackingCommandService) RecordHandlingEvent(ctx context.Context, cmd RecordHandlingEventCommand) error {
	activity, err := s.repo.FindByBookingID(ctx, cmd.BookingID)
	if err != nil {
		return err
	}
	location, err := shared.NewLocation(cmd.LocationUnLocode)
	if err != nil {
		return err
	}
	event := domain.NewTrackingActivityEvent(
		cmd.HandlingType, location, cmd.CompletionTime, cmd.VoyageNumber,
		shared.TransportStatus(cmd.TransportStatus),
	)
	activity.AddEvent(event)
	return s.repo.Save(ctx, activity)
}
