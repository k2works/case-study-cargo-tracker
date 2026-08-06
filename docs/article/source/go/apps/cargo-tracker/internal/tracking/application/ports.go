// Package application は Tracking Context のユースケースとポートを提供する。
package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/domain"
)

// TrackingActivityRepository は追跡レコード集約の永続化ポート（出力ポート）。
type TrackingActivityRepository interface {
	Save(ctx context.Context, activity *domain.TrackingActivity) error
	FindByTrackingNumber(ctx context.Context, trackingNumber string) (*domain.TrackingActivity, error)
	FindByBookingID(ctx context.Context, bookingID string) (*domain.TrackingActivity, error)
}
