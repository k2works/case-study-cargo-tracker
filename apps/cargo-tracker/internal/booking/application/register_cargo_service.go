package application

import (
	"context"
	"errors"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ErrShipperNotFound は指定荷主が存在しない場合に返される。
var ErrShipperNotFound = errors.New("shipper not found")

// RegisterCargoCommand は貨物予約登録のコマンド。
// 荷主参照は業務識別子 ShipperCode で受け取る（ADR-0005）。
type RegisterCargoCommand struct {
	ShipperCode     string
	OriginUnLocode  string
	DestUnLocode    string
	ArrivalDeadline time.Time
	CargoType       string
	WeightKg        float64

	// US05: 特殊貨物情報（該当する貨物種別でのみ設定される）。
	HazardClass        string
	UNNumber           string
	ProperShippingName string
	MinTemperature     *float64
	MaxTemperature     *float64
	TemperatureUnit    string
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
	shipperCode, err := shared.NewShipperCode(cmd.ShipperCode)
	if err != nil {
		return domain.BookingId{}, err
	}
	if err := s.ensureShipperExists(ctx, shipperCode); err != nil {
		return domain.BookingId{}, err
	}

	cargo, err := s.buildCargo(shipperCode, cmd)
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

func (s *RegisterCargoService) ensureShipperExists(ctx context.Context, shipperCode shared.ShipperCode) error {
	exists, err := s.checker.Exists(ctx, shipperCode)
	if err != nil {
		return err
	}
	if !exists {
		return ErrShipperNotFound
	}
	return nil
}

func (s *RegisterCargoService) buildCargo(shipperCode shared.ShipperCode, cmd RegisterCargoCommand) (*domain.Cargo, error) {
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
	hazardous, temperature, err := buildSpecialCargo(cargoType, cmd)
	if err != nil {
		return nil, err
	}
	bookingID, err := domain.NewBookingId(generateBookingCode(s.idGen.Generate()))
	if err != nil {
		return nil, err
	}
	return domain.RegisterCargo(domain.CargoParams{
		BookingID:     bookingID,
		ShipperCode:   shipperCode,
		RouteSpec:     spec,
		CargoType:     cargoType,
		Weight:        weight,
		BookingAmount: domain.NewMoney(0, "JPY"),
		Hazardous:     hazardous,
		Temperature:   temperature,
	})
}

// buildSpecialCargo は貨物種別に応じて危険物申告・温度管理条件を構築する（US05）。
// 該当しない種別では nil を返し、集約側の整合検証に委ねる。
func buildSpecialCargo(cargoType domain.CargoType, cmd RegisterCargoCommand) (*domain.HazardousDeclaration, *domain.TemperatureRequirement, error) {
	switch cargoType {
	case domain.CargoTypeHazardous:
		haz, err := domain.NewHazardousDeclaration(cmd.HazardClass, cmd.UNNumber, cmd.ProperShippingName)
		if err != nil {
			return nil, nil, err
		}
		return &haz, nil, nil
	case domain.CargoTypeRefrigerated:
		if cmd.MinTemperature == nil || cmd.MaxTemperature == nil {
			return nil, nil, domain.ErrTemperatureReqRequired
		}
		unit, err := domain.ParseTemperatureUnit(cmd.TemperatureUnit)
		if err != nil {
			return nil, nil, err
		}
		temp, err := domain.NewTemperatureRequirement(*cmd.MinTemperature, *cmd.MaxTemperature, unit)
		if err != nil {
			return nil, nil, err
		}
		return nil, &temp, nil
	default:
		return nil, nil, nil
	}
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
	return shared.GenerateBusinessCode("BKG", rawUUID)
}
