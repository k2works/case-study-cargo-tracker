package application_test

import (
	"context"
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type memRepo struct {
	items map[string]*domain.DiscountPolicy
}

func newMemRepo() *memRepo { return &memRepo{items: map[string]*domain.DiscountPolicy{}} }

func (m *memRepo) Save(_ context.Context, p *domain.DiscountPolicy) error {
	m.items[p.ID()] = p
	return nil
}
func (m *memRepo) FindByID(_ context.Context, id string) (*domain.DiscountPolicy, error) {
	p, ok := m.items[id]
	if !ok {
		return nil, application.ErrPolicyNotFound
	}
	return p, nil
}
func (m *memRepo) List(_ context.Context) ([]*domain.DiscountPolicy, error) {
	out := make([]*domain.DiscountPolicy, 0, len(m.items))
	for _, p := range m.items {
		out = append(out, p)
	}
	return out, nil
}

type fixedIDGen struct{ id string }

func (f fixedIDGen) NewID() string { return f.id }

func TestService_Register(t *testing.T) {
	repo := newMemRepo()
	svc := application.NewDiscountPolicyService(repo, fixedIDGen{id: "POL-1"})
	id, err := svc.Register(context.Background(), application.RegisterCommand{
		PolicyType: "CORPORATE_STANDARD", Rate: 0.15,
		ValidFrom: time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC), Description: "法人標準",
	})
	require.NoError(t, err)
	assert.Equal(t, "POL-1", id)

	q := application.NewDiscountPolicyQueryService(repo)
	view, err := q.Find(context.Background(), "POL-1")
	require.NoError(t, err)
	assert.InDelta(t, 15.0, view.RatePercent, 1e-9)
	assert.Equal(t, "法人標準割引", view.PolicyTypeJa)
	assert.Equal(t, "無期限", view.ValidUntil)
}

func TestService_Register_RejectsInvalidRate(t *testing.T) {
	repo := newMemRepo()
	svc := application.NewDiscountPolicyService(repo, fixedIDGen{id: "POL-1"})
	_, err := svc.Register(context.Background(), application.RegisterCommand{
		PolicyType: "SEASONAL", Rate: 0.5,
		ValidFrom: time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC),
	})
	assert.ErrorIs(t, err, domain.ErrInvalidRate)
}

func TestService_Update(t *testing.T) {
	repo := newMemRepo()
	svc := application.NewDiscountPolicyService(repo, fixedIDGen{id: "POL-1"})
	_, err := svc.Register(context.Background(), application.RegisterCommand{
		PolicyType: "CORPORATE_STANDARD", Rate: 0.15,
		ValidFrom: time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC),
	})
	require.NoError(t, err)

	err = svc.Update(context.Background(), "POL-1", application.UpdateCommand{
		PolicyType: "SEASONAL", Rate: 0.05,
		ValidFrom: time.Date(2026, 3, 1, 0, 0, 0, 0, time.UTC), Description: "春季",
	})
	require.NoError(t, err)

	q := application.NewDiscountPolicyQueryService(repo)
	view, _ := q.Find(context.Background(), "POL-1")
	assert.Equal(t, "シーズン割引", view.PolicyTypeJa)
	assert.InDelta(t, 5.0, view.RatePercent, 1e-9)
}

func TestService_Update_NotFound(t *testing.T) {
	repo := newMemRepo()
	svc := application.NewDiscountPolicyService(repo, fixedIDGen{id: "POL-1"})
	err := svc.Update(context.Background(), "MISSING", application.UpdateCommand{
		PolicyType: "NONE", Rate: 0, ValidFrom: time.Now(),
	})
	assert.ErrorIs(t, err, application.ErrPolicyNotFound)
}

func TestQuery_List(t *testing.T) {
	repo := newMemRepo()
	svc := application.NewDiscountPolicyService(repo, fixedIDGen{id: "POL-1"})
	_, _ = svc.Register(context.Background(), application.RegisterCommand{
		PolicyType: "VOLUME_DISCOUNT", Rate: 0.10,
		ValidFrom: time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC),
	})
	q := application.NewDiscountPolicyQueryService(repo)
	list, err := q.List(context.Background())
	require.NoError(t, err)
	require.Len(t, list, 1)
	assert.Equal(t, "ボリューム割引", list[0].PolicyTypeJa)
}
