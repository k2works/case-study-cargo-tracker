// Package web は全コンテキスト共通の Web 基盤（テンプレートレンダラ・カレントユーザー）を提供する。
package web

import "context"

// CurrentUser は認証済みユーザーの表示用情報。ロールに応じた UI 表示制御に用いる。
// 注: 認証境界はサーバー側 RBAC ミドルウェアが担保する。テンプレートのロール分岐は表示制御のみ。
type CurrentUser struct {
	Username string
	Roles    []string
}

// HasRole は指定ロールを保持するかを返す。
func (u CurrentUser) HasRole(role string) bool {
	for _, r := range u.Roles {
		if r == role {
			return true
		}
	}
	return false
}

// CanAccess は指定ロールの機能にアクセスできるかを返す。
// 管理者（ROLE_ADMIN）は全機能にアクセス可能なため常に真（RequireRole の admin バイパスと一貫）。
// navbar 等の表示制御に用いる。
func (u CurrentUser) CanAccess(roles ...string) bool {
	if u.HasRole(RoleAdmin) {
		return true
	}
	for _, role := range roles {
		if u.HasRole(role) {
			return true
		}
	}
	return false
}

type ctxKey struct{}

// WithCurrentUser はコンテキストにカレントユーザーを格納する。
func WithCurrentUser(ctx context.Context, u CurrentUser) context.Context {
	return context.WithValue(ctx, ctxKey{}, u)
}

// CurrentUserFrom はコンテキストからカレントユーザーを取得する。
func CurrentUserFrom(ctx context.Context) CurrentUser {
	if u, ok := ctx.Value(ctxKey{}).(CurrentUser); ok {
		return u
	}
	return CurrentUser{}
}
