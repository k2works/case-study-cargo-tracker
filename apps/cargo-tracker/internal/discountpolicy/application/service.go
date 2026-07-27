package application

import (
	"context"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/domain"
)

// RegisterCommand は割引ポリシー登録の入力（割引率は小数 0.0〜0.30）。
type RegisterCommand struct {
	PolicyType  string
	Rate        float64
	ValidFrom   time.Time
	ValidUntil  *time.Time
	Description string
}

// UpdateCommand は割引ポリシー編集の入力。
type UpdateCommand struct {
	PolicyType  string
	Rate        float64
	ValidFrom   time.Time
	ValidUntil  *time.Time
	Description string
}

// DiscountPolicyService は割引ポリシーの登録・編集ユースケース（US-ADM-01）。
type DiscountPolicyService struct {
	repo DiscountPolicyRepository
	ids  IDGenerator
}

// NewDiscountPolicyService は DiscountPolicyService を生成する。
func NewDiscountPolicyService(repo DiscountPolicyRepository, ids IDGenerator) *DiscountPolicyService {
	return &DiscountPolicyService{repo: repo, ids: ids}
}

// Register は新規割引ポリシーを登録し、採番した ID を返す。
func (s *DiscountPolicyService) Register(ctx context.Context, cmd RegisterCommand) (string, error) {
	id := s.ids.NewID()
	policy, err := domain.NewDiscountPolicy(domain.NewDiscountPolicyParams{
		ID:          id,
		PolicyType:  domain.PolicyType(cmd.PolicyType),
		Rate:        cmd.Rate,
		ValidFrom:   cmd.ValidFrom,
		ValidUntil:  cmd.ValidUntil,
		Description: cmd.Description,
	})
	if err != nil {
		return "", err
	}
	if err := s.repo.Save(ctx, policy); err != nil {
		return "", err
	}
	return id, nil
}

// Update は既存割引ポリシーを更新する。存在しなければ ErrPolicyNotFound。
func (s *DiscountPolicyService) Update(ctx context.Context, id string, cmd UpdateCommand) error {
	policy, err := s.repo.FindByID(ctx, id)
	if err != nil {
		return err
	}
	if err := policy.Update(domain.UpdateDiscountPolicyParams{
		PolicyType:  domain.PolicyType(cmd.PolicyType),
		Rate:        cmd.Rate,
		ValidFrom:   cmd.ValidFrom,
		ValidUntil:  cmd.ValidUntil,
		Description: cmd.Description,
	}); err != nil {
		return err
	}
	return s.repo.Save(ctx, policy)
}
