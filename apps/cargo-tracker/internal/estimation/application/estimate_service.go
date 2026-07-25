package application

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ErrNoRouteInDeadline は希望期限に間に合うルートが存在しない場合に返される（US01）。
var ErrNoRouteInDeadline = errors.New("no route candidate meets the arrival deadline")

// CreateEstimateCommand は輸送見積作成のコマンド（US01）。
type CreateEstimateCommand struct {
	OriginUnLocode      string
	DestinationUnLocode string
	ArrivalDeadline     time.Time
	CargoType           string
	WeightKg            float64
}

// CreateEstimateService は輸送見積作成ユースケース。
type CreateEstimateService struct {
	repo     EstimateRepository
	idGen    IDGenerator
	clock    shared.Clock
	searcher RouteSearcher
}

// NewCreateEstimateService は CreateEstimateService を生成する。
// searcher は経路探索 ACL（T3）。実候補を航海スケジュールから算出する。
func NewCreateEstimateService(repo EstimateRepository, idGen IDGenerator, clock shared.Clock, searcher RouteSearcher) *CreateEstimateService {
	return &CreateEstimateService{repo: repo, idGen: idGen, clock: clock, searcher: searcher}
}

// Create は見積を作成し、発行された見積 ID を返す。
// ルート候補は航海スケジュールに基づく簡易算出（スタブ）。精緻化は US08（IT4）。
func (s *CreateEstimateService) Create(ctx context.Context, cmd CreateEstimateCommand) (domain.EstimateId, error) {
	origin, err := shared.NewLocation(cmd.OriginUnLocode)
	if err != nil {
		return domain.EstimateId{}, err
	}
	dest, err := shared.NewLocation(cmd.DestinationUnLocode)
	if err != nil {
		return domain.EstimateId{}, err
	}
	cargoType, err := shared.ParseCargoType(cmd.CargoType)
	if err != nil {
		return domain.EstimateId{}, err
	}
	id, err := domain.NewEstimateId(s.idGen.Generate())
	if err != nil {
		return domain.EstimateId{}, err
	}
	candidates, err := s.searchCandidates(ctx, cmd)
	if err != nil {
		return domain.EstimateId{}, err
	}
	estimate, err := domain.CreateEstimate(id, origin, dest, cmd.ArrivalDeadline, cargoType, cmd.WeightKg, candidates, s.clock.Now())
	if err != nil {
		return domain.EstimateId{}, err
	}
	if err := s.repo.Save(ctx, estimate); err != nil {
		return domain.EstimateId{}, err
	}
	return id, nil
}

// searchCandidates は経路探索 ACL から実ルート候補を算出する（T3・US08 の探索を利用）。
// 航海スケジュール上の接続を評価した候補を推奨順で受け取り、見積のルート候補へ写像する。
// 希望期限に間に合う候補が無ければ ErrNoRouteInDeadline を返す（候補ゼロ通知）。
func (s *CreateEstimateService) searchCandidates(ctx context.Context, cmd CreateEstimateCommand) ([]domain.RouteCandidate, error) {
	results, err := s.searcher.Search(ctx, RouteSearchSpec{
		OriginUnLocode:      cmd.OriginUnLocode,
		DestinationUnLocode: cmd.DestinationUnLocode,
		ArrivalDeadline:     cmd.ArrivalDeadline,
		CargoType:           cmd.CargoType,
	})
	if err != nil {
		return nil, err
	}
	candidates := make([]domain.RouteCandidate, 0, len(results))
	for _, r := range results {
		cost := r.EstimatedCost + int64(cmd.WeightKg)*90
		rc, err := domain.NewRouteCandidate(strings.Join(r.VoyageNumbers, "+"), r.TransitDays, cost, r.Waypoints)
		if err != nil {
			return nil, err
		}
		candidates = append(candidates, rc)
	}
	if len(candidates) == 0 {
		return nil, ErrNoRouteInDeadline
	}
	return candidates, nil
}
