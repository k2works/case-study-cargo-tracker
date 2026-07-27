// Package application は Discount Policy Context のユースケースとポートを提供する。
package application

import (
	"context"
	"errors"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/domain"
)

// ErrPolicyNotFound は指定 ID の割引ポリシーが存在しないことを示す。
var ErrPolicyNotFound = errors.New("割引ポリシーが見つかりません")

// DiscountPolicyRepository は割引ポリシー集約の永続化ポート（出力ポート）。
type DiscountPolicyRepository interface {
	Save(ctx context.Context, policy *domain.DiscountPolicy) error
	FindByID(ctx context.Context, id string) (*domain.DiscountPolicy, error)
	List(ctx context.Context) ([]*domain.DiscountPolicy, error)
}

// IDGenerator は割引ポリシー ID を採番する出力ポート。
type IDGenerator interface {
	NewID() string
}
