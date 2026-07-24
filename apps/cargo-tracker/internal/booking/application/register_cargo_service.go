package application

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ErrShipperNotFound は指定荷主が存在しない場合に返される。
var ErrShipperNotFound = errors.New("shipper not found")

// RegisterCargoCommand は貨物予約登録のコマンド。
type RegisterCargoCommand struct {
	ShipperID       string
	OriginUnLocode  string
	DestUnLocode    string
	ArrivalDeadline time.Time
	CargoType       string
	WeightKg        float64
}

// RegisterCargoService は貨物予約登録ユースケースを実行するコマンドサービス。
type RegisterCargoService struct {
	repo    CargoRepository
	checker ShipperExistenceChecker
	idGen   IDGenerator
	pub     EventPublisher
}

// NewRegisterCargoService は RegisterCargoService を生成する。
func NewRegisterCargoService(repo CargoRepository, checker ShipperExistenceChecker, idGen IDGenerator, pub EventPublisher) *RegisterCargoService {
	return &RegisterCargoService{repo: repo, checker: checker, idGen: idGen, pub: pub}
}

// Register は貨物予約を新規登録し、発行された BookingId を返す。
func (s *RegisterCargoService) Register(ctx context.Context, cmd RegisterCargoCommand) (domain.BookingId, error) {
	shipperID, err := shared.NewShipperId(cmd.ShipperID)
	if err != nil {
		return domain.BookingId{}, err
	}
	if err := s.ensureShipperExists(ctx, shipperID); err != nil {
		return domain.BookingId{}, err
	}

	cargo, err := s.buildCargo(shipperID, cmd)
	if err != nil {
		return domain.BookingId{}, err
	}

	if err := s.repo.Save(ctx, cargo); err != nil {
		return domain.BookingId{}, err
	}

	// 経路設計者への通知（CargoBooked）。購読側 routing は Phase 2 で実装。
	if err := s.pub.Publish(ctx, "CargoBooked", map[string]string{"bookingId": cargo.BookingID().Value()}); err != nil {
		return domain.BookingId{}, err
	}
	return cargo.BookingID(), nil
}

func (s *RegisterCargoService) ensureShipperExists(ctx context.Context, shipperID shared.ShipperId) error {
	exists, err := s.checker.Exists(ctx, shipperID)
	if err != nil {
		return err
	}
	if !exists {
		return ErrShipperNotFound
	}
	return nil
}

func (s *RegisterCargoService) buildCargo(shipperID shared.ShipperId, cmd RegisterCargoCommand) (*domain.Cargo, error) {
	spec, err := buildRouteSpec(cmd)
	if err != nil {
		return nil, err
	}
	cargoType, err := domain.ParseCargoType(cmd.CargoType)
	if err != nil {
		return nil, err
	}
	weight, err := domain.NewWeight(cmd.WeightKg)
	if err != nil {
		return nil, err
	}
	bookingID, err := domain.NewBookingId(generateBookingCode(s.idGen.Generate()))
	if err != nil {
		return nil, err
	}
	return domain.RegisterCargo(bookingID, shipperID, spec, cargoType, weight, domain.NewMoney(0, "JPY"))
}

func buildRouteSpec(cmd RegisterCargoCommand) (domain.RouteSpecification, error) {
	origin, err := shared.NewLocation(cmd.OriginUnLocode)
	if err != nil {
		return domain.RouteSpecification{}, err
	}
	dest, err := shared.NewLocation(cmd.DestUnLocode)
	if err != nil {
		return domain.RouteSpecification{}, err
	}
	return domain.NewRouteSpecification(origin, dest, cmd.ArrivalDeadline)
}

// generateBookingCode は生の UUID から BKG- プレフィックス + 先頭 8 文字（大文字）の予約番号を生成する。
func generateBookingCode(rawUUID string) string {
	prefix := strings.ToUpper(strings.ReplaceAll(rawUUID, "-", ""))
	if len(prefix) > 8 {
		prefix = prefix[:8]
	}
	return "BKG-" + prefix
}
