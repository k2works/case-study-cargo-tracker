package domain_test

import (
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func newCargoFixture(t *testing.T, cargoType domain.CargoType, haz *domain.HazardousDeclaration, temp *domain.TemperatureRequirement) (*domain.Cargo, error) {
	t.Helper()
	shipperCode, _ := shared.NewShipperCode("SHP-00000001")
	bookingID, _ := domain.NewBookingId("BKG-0001")
	spec, _ := domain.NewRouteSpecification(mustLoc(t, "JPTYO"), mustLoc(t, "DEHAM"), time.Date(2026, 9, 1, 0, 0, 0, 0, time.UTC))
	weight, _ := domain.NewWeight(1000)
	return domain.RegisterCargo(bookingID, shipperCode, spec, cargoType, weight, domain.NewMoney(0, "JPY"), haz, temp)
}

// --- US05: HazardousDeclaration ---

func TestNewHazardousDeclaration(t *testing.T) {
	t.Run("すべて入力があれば生成できる", func(t *testing.T) {
		h, err := domain.NewHazardousDeclaration("3", "UN1203", "Gasoline")
		require.NoError(t, err)
		assert.Equal(t, "3", h.HazardClass())
		assert.Equal(t, "UN1203", h.UNNumber())
		assert.Equal(t, "Gasoline", h.ProperShippingName())
	})
	t.Run("危険物クラスが空はエラー", func(t *testing.T) {
		_, err := domain.NewHazardousDeclaration(" ", "UN1203", "Gasoline")
		require.ErrorIs(t, err, domain.ErrEmptyHazardClass)
	})
	t.Run("UN 番号が空はエラー", func(t *testing.T) {
		_, err := domain.NewHazardousDeclaration("3", "", "Gasoline")
		require.ErrorIs(t, err, domain.ErrEmptyUNNumber)
	})
	t.Run("正式輸送品名が空はエラー", func(t *testing.T) {
		_, err := domain.NewHazardousDeclaration("3", "UN1203", "")
		require.ErrorIs(t, err, domain.ErrEmptyProperShippingName)
	})
}

// --- US05: TemperatureRequirement ---

func TestNewTemperatureRequirement(t *testing.T) {
	t.Run("最低 <= 最高なら生成できる", func(t *testing.T) {
		temp, err := domain.NewTemperatureRequirement(-20, -5, domain.TemperatureUnitCelsius)
		require.NoError(t, err)
		assert.InDelta(t, -20.0, temp.MinTemperature(), 0.001)
		assert.InDelta(t, -5.0, temp.MaxTemperature(), 0.001)
		assert.Equal(t, domain.TemperatureUnitCelsius, temp.Unit())
	})
	t.Run("温度範囲が逆転しているとエラー", func(t *testing.T) {
		_, err := domain.NewTemperatureRequirement(5, -5, domain.TemperatureUnitCelsius)
		require.ErrorIs(t, err, domain.ErrTemperatureRangeInverted)
	})
	t.Run("未知の温度単位はエラー", func(t *testing.T) {
		_, err := domain.NewTemperatureRequirement(-20, -5, domain.TemperatureUnit("KELVIN"))
		require.ErrorIs(t, err, domain.ErrUnknownTemperatureUnit)
	})
}

// --- US05: 貨物種別と特殊貨物情報の整合 ---

func TestRegisterCargoSpecialValidation(t *testing.T) {
	haz, _ := domain.NewHazardousDeclaration("3", "UN1203", "Gasoline")
	temp, _ := domain.NewTemperatureRequirement(-20, -5, domain.TemperatureUnitCelsius)

	t.Run("危険物は危険物申告が必須", func(t *testing.T) {
		_, err := newCargoFixture(t, domain.CargoTypeHazardous, nil, nil)
		require.ErrorIs(t, err, domain.ErrHazardousDeclRequired)
	})
	t.Run("危険物申告付き危険物は登録できる", func(t *testing.T) {
		c, err := newCargoFixture(t, domain.CargoTypeHazardous, &haz, nil)
		require.NoError(t, err)
		require.NotNil(t, c.HazardousDeclaration())
		assert.Equal(t, "UN1203", c.HazardousDeclaration().UNNumber())
	})
	t.Run("冷凍貨物は温度条件が必須", func(t *testing.T) {
		_, err := newCargoFixture(t, domain.CargoTypeRefrigerated, nil, nil)
		require.ErrorIs(t, err, domain.ErrTemperatureReqRequired)
	})
	t.Run("温度条件付き冷凍貨物は登録できる", func(t *testing.T) {
		c, err := newCargoFixture(t, domain.CargoTypeRefrigerated, nil, &temp)
		require.NoError(t, err)
		require.NotNil(t, c.TemperatureRequirement())
	})
	t.Run("一般貨物に特殊情報を付けるとエラー", func(t *testing.T) {
		_, err := newCargoFixture(t, domain.CargoTypeGeneral, &haz, nil)
		require.ErrorIs(t, err, domain.ErrSpecialInfoNotAllowed)
	})
	t.Run("危険物に温度条件を付けるとエラー", func(t *testing.T) {
		_, err := newCargoFixture(t, domain.CargoTypeHazardous, &haz, &temp)
		require.ErrorIs(t, err, domain.ErrSpecialInfoNotAllowed)
	})
}

// --- US13: 状態遷移 ---

func TestCargoConfirm(t *testing.T) {
	t.Run("PRELIMINARY から確定できる", func(t *testing.T) {
		c, _ := newCargoFixture(t, domain.CargoTypeGeneral, nil, nil)
		require.NoError(t, c.Confirm())
		assert.Equal(t, domain.BookingStatusConfirmed, c.Status())
	})
	t.Run("確定済みを再度確定するとエラー", func(t *testing.T) {
		c, _ := newCargoFixture(t, domain.CargoTypeGeneral, nil, nil)
		require.NoError(t, c.Confirm())
		require.ErrorIs(t, c.Confirm(), domain.ErrInvalidStatusTransition)
	})
}

func TestCargoCancel(t *testing.T) {
	t.Run("PRELIMINARY からキャンセルできる", func(t *testing.T) {
		c, _ := newCargoFixture(t, domain.CargoTypeGeneral, nil, nil)
		require.NoError(t, c.Cancel())
		assert.Equal(t, domain.BookingStatusCancelled, c.Status())
	})
	t.Run("確定済みからキャンセルできる", func(t *testing.T) {
		c, _ := newCargoFixture(t, domain.CargoTypeGeneral, nil, nil)
		require.NoError(t, c.Confirm())
		require.NoError(t, c.Cancel())
		assert.Equal(t, domain.BookingStatusCancelled, c.Status())
	})
	t.Run("キャンセル済みを再度キャンセルするとエラー", func(t *testing.T) {
		c, _ := newCargoFixture(t, domain.CargoTypeGeneral, nil, nil)
		require.NoError(t, c.Cancel())
		require.ErrorIs(t, c.Cancel(), domain.ErrInvalidStatusTransition)
	})
}

func TestCargoSendBackToRouting(t *testing.T) {
	t.Run("確定済みから経路再設計へ差し戻せる", func(t *testing.T) {
		c, _ := newCargoFixture(t, domain.CargoTypeGeneral, nil, nil)
		require.NoError(t, c.Confirm())
		require.NoError(t, c.SendBackToRouting())
		assert.Equal(t, domain.BookingStatusPreliminary, c.Status())
	})
	t.Run("PRELIMINARY からは差し戻せない", func(t *testing.T) {
		c, _ := newCargoFixture(t, domain.CargoTypeGeneral, nil, nil)
		require.ErrorIs(t, c.SendBackToRouting(), domain.ErrInvalidStatusTransition)
	})
}
