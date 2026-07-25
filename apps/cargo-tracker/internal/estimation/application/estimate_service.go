package application

import (
	"context"
	"errors"
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
	repo  EstimateRepository
	idGen IDGenerator
}

// NewCreateEstimateService は CreateEstimateService を生成する。
func NewCreateEstimateService(repo EstimateRepository, idGen IDGenerator) *CreateEstimateService {
	return &CreateEstimateService{repo: repo, idGen: idGen}
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
	candidates, err := stubCandidates(cmd.WeightKg, cmd.ArrivalDeadline)
	if err != nil {
		return domain.EstimateId{}, err
	}
	estimate, err := domain.CreateEstimate(id, origin, dest, cmd.ArrivalDeadline, cargoType, cmd.WeightKg, candidates)
	if err != nil {
		return domain.EstimateId{}, err
	}
	if err := s.repo.Save(ctx, estimate); err != nil {
		return domain.EstimateId{}, err
	}
	return id, nil
}

// stubCandidates は簡易ルート候補を生成する（US01 スタブ・精緻化は US08）。
// 所要日数から到着日を概算し、希望期限に間に合う候補のみを採用する。
func stubCandidates(weightKg float64, deadline time.Time) ([]domain.RouteCandidate, error) {
	base := time.Now()
	presets := []struct {
		voyageNumber string
		transitDays  int
		unitCost     int64
	}{
		{"V-DIRECT", 12, 120},
		{"V-TRANSIT", 18, 90},
	}
	candidates := make([]domain.RouteCandidate, 0, len(presets))
	for _, p := range presets {
		if !deadline.IsZero() && base.AddDate(0, 0, p.transitDays).After(deadline) {
			continue
		}
		cost := int64(weightKg)*p.unitCost + 50000
		rc, err := domain.NewRouteCandidate(p.voyageNumber, p.transitDays, cost)
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
