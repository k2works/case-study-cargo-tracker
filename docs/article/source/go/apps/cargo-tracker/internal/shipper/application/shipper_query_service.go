package application

import "context"

// ShipperListItem は荷主一覧の表示用 DTO（読み取りモデル）。
type ShipperListItem struct {
	Code  string
	Name  string
	Type  string
	Email string
}

// ShipperQueryPort は荷主の読み取り用出力ポート。
// CQRS のクエリ側として infrastructure が sqlc で実装する。
type ShipperQueryPort interface {
	ListShippers(ctx context.Context) ([]ShipperListItem, error)
}

// ShipperQueryService は荷主の照会ユースケースを提供する。
type ShipperQueryService struct {
	port ShipperQueryPort
}

// NewShipperQueryService は ShipperQueryService を生成する。
func NewShipperQueryService(port ShipperQueryPort) *ShipperQueryService {
	return &ShipperQueryService{port: port}
}

// List は登録済み荷主の一覧を返す。
func (s *ShipperQueryService) List(ctx context.Context) ([]ShipperListItem, error) {
	return s.port.ListShippers(ctx)
}
