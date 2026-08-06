// Package application は認証ユースケース（ログイン）とポートを提供する。
package application

import (
	"context"
	"errors"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/auth/domain"
)

// 認証エラー。
var (
	ErrInvalidCredentials = errors.New("invalid username or password")
	ErrUserDisabled       = errors.New("user is disabled")
)

// UserRepository はユーザーの読み取りポート（出力ポート）。
type UserRepository interface {
	FindByUsername(ctx context.Context, username string) (domain.User, bool, error)
}

// PasswordHasher はパスワードハッシュの照合ポート。実装は bcrypt（infrastructure）。
type PasswordHasher interface {
	Compare(hash, plain string) bool
}

// AuthService は認証（ログイン）ユースケースを実行する。
type AuthService struct {
	repo   UserRepository
	hasher PasswordHasher
}

// NewAuthService は AuthService を生成する。
func NewAuthService(repo UserRepository, hasher PasswordHasher) *AuthService {
	return &AuthService{repo: repo, hasher: hasher}
}

// Authenticate はユーザー名・パスワードを検証し、成功したら User を返す。
func (s *AuthService) Authenticate(ctx context.Context, username, plain string) (domain.User, error) {
	user, found, err := s.repo.FindByUsername(ctx, username)
	if err != nil {
		return domain.User{}, err
	}
	if !found {
		return domain.User{}, ErrInvalidCredentials
	}
	if !s.hasher.Compare(user.PasswordHash(), plain) {
		return domain.User{}, ErrInvalidCredentials
	}
	if !user.IsEnabled() {
		return domain.User{}, ErrUserDisabled
	}
	return user, nil
}
