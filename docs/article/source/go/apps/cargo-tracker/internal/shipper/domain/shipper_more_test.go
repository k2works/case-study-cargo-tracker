package domain_test

import (
	"testing"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestValueObjectErrorBranches(t *testing.T) {
	t.Run("空の荷主名はエラー", func(t *testing.T) {
		_, err := domain.NewShipperName("  ")
		require.ErrorIs(t, err, domain.ErrEmptyShipperName)
	})
	t.Run("空の契約番号はエラー", func(t *testing.T) {
		_, err := domain.NewContractNumber("")
		require.ErrorIs(t, err, domain.ErrEmptyContractNumber)
	})
	t.Run("500 文字超の住所はエラー", func(t *testing.T) {
		long := make([]rune, 501)
		for i := range long {
			long[i] = 'あ'
		}
		_, err := domain.NewAddress(string(long))
		require.ErrorIs(t, err, domain.ErrAddressTooLong)
	})
	t.Run("電話番号・住所の値を取得できる", func(t *testing.T) {
		p := domain.NewPhone("03-1234-5678")
		assert.Equal(t, "03-1234-5678", p.Value())
		a, err := domain.NewAddress("東京都千代田区")
		require.NoError(t, err)
		assert.Equal(t, "東京都千代田区", a.Value())
	})
}

func TestShipperOptionalFieldsAndGetters(t *testing.T) {
	id, _ := domain.NewShipperId("id-1")
	name, _ := domain.NewShipperName("山田太郎")
	email, _ := domain.NewEmail("taro@example.com")
	code := domain.GenerateShipperCode("abcdef12-0000-0000-0000-000000000000")

	s, err := domain.RegisterIndividualShipper(id, code, name, email)
	require.NoError(t, err)

	// オプション項目の設定・取得
	assert.Nil(t, s.Phone())
	assert.Nil(t, s.Address())
	s.SetPhone(domain.NewPhone("03-0000"))
	addr, _ := domain.NewAddress("大阪府")
	s.SetAddress(addr)

	assert.Equal(t, "id-1", s.ID().Value())
	assert.Equal(t, "taro@example.com", s.Email().Value())
	require.NotNil(t, s.Phone())
	assert.Equal(t, "03-0000", s.Phone().Value())
	require.NotNil(t, s.Address())
	assert.Equal(t, "大阪府", s.Address().Value())
}
