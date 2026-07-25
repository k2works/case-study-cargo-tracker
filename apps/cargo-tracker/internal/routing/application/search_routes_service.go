package application

import (
	"context"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// RouteSearchQuery は経路探索の照会条件（US08）。
type RouteSearchQuery struct {
	OriginUnLocode      string
	DestinationUnLocode string
	ArrivalDeadline     time.Time
	CargoType           string
}

// LegView は経路候補を構成する 1 区間の表示・受け渡し用 DTO（domain 型を漏らさない）。
type LegView struct {
	VoyageNumber   string
	LoadUnLocode   string
	UnloadUnLocode string
	LoadTime       time.Time
	UnloadTime     time.Time
}

// RouteCandidateView は経路候補の公開 DTO。BC 横断（Booking の RouteSearcher ポート）への受け渡しにも用いる。
type RouteCandidateView struct {
	Legs          []LegView
	TransitDays   int
	EstimatedCost int64
	Waypoints     []string
}

// SearchRoutesService は経路候補算出ユースケース（US08）。RouteFinder ドメインサービスを駆動する。
type SearchRoutesService struct {
	repo   VoyageRepository
	finder domain.RouteFinder
}

// NewSearchRoutesService は SearchRoutesService を生成する。
func NewSearchRoutesService(repo VoyageRepository) *SearchRoutesService {
	return &SearchRoutesService{repo: repo}
}

// Search は条件を満たす経路候補を推奨順（区間数少→所要日数→費用）で返す。
func (s *SearchRoutesService) Search(ctx context.Context, q RouteSearchQuery) ([]RouteCandidateView, error) {
	origin, err := shared.NewLocation(q.OriginUnLocode)
	if err != nil {
		return nil, err
	}
	dest, err := shared.NewLocation(q.DestinationUnLocode)
	if err != nil {
		return nil, err
	}
	cargoType, err := shared.ParseCargoType(q.CargoType)
	if err != nil {
		return nil, err
	}
	spec, err := domain.NewRouteSpecification(origin, dest, q.ArrivalDeadline, cargoType)
	if err != nil {
		return nil, err
	}
	voyages, err := s.repo.ListAll(ctx)
	if err != nil {
		return nil, err
	}
	candidates := s.finder.FindCandidates(spec, voyages)
	views := make([]RouteCandidateView, 0, len(candidates))
	for _, c := range candidates {
		views = append(views, toRouteCandidateView(c))
	}
	return views, nil
}

// toRouteCandidateView は domain の RouteCandidate を公開 DTO へ変換する。
func toRouteCandidateView(c domain.RouteCandidate) RouteCandidateView {
	legs := c.Legs()
	legViews := make([]LegView, len(legs))
	for i, l := range legs {
		legViews[i] = LegView{
			VoyageNumber:   l.VoyageNumber().Value(),
			LoadUnLocode:   l.Load().UnLocode(),
			UnloadUnLocode: l.Unload().UnLocode(),
			LoadTime:       l.LoadTime(),
			UnloadTime:     l.UnloadTime(),
		}
	}
	waypoints := c.Waypoints()
	wpStrs := make([]string, len(waypoints))
	for i, w := range waypoints {
		wpStrs[i] = w.UnLocode()
	}
	return RouteCandidateView{
		Legs:          legViews,
		TransitDays:   c.TransitDays(),
		EstimatedCost: c.EstimatedCost(),
		Waypoints:     wpStrs,
	}
}
