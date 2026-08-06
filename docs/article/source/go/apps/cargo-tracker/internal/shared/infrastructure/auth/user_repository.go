// Package auth は認証の出力アダプター（UserRepository・PasswordHasher）を提供する。
package auth

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/auth/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/sqlcgen"
	"golang.org/x/crypto/bcrypt"
)

// UserRepository は sqlc + pgx によるユーザーリポジトリ実装。
type UserRepository struct {
	q *sqlcgen.Queries
}

// NewUserRepository は UserRepository を生成する。
func NewUserRepository(pool *pgxpool.Pool) *UserRepository {
	return &UserRepository{q: sqlcgen.New(pool)}
}

// FindByUsername はユーザー名でユーザーをロール付きで取得する。
func (r *UserRepository) FindByUsername(ctx context.Context, username string) (domain.User, bool, error) {
	row, err := r.q.FindUserByUsername(ctx, username)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.User{}, false, nil
		}
		return domain.User{}, false, err
	}
	roles, err := r.q.ListUserRoles(ctx, row.ID)
	if err != nil {
		return domain.User{}, false, err
	}
	user, err := domain.NewUser(row.Username, row.Password, row.Enabled, roles)
	if err != nil {
		return domain.User{}, false, err
	}
	return user, true, nil
}

// BcryptHasher は bcrypt によるパスワード照合実装。
type BcryptHasher struct{}

// Compare はハッシュと平文パスワードを照合する。
func (BcryptHasher) Compare(hash, plain string) bool {
	return bcrypt.CompareHashAndPassword([]byte(hash), []byte(plain)) == nil
}
