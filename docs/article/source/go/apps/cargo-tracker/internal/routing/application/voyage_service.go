package application

import (
	"context"
	"errors"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// アプリケーションエラー。
var (
	// ErrVoyageAlreadyExists は同一航海番号が既に存在する場合に返される。
	ErrVoyageAlreadyExists = errors.New("voyage already exists")
	// ErrVoyageNotFound は指定航海が存在しない場合に返される（domain のセンチネル相当）。
	ErrVoyageNotFound = errors.New("voyage not found")
)

// MovementInput は運送区間の入力。
type MovementInput struct {
	DepartureUnLocode string
	ArrivalUnLocode   string
	DepartureTime     time.Time
	ArrivalTime       time.Time
	Seq               int
}

// RegisterVoyageCommand は航海スケジュール登録のコマンド（US24）。
type RegisterVoyageCommand struct {
	VoyageNumber        string
	VesselName          string
	Carrier             string
	SupportedCargoTypes []string
	Movements           []MovementInput
}

// RegisterVoyageService は航海スケジュール登録ユースケース。
type RegisterVoyageService struct {
	repo VoyageRepository
}

// NewRegisterVoyageService は RegisterVoyageService を生成する。
func NewRegisterVoyageService(repo VoyageRepository) *RegisterVoyageService {
	return &RegisterVoyageService{repo: repo}
}

// Register は航海スケジュールを新規登録する。
func (s *RegisterVoyageService) Register(ctx context.Context, cmd RegisterVoyageCommand) error {
	vn, err := domain.NewVoyageNumber(cmd.VoyageNumber)
	if err != nil {
		return err
	}
	exists, err := s.repo.Exists(ctx, vn)
	if err != nil {
		return err
	}
	if exists {
		return ErrVoyageAlreadyExists
	}
	voyage, err := buildVoyage(vn, cmd.VesselName, cmd.Carrier, cmd.SupportedCargoTypes, cmd.Movements)
	if err != nil {
		return err
	}
	return s.repo.Save(ctx, voyage)
}

// UpdateVoyageCommand は航海スケジュール更新のコマンド（US25）。
type UpdateVoyageCommand struct {
	VoyageNumber        string
	VesselName          string
	Carrier             string
	SupportedCargoTypes []string
	Movements           []MovementInput
}

// UpdateVoyageService は航海スケジュール更新ユースケース。
type UpdateVoyageService struct {
	repo VoyageRepository
}

// NewUpdateVoyageService は UpdateVoyageService を生成する。
func NewUpdateVoyageService(repo VoyageRepository) *UpdateVoyageService {
	return &UpdateVoyageService{repo: repo}
}

// Update は既存航海スケジュールを上書き更新する。
func (s *UpdateVoyageService) Update(ctx context.Context, cmd UpdateVoyageCommand) error {
	vn, err := domain.NewVoyageNumber(cmd.VoyageNumber)
	if err != nil {
		return err
	}
	voyage, err := s.repo.FindByNumber(ctx, vn)
	if err != nil {
		return err
	}
	schedule, cts, err := buildScheduleAndTypes(cmd.SupportedCargoTypes, cmd.Movements)
	if err != nil {
		return err
	}
	if err := voyage.UpdateSchedule(cmd.VesselName, cmd.Carrier, schedule, cts); err != nil {
		return err
	}
	return s.repo.Update(ctx, voyage)
}

// buildVoyage はコマンドから Voyage 集約を組み立てる。
func buildVoyage(vn domain.VoyageNumber, vesselName, carrier string, cargoTypes []string, movements []MovementInput) (*domain.Voyage, error) {
	schedule, cts, err := buildScheduleAndTypes(cargoTypes, movements)
	if err != nil {
		return nil, err
	}
	return domain.RegisterVoyage(vn, vesselName, carrier, schedule, cts)
}

// buildScheduleAndTypes は入力からスケジュールと対応貨物種別を構築する。
func buildScheduleAndTypes(cargoTypes []string, movements []MovementInput) (domain.Schedule, []shared.CargoType, error) {
	cms := make([]domain.CarrierMovement, 0, len(movements))
	for _, m := range movements {
		dep, err := shared.NewLocation(m.DepartureUnLocode)
		if err != nil {
			return domain.Schedule{}, nil, err
		}
		arr, err := shared.NewLocation(m.ArrivalUnLocode)
		if err != nil {
			return domain.Schedule{}, nil, err
		}
		cm, err := domain.NewCarrierMovement(dep, arr, m.DepartureTime, m.ArrivalTime, m.Seq)
		if err != nil {
			return domain.Schedule{}, nil, err
		}
		cms = append(cms, cm)
	}
	schedule, err := domain.NewSchedule(cms)
	if err != nil {
		return domain.Schedule{}, nil, err
	}
	cts := make([]shared.CargoType, 0, len(cargoTypes))
	for _, s := range cargoTypes {
		ct, err := shared.ParseCargoType(s)
		if err != nil {
			return domain.Schedule{}, nil, err
		}
		cts = append(cts, ct)
	}
	return schedule, cts, nil
}
