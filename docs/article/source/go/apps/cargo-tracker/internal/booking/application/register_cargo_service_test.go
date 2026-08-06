package application_test

import (
	"context"
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type stubCargoRepo struct{ saved *domain.Cargo }

func (s *stubCargoRepo) Save(_ context.Context, c *domain.Cargo) error { s.saved = c; return nil }

type stubShipperChecker struct{ exists bool }

func (s stubShipperChecker) Exists(_ context.Context, _ shared.ShipperCode) (bool, error) {
	return s.exists, nil
}

type stubIDGen struct{ id string }

func (s stubIDGen) Generate() string { return s.id }

type stubPublisher struct{ events []string }

func (s *stubPublisher) Publish(_ context.Context, name string, _ any) error {
	s.events = append(s.events, name)
	return nil
}

func baseCmd() application.RegisterCargoCommand {
	return application.RegisterCargoCommand{
		ShipperCode:     "SHP-00000001",
		OriginUnLocode:  "JPTYO",
		DestUnLocode:    "DEHAM",
		ArrivalDeadline: time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC),
		CargoType:       "GENERAL",
		WeightKg:        1200.5,
	}
}

func TestRegisterCargoService_Success(t *testing.T) {
	repo := &stubCargoRepo{}
	pub := &stubPublisher{}
	svc := application.NewRegisterCargoService(repo, stubShipperChecker{exists: true}, stubIDGen{id: "abcdef12-3456-7890-abcd-ef1234567890"}, pub)

	id, err := svc.Register(context.Background(), baseCmd())

	require.NoError(t, err)
	assert.Equal(t, "BKG-ABCDEF12", id.Value())
	require.NotNil(t, repo.saved)
	assert.Equal(t, domain.BookingStatusPreliminary, repo.saved.Status())
	assert.Contains(t, pub.events, "CargoBooked")
}

func TestRegisterCargoService_ShipperNotFound(t *testing.T) {
	svc := application.NewRegisterCargoService(&stubCargoRepo{}, stubShipperChecker{exists: false}, stubIDGen{id: "x"}, &stubPublisher{})

	_, err := svc.Register(context.Background(), baseCmd())

	require.ErrorIs(t, err, application.ErrShipperNotFound)
}

func newSvc() *application.RegisterCargoService {
	return application.NewRegisterCargoService(&stubCargoRepo{}, stubShipperChecker{exists: true}, stubIDGen{id: "abcdef12-3456-7890-abcd-ef1234567890"}, &stubPublisher{})
}

// US05: 危険物予約は危険物申告付きで登録できる。
func TestRegisterCargoService_Hazardous(t *testing.T) {
	cmd := baseCmd()
	cmd.CargoType = "HAZARDOUS"
	cmd.HazardClass = "3"
	cmd.UNNumber = "UN1203"
	cmd.ProperShippingName = "Gasoline"

	_, err := newSvc().Register(context.Background(), cmd)

	require.NoError(t, err)
}

// US05: 冷凍予約は温度条件付きで登録できる。
func TestRegisterCargoService_Refrigerated(t *testing.T) {
	minT, maxT := -20.0, -5.0
	cmd := baseCmd()
	cmd.CargoType = "REFRIGERATED"
	cmd.MinTemperature = &minT
	cmd.MaxTemperature = &maxT
	cmd.TemperatureUnit = "CELSIUS"

	_, err := newSvc().Register(context.Background(), cmd)

	require.NoError(t, err)
}

// US05 異常系: 冷凍で温度未指定はエラー。
func TestRegisterCargoService_RefrigeratedMissingTemp(t *testing.T) {
	cmd := baseCmd()
	cmd.CargoType = "REFRIGERATED"

	_, err := newSvc().Register(context.Background(), cmd)

	require.ErrorIs(t, err, domain.ErrTemperatureReqRequired)
}

// US05 異常系: 温度単位が不正はエラー。
func TestRegisterCargoService_InvalidTempUnit(t *testing.T) {
	minT, maxT := -20.0, -5.0
	cmd := baseCmd()
	cmd.CargoType = "REFRIGERATED"
	cmd.MinTemperature = &minT
	cmd.MaxTemperature = &maxT
	cmd.TemperatureUnit = "KELVIN"

	_, err := newSvc().Register(context.Background(), cmd)

	require.ErrorIs(t, err, domain.ErrUnknownTemperatureUnit)
}

// US05 異常系: 危険物で申告未入力はエラー。
func TestRegisterCargoService_HazardousMissingDecl(t *testing.T) {
	cmd := baseCmd()
	cmd.CargoType = "HAZARDOUS"

	_, err := newSvc().Register(context.Background(), cmd)

	require.ErrorIs(t, err, domain.ErrEmptyHazardClass)
}

// 異常系: 不正な貨物種別はエラー。
func TestRegisterCargoService_InvalidCargoType(t *testing.T) {
	cmd := baseCmd()
	cmd.CargoType = "XXX"

	_, err := newSvc().Register(context.Background(), cmd)

	require.ErrorIs(t, err, domain.ErrUnknownCargoType)
}
