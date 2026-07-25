package application

import (
	"context"
	"errors"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/domain"
)

// ErrEmailAlreadyRegistered は同一メールアドレスが既に登録済みの場合に返される。
var ErrEmailAlreadyRegistered = errors.New("email already registered")

// ErrCorporateContractRequired は法人荷主に契約情報が欠けている場合に返される。
var ErrCorporateContractRequired = errors.New("corporate shipper requires contract number and discount rate")

// RegisterShipperCommand は荷主登録のコマンド。
type RegisterShipperCommand struct {
	Name           string
	Email          string
	ShipperType    string
	Phone          *string
	Address        *string
	ContractNumber *string
	DiscountRate   *float64
}

// RegisterShipperService は荷主登録ユースケースを実行するコマンドサービス。
type RegisterShipperService struct {
	repo  ShipperRepository
	idGen IDGenerator
}

// NewRegisterShipperService は RegisterShipperService を生成する。
func NewRegisterShipperService(repo ShipperRepository, idGen IDGenerator) *RegisterShipperService {
	return &RegisterShipperService{repo: repo, idGen: idGen}
}

// Register は荷主を新規登録し、発行された ShipperId を返す。
func (s *RegisterShipperService) Register(ctx context.Context, cmd RegisterShipperCommand) (domain.ShipperId, error) {
	email, err := domain.NewEmail(cmd.Email)
	if err != nil {
		return domain.ShipperId{}, err
	}

	exists, err := s.repo.ExistsByEmail(ctx, email)
	if err != nil {
		return domain.ShipperId{}, err
	}
	if exists {
		return domain.ShipperId{}, ErrEmailAlreadyRegistered
	}

	name, err := domain.NewShipperName(cmd.Name)
	if err != nil {
		return domain.ShipperId{}, err
	}

	shipperType, err := domain.ParseShipperType(cmd.ShipperType)
	if err != nil {
		return domain.ShipperId{}, err
	}

	rawUUID := s.idGen.Generate()
	id, err := domain.NewShipperId(rawUUID)
	if err != nil {
		return domain.ShipperId{}, err
	}
	code := domain.GenerateShipperCode(rawUUID)

	var shipper *domain.Shipper
	if shipperType == domain.ShipperTypeCorporate {
		shipper, err = s.buildCorporate(id, code, name, email, cmd)
	} else {
		shipper, err = domain.RegisterIndividualShipper(id, code, name, email)
	}
	if err != nil {
		return domain.ShipperId{}, err
	}

	s.applyOptional(shipper, cmd)

	if err := s.repo.Save(ctx, shipper); err != nil {
		return domain.ShipperId{}, err
	}
	return id, nil
}

func (s *RegisterShipperService) buildCorporate(
	id domain.ShipperId, code domain.ShipperCode, name domain.ShipperName, email domain.Email, cmd RegisterShipperCommand,
) (*domain.Shipper, error) {
	if cmd.ContractNumber == nil || cmd.DiscountRate == nil {
		return nil, ErrCorporateContractRequired
	}
	contract, err := domain.NewContractNumber(*cmd.ContractNumber)
	if err != nil {
		return nil, err
	}
	rate, err := domain.NewDiscountRate(*cmd.DiscountRate)
	if err != nil {
		return nil, err
	}
	return domain.RegisterCorporateShipper(id, code, name, email, contract, rate)
}

func (s *RegisterShipperService) applyOptional(shipper *domain.Shipper, cmd RegisterShipperCommand) {
	if cmd.Phone != nil {
		shipper.SetPhone(domain.NewPhone(*cmd.Phone))
	}
	if cmd.Address != nil {
		if addr, err := domain.NewAddress(*cmd.Address); err == nil {
			shipper.SetAddress(addr)
		}
	}
}
