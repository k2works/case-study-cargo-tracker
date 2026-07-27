package application

import (
	"context"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// RegisterHandlingActivityCommand は荷役作業登録コマンド（US15/US16）。
type RegisterHandlingActivityCommand struct {
	BookingID             string
	HandlingType          string
	LocationUnLocode      string
	VoyageNumber          string
	ConsigneeConfirmation string
	CompletionTime        time.Time
	OperatorName          string
}

// RegisterHandlingActivityResult は登録結果（妥当性検証の判定）を表す。
type RegisterHandlingActivityResult struct {
	Warning   bool
	Misrouted bool
}

// RegisterHandlingActivityService は荷役作業登録ユースケース。
type RegisterHandlingActivityService struct {
	repo      HandlingActivityRepository
	snapshots CargoSnapshotProvider
	publisher HandlingEventPublisher
}

// NewRegisterHandlingActivityService は RegisterHandlingActivityService を生成する。
func NewRegisterHandlingActivityService(repo HandlingActivityRepository, snapshots CargoSnapshotProvider, publisher HandlingEventPublisher) *RegisterHandlingActivityService {
	return &RegisterHandlingActivityService{repo: repo, snapshots: snapshots, publisher: publisher}
}

// Register は荷役作業を記録し、妥当性検証結果を返す。
// 記録後、輸送状態同期のためのイベントを発行する（Tracking / Booking へ配線）。
func (s *RegisterHandlingActivityService) Register(ctx context.Context, cmd RegisterHandlingActivityCommand) (RegisterHandlingActivityResult, error) {
	handlingType, err := domain.ParseHandlingType(cmd.HandlingType)
	if err != nil {
		return RegisterHandlingActivityResult{}, err
	}
	location, err := shared.NewLocation(cmd.LocationUnLocode)
	if err != nil {
		return RegisterHandlingActivityResult{}, err
	}
	activity, err := domain.NewHandlingActivity(
		cmd.BookingID, handlingType, location, cmd.CompletionTime,
		cmd.VoyageNumber, cmd.ConsigneeConfirmation, cmd.OperatorName,
	)
	if err != nil {
		return RegisterHandlingActivityResult{}, err
	}

	snapshot, err := s.snapshots.FetchSnapshot(ctx, cmd.BookingID)
	if err != nil {
		return RegisterHandlingActivityResult{}, err
	}
	validation := activity.IsValidFor(snapshot)

	if err := s.repo.Save(ctx, activity); err != nil {
		return RegisterHandlingActivityResult{}, err
	}

	event := HandlingActivityRegisteredEvent{
		BookingID:        cmd.BookingID,
		HandlingType:     handlingType.String(),
		LocationUnLocode: cmd.LocationUnLocode,
		VoyageNumber:     cmd.VoyageNumber,
		TransportStatus:  string(handlingType.ResultingTransportStatus()),
		Misrouted:        validation.Misrouted(),
	}
	if err := s.publisher.Publish(ctx, event); err != nil {
		return RegisterHandlingActivityResult{}, err
	}

	return RegisterHandlingActivityResult{Warning: validation.Warning(), Misrouted: validation.Misrouted()}, nil
}

// ListByBookingID は指定予約の荷役作業一覧を返す。
func (s *RegisterHandlingActivityService) ListByBookingID(ctx context.Context, bookingID string) ([]domain.HandlingActivity, error) {
	return s.repo.ListByBookingID(ctx, bookingID)
}
