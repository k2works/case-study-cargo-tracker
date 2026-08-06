// Package application は Shipper Context のユースケース（コマンド/クエリサービス）とポートを提供する。
package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/domain"
)

// ShipperRepository は荷主集約の永続化ポート（出力ポート）。
// 実装は infrastructure 層のアダプターが担う。
type ShipperRepository interface {
	Save(ctx context.Context, shipper *domain.Shipper) error
	ExistsByEmail(ctx context.Context, email domain.Email) (bool, error)
}

// IDGenerator は一意 ID（UUID）を採番する横断ポート。
// テストでは決定的な値を返すスタブに差し替える。
type IDGenerator interface {
	Generate() string
}
