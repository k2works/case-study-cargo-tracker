// Package application は Estimation Context のユースケースとポートを提供する。
package application

import (
	"context"
	"time"

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

// RouteSearcher は経路探索への ACL ポート（T3: 見積候補の実経路化）。
// Estimation は Routing に直接依存せず、自 BC の語彙で経路候補を受け取る。
// 実体は合成ルート（cmd/server）で Routing の SearchRoutesService を変換注入する。
type RouteSearcher interface {
	Search(ctx context.Context, spec RouteSearchSpec) ([]RouteCandidateResult, error)
}

// RouteSearchSpec は経路探索の要求（Estimation の語彙）。
type RouteSearchSpec struct {
	OriginUnLocode      string
	DestinationUnLocode string
	ArrivalDeadline     time.Time
	CargoType           string
}

// RouteCandidateResult は経路探索が返す候補（Estimation の語彙）。
type RouteCandidateResult struct {
	VoyageNumbers []string
	TransitDays   int
	EstimatedCost int64
	Waypoints     []string
}
