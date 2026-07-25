package domain_test

import (
	"testing"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewShipperId(t *testing.T) {
	t.Run("値を持つ ShipperId を生成できる", func(t *testing.T) {
		id, err := domain.NewShipperId("11111111-2222-3333-4444-555555555555")

		require.NoError(t, err)
		assert.Equal(t, "11111111-2222-3333-4444-555555555555", id.Value())
	})

	t.Run("空文字の ShipperId はエラーになる", func(t *testing.T) {
		_, err := domain.NewShipperId("")

		require.Error(t, err)
	})

	t.Run("同じ値の ShipperId は等価である", func(t *testing.T) {
		a, _ := domain.NewShipperId("id-1")
		b, _ := domain.NewShipperId("id-1")

		assert.Equal(t, a, b)
	})
}
