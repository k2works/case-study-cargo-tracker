package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/domain"
)

const dateFormat = "2006-01-02"

// PolicyView は割引ポリシーの表示モデル。
type PolicyView struct {
	ID           string
	PolicyType   string
	PolicyTypeJa string
	RatePercent  float64 // 表示用の百分率（例: 15.0）
	ValidFrom    string
	ValidUntil   string // 無期限は "無期限"
	Description  string
}

// DiscountPolicyQueryService は割引ポリシー照会ユースケース（読み取り最適化）。
type DiscountPolicyQueryService struct {
	repo DiscountPolicyRepository
}

// NewDiscountPolicyQueryService は DiscountPolicyQueryService を生成する。
func NewDiscountPolicyQueryService(repo DiscountPolicyRepository) *DiscountPolicyQueryService {
	return &DiscountPolicyQueryService{repo: repo}
}

// List は割引ポリシーの一覧を返す。
func (s *DiscountPolicyQueryService) List(ctx context.Context) ([]PolicyView, error) {
	policies, err := s.repo.List(ctx)
	if err != nil {
		return nil, err
	}
	views := make([]PolicyView, 0, len(policies))
	for _, p := range policies {
		views = append(views, toView(p))
	}
	return views, nil
}

// Find は ID 指定で割引ポリシーを返す。存在しなければ ErrPolicyNotFound。
func (s *DiscountPolicyQueryService) Find(ctx context.Context, id string) (PolicyView, error) {
	p, err := s.repo.FindByID(ctx, id)
	if err != nil {
		return PolicyView{}, err
	}
	return toView(p), nil
}

func toView(p *domain.DiscountPolicy) PolicyView {
	validUntil := "無期限"
	if p.ValidUntil() != nil {
		validUntil = p.ValidUntil().Format(dateFormat)
	}
	return PolicyView{
		ID:           p.ID(),
		PolicyType:   string(p.PolicyType()),
		PolicyTypeJa: p.PolicyType().Ja(),
		RatePercent:  p.Rate() * 100,
		ValidFrom:    p.ValidFrom().Format(dateFormat),
		ValidUntil:   validUntil,
		Description:  p.Description(),
	}
}
