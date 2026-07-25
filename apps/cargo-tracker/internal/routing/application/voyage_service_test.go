package application_test

import (
	"context"
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type stubVoyageRepo struct {
	saved   *domain.Voyage
	updated *domain.Voyage
	exists  bool
	stored  map[string]*domain.Voyage
}

func newStubRepo() *stubVoyageRepo { return &stubVoyageRepo{stored: map[string]*domain.Voyage{}} }

func (s *stubVoyageRepo) Save(_ context.Context, v *domain.Voyage) error {
	s.saved = v
	s.stored[v.VoyageNumber().Value()] = v
	return nil
}
func (s *stubVoyageRepo) Update(_ context.Context, v *domain.Voyage) error {
	s.updated = v
	s.stored[v.VoyageNumber().Value()] = v
	return nil
}
func (s *stubVoyageRepo) Exists(_ context.Context, vn domain.VoyageNumber) (bool, error) {
	_, ok := s.stored[vn.Value()]
	return s.exists || ok, nil
}
func (s *stubVoyageRepo) FindByNumber(_ context.Context, vn domain.VoyageNumber) (*domain.Voyage, error) {
	v, ok := s.stored[vn.Value()]
	if !ok {
		return nil, application.ErrVoyageNotFound
	}
	return v, nil
}
func (s *stubVoyageRepo) ListAll(_ context.Context) ([]*domain.Voyage, error) {
	out := make([]*domain.Voyage, 0, len(s.stored))
	for _, v := range s.stored {
		out = append(out, v)
	}
	return out, nil
}

func baseVoyageCmd() application.RegisterVoyageCommand {
	return application.RegisterVoyageCommand{
		VoyageNumber:        "V0001",
		VesselName:          "Ever Given",
		Carrier:             "Evergreen",
		SupportedCargoTypes: []string{"GENERAL", "REFRIGERATED"},
		Movements: []application.MovementInput{
			{DepartureUnLocode: "JPTYO", ArrivalUnLocode: "SGSIN", DepartureTime: time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC), ArrivalTime: time.Date(2026, 9, 5, 0, 0, 0, 0, time.UTC), Seq: 1},
			{DepartureUnLocode: "SGSIN", ArrivalUnLocode: "USLAX", DepartureTime: time.Date(2026, 9, 6, 0, 0, 0, 0, time.UTC), ArrivalTime: time.Date(2026, 9, 12, 0, 0, 0, 0, time.UTC), Seq: 2},
		},
	}
}

func TestRegisterVoyageService(t *testing.T) {
	t.Run("航海を登録できる", func(t *testing.T) {
		repo := newStubRepo()
		svc := application.NewRegisterVoyageService(repo)
		err := svc.Register(context.Background(), baseVoyageCmd())
		require.NoError(t, err)
		require.NotNil(t, repo.saved)
		assert.Equal(t, "V0001", repo.saved.VoyageNumber().Value())
	})
	t.Run("同一航海番号は重複エラー", func(t *testing.T) {
		repo := newStubRepo()
		repo.exists = true
		svc := application.NewRegisterVoyageService(repo)
		err := svc.Register(context.Background(), baseVoyageCmd())
		require.ErrorIs(t, err, application.ErrVoyageAlreadyExists)
	})
	t.Run("出発が到着より後は不正", func(t *testing.T) {
		repo := newStubRepo()
		svc := application.NewRegisterVoyageService(repo)
		cmd := baseVoyageCmd()
		cmd.Movements[0].DepartureTime, cmd.Movements[0].ArrivalTime = cmd.Movements[0].ArrivalTime, cmd.Movements[0].DepartureTime
		err := svc.Register(context.Background(), cmd)
		require.Error(t, err)
	})
	t.Run("不正な貨物種別はエラー", func(t *testing.T) {
		repo := newStubRepo()
		svc := application.NewRegisterVoyageService(repo)
		cmd := baseVoyageCmd()
		cmd.SupportedCargoTypes = []string{"XXX"}
		err := svc.Register(context.Background(), cmd)
		require.Error(t, err)
	})
}

func TestSearchVoyageService(t *testing.T) {
	repo := newStubRepo()
	require.NoError(t, application.NewRegisterVoyageService(repo).Register(context.Background(), baseVoyageCmd()))
	// 冷凍非対応の別航海
	cmd2 := baseVoyageCmd()
	cmd2.VoyageNumber = "V0002"
	cmd2.SupportedCargoTypes = []string{"GENERAL"}
	require.NoError(t, application.NewRegisterVoyageService(repo).Register(context.Background(), cmd2))

	svc := application.NewSearchVoyageService(repo)

	t.Run("出発地・目的地で絞り込める", func(t *testing.T) {
		res, err := svc.Search(context.Background(), application.SearchVoyageCriteria{OriginUnLocode: "JPTYO", DestinationUnLocode: "USLAX"})
		require.NoError(t, err)
		assert.Len(t, res, 2)
	})
	t.Run("冷凍貨物は対応航海のみ", func(t *testing.T) {
		res, err := svc.Search(context.Background(), application.SearchVoyageCriteria{CargoType: "REFRIGERATED"})
		require.NoError(t, err)
		require.Len(t, res, 1)
		assert.Equal(t, "V0001", res[0].VoyageNumber)
	})
	t.Run("該当なしは空", func(t *testing.T) {
		res, err := svc.Search(context.Background(), application.SearchVoyageCriteria{OriginUnLocode: "DEHAM"})
		require.NoError(t, err)
		assert.Empty(t, res)
	})
}
