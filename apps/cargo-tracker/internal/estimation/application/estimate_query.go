package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/domain"
)

// RouteCandidateView はルート候補の表示用 DTO。
type RouteCandidateView struct {
	VoyageNumber  string
	TransitDays   int
	EstimatedCost int64
}

// EstimateView は見積一覧・詳細の表示用 DTO。
type EstimateView struct {
	EstimateId  string
	Origin      string
	Destination string
	CargoType   string
	WeightKg    float64
	Status      string
	StatusJa    string
	Candidates  []RouteCandidateView
}

// EstimateQueryService は見積の照会ユースケース。
type EstimateQueryService struct {
	repo EstimateRepository
}

// NewEstimateQueryService は EstimateQueryService を生成する。
func NewEstimateQueryService(repo EstimateRepository) *EstimateQueryService {
	return &EstimateQueryService{repo: repo}
}

// List は全見積を表示用 DTO で返す。
func (s *EstimateQueryService) List(ctx context.Context) ([]EstimateView, error) {
	estimates, err := s.repo.ListAll(ctx)
	if err != nil {
		return nil, err
	}
	views := make([]EstimateView, 0, len(estimates))
	for _, e := range estimates {
		views = append(views, toEstimateView(e))
	}
	return views, nil
}

// Find は見積 ID で見積詳細を返す。
func (s *EstimateQueryService) Find(ctx context.Context, estimateID string) (EstimateView, error) {
	id, err := domain.NewEstimateId(estimateID)
	if err != nil {
		return EstimateView{}, err
	}
	e, err := s.repo.FindByEstimateId(ctx, id)
	if err != nil {
		return EstimateView{}, err
	}
	return toEstimateView(e), nil
}

// toEstimateView は Estimate 集約を表示用 DTO へ変換する。
func toEstimateView(e *domain.Estimate) EstimateView {
	cands := e.Candidates()
	cvs := make([]RouteCandidateView, 0, len(cands))
	for _, c := range cands {
		cvs = append(cvs, RouteCandidateView{VoyageNumber: c.VoyageNumber(), TransitDays: c.TransitDays(), EstimatedCost: c.EstimatedCost()})
	}
	return EstimateView{
		EstimateId:  e.EstimateId().Value(),
		Origin:      e.Origin().UnLocode(),
		Destination: e.Destination().UnLocode(),
		CargoType:   string(e.CargoType()),
		WeightKg:    e.WeightKg(),
		Status:      string(e.Status()),
		StatusJa:    e.Status().Ja(),
		Candidates:  cvs,
	}
}
