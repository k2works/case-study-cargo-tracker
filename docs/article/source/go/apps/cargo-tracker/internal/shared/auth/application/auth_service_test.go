package application_test

import (
	"context"
	"testing"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/auth/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/auth/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type stubUserRepo struct {
	user  *domain.User
	found bool
}

func (s stubUserRepo) FindByUsername(_ context.Context, _ string) (domain.User, bool, error) {
	if !s.found {
		return domain.User{}, false, nil
	}
	return *s.user, true, nil
}

// stubHasher は "hash:plain" 形式で一致判定する簡易ハッシャ。
type stubHasher struct{}

func (stubHasher) Compare(hash, plain string) bool { return hash == "hashed-"+plain }

func newUser(t *testing.T, enabled bool) *domain.User {
	t.Helper()
	u, err := domain.NewUser("sales01", "hashed-secret", enabled, []string{"ROLE_SALES"})
	require.NoError(t, err)
	return &u
}

func TestAuthService_Authenticate(t *testing.T) {
	t.Run("正しい資格情報で認証に成功する", func(t *testing.T) {
		svc := application.NewAuthService(stubUserRepo{user: newUser(t, true), found: true}, stubHasher{})
		u, err := svc.Authenticate(context.Background(), "sales01", "secret")
		require.NoError(t, err)
		assert.Equal(t, "sales01", u.Username())
	})

	t.Run("誤ったパスワードで ErrInvalidCredentials", func(t *testing.T) {
		svc := application.NewAuthService(stubUserRepo{user: newUser(t, true), found: true}, stubHasher{})
		_, err := svc.Authenticate(context.Background(), "sales01", "wrong")
		require.ErrorIs(t, err, application.ErrInvalidCredentials)
	})

	t.Run("存在しないユーザーで ErrInvalidCredentials", func(t *testing.T) {
		svc := application.NewAuthService(stubUserRepo{found: false}, stubHasher{})
		_, err := svc.Authenticate(context.Background(), "nobody", "secret")
		require.ErrorIs(t, err, application.ErrInvalidCredentials)
	})

	t.Run("無効化ユーザーで ErrUserDisabled", func(t *testing.T) {
		svc := application.NewAuthService(stubUserRepo{user: newUser(t, false), found: true}, stubHasher{})
		_, err := svc.Authenticate(context.Background(), "sales01", "secret")
		require.ErrorIs(t, err, application.ErrUserDisabled)
	})
}
