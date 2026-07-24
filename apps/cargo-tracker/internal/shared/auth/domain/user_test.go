package domain_test

import (
	"testing"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/auth/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewUser(t *testing.T) {
	t.Run("ユーザーを生成しロールを保持する", func(t *testing.T) {
		u, err := domain.NewUser("sales01", "$2a$hash", true, []string{"ROLE_SALES", "ROLE_SHIPPER"})
		require.NoError(t, err)
		assert.Equal(t, "sales01", u.Username())
		assert.Equal(t, "$2a$hash", u.PasswordHash())
		assert.True(t, u.IsEnabled())
		assert.True(t, u.HasRole("ROLE_SALES"))
		assert.True(t, u.HasRole("ROLE_SHIPPER"))
		assert.False(t, u.HasRole("ROLE_ADMIN"))
	})

	t.Run("空のユーザー名はエラー", func(t *testing.T) {
		_, err := domain.NewUser("", "h", true, nil)
		require.ErrorIs(t, err, domain.ErrEmptyUsername)
	})

	t.Run("Roles でロール一覧を取得できる", func(t *testing.T) {
		u, _ := domain.NewUser("admin", "h", true, []string{"ROLE_ADMIN"})
		assert.Equal(t, []string{"ROLE_ADMIN"}, u.Roles())
	})
}
