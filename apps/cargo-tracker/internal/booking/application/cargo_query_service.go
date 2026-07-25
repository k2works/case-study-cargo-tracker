package application

import "context"

// CargoListItem は貨物予約一覧の表示用 DTO（読み取りモデル）。
type CargoListItem struct {
	BookingID       string
	ShipperCode     string
	Origin          string
	Destination     string
	CargoType       string
	Status          string
	StatusJa        string
	RoutingStatus   string
	RoutingStatusJa string
}

// CargoQueryPort は貨物予約の読み取り用出力ポート。
// CQRS のクエリ側として infrastructure が sqlc で実装する。
type CargoQueryPort interface {
	ListCargos(ctx context.Context) ([]CargoListItem, error)
}

// CargoQueryService は貨物予約の照会ユースケースを提供する。
type CargoQueryService struct {
	port CargoQueryPort
}

// NewCargoQueryService は CargoQueryService を生成する。
func NewCargoQueryService(port CargoQueryPort) *CargoQueryService {
	return &CargoQueryService{port: port}
}

// List は登録済み貨物予約の一覧を返す。
func (s *CargoQueryService) List(ctx context.Context) ([]CargoListItem, error) {
	return s.port.ListCargos(ctx)
}
