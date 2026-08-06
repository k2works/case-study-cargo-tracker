package domain_test

import (
	"testing"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewLocation(t *testing.T) {
	t.Run("5 文字の UN/LOCODE で生成できる", func(t *testing.T) {
		loc, err := domain.NewLocation("JPTYO")
		require.NoError(t, err)
		assert.Equal(t, "JPTYO", loc.UnLocode())
	})

	tests := []struct{ name, value string }{
		{"空文字はエラー", ""},
		{"5 文字未満はエラー", "JPT"},
		{"5 文字超はエラー", "JPTYOO"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := domain.NewLocation(tt.value)
			require.Error(t, err)
		})
	}

	t.Run("同一 UN/LOCODE は等価", func(t *testing.T) {
		a, _ := domain.NewLocation("USLAX")
		b, _ := domain.NewLocation("USLAX")
		assert.True(t, a.SameAs(b))
	})
}
