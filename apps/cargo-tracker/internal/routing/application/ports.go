// Package application は Routing Context のユースケースとポートを提供する。
package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/domain"
)

// VoyageRepository は航海集約の永続化ポート（出力ポート）。
type VoyageRepository interface {
	Save(ctx context.Context, voyage *domain.Voyage) error
	Update(ctx context.Context, voyage *domain.Voyage) error
	Exists(ctx context.Context, voyageNumber domain.VoyageNumber) (bool, error)
	FindByNumber(ctx context.Context, voyageNumber domain.VoyageNumber) (*domain.Voyage, error)
}
