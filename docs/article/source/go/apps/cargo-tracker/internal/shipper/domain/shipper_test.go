package domain_test

import (
	"testing"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewEmail(t *testing.T) {
	t.Run("正しい形式のメールを生成できる", func(t *testing.T) {
		email, err := domain.NewEmail("sales@example.com")
		require.NoError(t, err)
		assert.Equal(t, "sales@example.com", email.Value())
	})

	tests := []struct {
		name  string
		value string
	}{
		{"空文字はエラー", ""},
		{"@ がないとエラー", "invalid"},
		{"ドメインがないとエラー", "user@"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := domain.NewEmail(tt.value)
			require.Error(t, err)
		})
	}
}

func TestNewDiscountRate(t *testing.T) {
	tests := []struct {
		name    string
		value   float64
		wantErr bool
	}{
		{"0% は有効", 0.0, false},
		{"30% は有効", 0.30, false},
		{"15% は有効", 0.15, false},
		{"30% 超はエラー", 0.31, true},
		{"負の値はエラー", -0.01, true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			rate, err := domain.NewDiscountRate(tt.value)
			if tt.wantErr {
				require.Error(t, err)
				return
			}
			require.NoError(t, err)
			assert.InDelta(t, tt.value, rate.Value(), 0.00001)
		})
	}
}

func TestNewShipperCode(t *testing.T) {
	t.Run("生の UUID から SHP- プレフィックス付きコードを生成する", func(t *testing.T) {
		code := domain.GenerateShipperCode("abcdef12-3456-7890-abcd-ef1234567890")
		assert.Equal(t, "SHP-ABCDEF12", code.Value())
	})
}

func TestNewShipperType(t *testing.T) {
	t.Run("INDIVIDUAL を解釈できる", func(t *testing.T) {
		st, err := domain.ParseShipperType("INDIVIDUAL")
		require.NoError(t, err)
		assert.Equal(t, domain.ShipperTypeIndividual, st)
	})
	t.Run("CORPORATE を解釈できる", func(t *testing.T) {
		st, err := domain.ParseShipperType("CORPORATE")
		require.NoError(t, err)
		assert.Equal(t, domain.ShipperTypeCorporate, st)
	})
	t.Run("未知の種別はエラー", func(t *testing.T) {
		_, err := domain.ParseShipperType("UNKNOWN")
		require.Error(t, err)
	})
}

// テスト用ヘルパ
func mustID(t *testing.T) domain.ShipperId {
	t.Helper()
	id, err := domain.NewShipperId("11111111-2222-3333-4444-555555555555")
	require.NoError(t, err)
	return id
}

func mustName(t *testing.T, v string) domain.ShipperName {
	t.Helper()
	n, err := domain.NewShipperName(v)
	require.NoError(t, err)
	return n
}

func mustEmail(t *testing.T, v string) domain.Email {
	t.Helper()
	e, err := domain.NewEmail(v)
	require.NoError(t, err)
	return e
}

func TestRegisterIndividualShipper(t *testing.T) {
	t.Run("個人荷主を登録できる", func(t *testing.T) {
		s, err := domain.RegisterIndividualShipper(
			mustID(t),
			domain.GenerateShipperCode("abcdef12-3456-7890-abcd-ef1234567890"),
			mustName(t, "山田太郎"),
			mustEmail(t, "taro@example.com"),
		)

		require.NoError(t, err)
		assert.Equal(t, domain.ShipperTypeIndividual, s.ShipperType())
		assert.Equal(t, "山田太郎", s.Name().Value())
		assert.Equal(t, "SHP-ABCDEF12", s.Code().Value())
		assert.False(t, s.IsCorporate())
	})
}

func TestRegisterCorporateShipper(t *testing.T) {
	t.Run("法人荷主を契約番号・割引率付きで登録できる", func(t *testing.T) {
		contract, err := domain.NewContractNumber("CN-0001")
		require.NoError(t, err)
		rate, err := domain.NewDiscountRate(0.10)
		require.NoError(t, err)

		s, err := domain.RegisterCorporateShipper(
			mustID(t),
			domain.GenerateShipperCode("abcdef12-3456-7890-abcd-ef1234567890"),
			mustName(t, "株式会社サンプル"),
			mustEmail(t, "corp@example.com"),
			contract,
			rate,
		)

		require.NoError(t, err)
		assert.Equal(t, domain.ShipperTypeCorporate, s.ShipperType())
		assert.True(t, s.IsCorporate())
		require.NotNil(t, s.Contract())
		assert.Equal(t, "CN-0001", s.Contract().Number().Value())
		assert.InDelta(t, 0.10, s.Contract().DiscountRate().Value(), 0.00001)
	})
}
