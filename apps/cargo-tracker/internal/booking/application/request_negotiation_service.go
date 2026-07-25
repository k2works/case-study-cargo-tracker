package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
)

// NegotiationRequest は営業担当者への条件協議依頼のイベントペイロード（US10）。
type NegotiationRequest struct {
	BookingID   string
	ShipperCode string
	Origin      string
	Destination string
	Reason      string
}

// RequestNegotiationService は経路条件協議依頼ユースケース（US10）。
// 調整後も期限内の経路候補が見つからない場合、営業担当者に荷主との条件協議を依頼する。
// 実体は EventPublisher でイベント発行（IT1 loggingPublisher と同型・実通知は後続）。
type RequestNegotiationService struct {
	repo      CargoItineraryRepository
	publisher EventPublisher
}

// NewRequestNegotiationService は RequestNegotiationService を生成する。
func NewRequestNegotiationService(repo CargoItineraryRepository, publisher EventPublisher) *RequestNegotiationService {
	return &RequestNegotiationService{repo: repo, publisher: publisher}
}

// Request は指定予約について営業担当者へ条件協議を依頼する（US10）。
func (s *RequestNegotiationService) Request(ctx context.Context, bookingID domain.BookingId, reason string) error {
	cargo, err := s.repo.FindByBookingID(ctx, bookingID)
	if err != nil {
		return err
	}
	spec := cargo.RouteSpec()
	return s.publisher.Publish(ctx, "RouteNegotiationRequested", NegotiationRequest{
		BookingID:   cargo.BookingID().Value(),
		ShipperCode: cargo.ShipperCode().Value(),
		Origin:      spec.Origin().UnLocode(),
		Destination: spec.Destination().UnLocode(),
		Reason:      reason,
	})
}
