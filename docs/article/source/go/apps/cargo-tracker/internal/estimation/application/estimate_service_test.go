package application_test

import (
	"context"
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// fixedNow はテストの決定性を確保する基準時刻。
var fixedNow = time.Date(2026, 8, 1, 0, 0, 0, 0, time.UTC)

func testClock() shared.Clock { return shared.FixedClock{Fixed: fixedNow} }

type stubRepo struct{ saved *domain.Estimate }

func (s *stubRepo) Save(_ context.Context, e *domain.Estimate) error { s.saved = e; return nil }
func (s *stubRepo) FindByEstimateId(_ context.Context, _ domain.EstimateId) (*domain.Estimate, error) {
	return s.saved, nil
}
func (s *stubRepo) ListAll(_ context.Context) ([]*domain.Estimate, error) {
	if s.saved == nil {
		return nil, nil
	}
	return []*domain.Estimate{s.saved}, nil
}

type stubIDGen struct{}

func (stubIDGen) Generate() string { return "11111111-2222-3333-4444-555555555555" }

// stubSearcher は経路探索 ACL のスタブ。deadline が近すぎる場合は候補ゼロを返す。
type stubSearcher struct{ empty bool }

func (s stubSearcher) Search(_ context.Context, spec application.RouteSearchSpec) ([]application.RouteCandidateResult, error) {
	if s.empty {
		return nil, nil
	}
	// 直行（12日）と経由（SGSIN・18日）の実候補を模す。期限内のもののみ返す。
	all := []application.RouteCandidateResult{
		{VoyageNumbers: []string{"V-DIRECT"}, TransitDays: 12, EstimatedCost: 120000},
		{VoyageNumbers: []string{"V-LEG1", "V-LEG2"}, TransitDays: 18, EstimatedCost: 190000, Waypoints: []string{"SGSIN"}},
	}
	if spec.ArrivalDeadline.IsZero() {
		return all, nil
	}
	out := make([]application.RouteCandidateResult, 0, len(all))
	for _, c := range all {
		if !fixedNow.AddDate(0, 0, c.TransitDays).After(spec.ArrivalDeadline) {
			out = append(out, c)
		}
	}
	return out, nil
}

func baseCmd() application.CreateEstimateCommand {
	return application.CreateEstimateCommand{
		OriginUnLocode:      "JPTYO",
		DestinationUnLocode: "USLAX",
		ArrivalDeadline:     fixedNow.AddDate(0, 2, 0),
		CargoType:           "GENERAL",
		WeightKg:            1200.5,
	}
}

func TestCreateEstimateService(t *testing.T) {
	t.Run("見積を作成しルート候補が付与される", func(t *testing.T) {
		repo := &stubRepo{}
		svc := application.NewCreateEstimateService(repo, stubIDGen{}, testClock(), stubSearcher{})
		id, err := svc.Create(context.Background(), baseCmd())
		require.NoError(t, err)
		assert.Equal(t, "11111111-2222-3333-4444-555555555555", id.Value())
		require.NotNil(t, repo.saved)
		assert.NotEmpty(t, repo.saved.Candidates())
		assert.Equal(t, domain.EstimateStatusCreated, repo.saved.Status())
	})
	t.Run("希望期限に間に合うルートがなければエラー", func(t *testing.T) {
		repo := &stubRepo{}
		svc := application.NewCreateEstimateService(repo, stubIDGen{}, testClock(), stubSearcher{})
		cmd := baseCmd()
		cmd.ArrivalDeadline = fixedNow.AddDate(0, 0, 1) // 1日後は全候補が間に合わない
		_, err := svc.Create(context.Background(), cmd)
		require.ErrorIs(t, err, application.ErrNoRouteInDeadline)
	})
	t.Run("不正な貨物種別はエラー", func(t *testing.T) {
		repo := &stubRepo{}
		svc := application.NewCreateEstimateService(repo, stubIDGen{}, testClock(), stubSearcher{})
		cmd := baseCmd()
		cmd.CargoType = "XXX"
		_, err := svc.Create(context.Background(), cmd)
		require.Error(t, err)
	})
	t.Run("重量が0以下はエラー", func(t *testing.T) {
		repo := &stubRepo{}
		svc := application.NewCreateEstimateService(repo, stubIDGen{}, testClock(), stubSearcher{})
		cmd := baseCmd()
		cmd.WeightKg = 0
		_, err := svc.Create(context.Background(), cmd)
		require.Error(t, err)
	})
}
