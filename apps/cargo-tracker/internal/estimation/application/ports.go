// Package application は Estimation Context のユースケースとポートを提供する。
package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/domain"
)

// EstimateRepository は見積集約の永続化ポート（出力ポート）。
type EstimateRepository interface {
	Save(ctx context.Context, e *domain.Estimate) error
	FindByEstimateId(ctx context.Context, id domain.EstimateId) (*domain.Estimate, error)
	ListAll(ctx context.Context) ([]*domain.Estimate, error)
}

// IDGenerator は一意 ID（UUID）を採番する横断ポート。
type IDGenerator interface {
	Generate() string
}
