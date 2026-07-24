// Package domain は認証（Authentication/Authorization）のドメインモデルを提供する。
// パスワードのハッシュ照合は外部依存（bcrypt）を避けるため application 層のポートに委譲する。
package domain

import "errors"

// ErrEmptyUsername はユーザー名が空の場合に返される。
var ErrEmptyUsername = errors.New("username must not be empty")

// User は認証ユーザーを表す集約。ユーザー名・パスワードハッシュ・有効状態・ロールを保持する。
type User struct {
	username     string
	passwordHash string
	enabled      bool
	roles        []string
}

// NewUser はバリデーション付きで User を生成する。
func NewUser(username, passwordHash string, enabled bool, roles []string) (User, error) {
	if username == "" {
		return User{}, ErrEmptyUsername
	}
	cp := make([]string, len(roles))
	copy(cp, roles)
	return User{username: username, passwordHash: passwordHash, enabled: enabled, roles: cp}, nil
}

// Username はユーザー名を返す。
func (u User) Username() string { return u.username }

// PasswordHash はパスワードハッシュを返す。
func (u User) PasswordHash() string { return u.passwordHash }

// IsEnabled は有効ユーザーかどうかを返す。
func (u User) IsEnabled() bool { return u.enabled }

// Roles はロール一覧を返す。
func (u User) Roles() []string {
	cp := make([]string, len(u.roles))
	copy(cp, u.roles)
	return cp
}

// HasRole は指定ロールを保持するかを返す。
func (u User) HasRole(role string) bool {
	for _, r := range u.roles {
		if r == role {
			return true
		}
	}
	return false
}
