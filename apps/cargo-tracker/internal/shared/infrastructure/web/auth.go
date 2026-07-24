package web

import (
	"context"
	"net/http"
	"strings"

	"github.com/alexedwards/scs/v2"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/auth/domain"
)

const (
	sessionKeyUsername = "auth.username"
	sessionKeyRoles    = "auth.roles"
)

// Authenticator は認証ユースケースの入力ポート（interfaces 視点）。
type Authenticator interface {
	Authenticate(ctx context.Context, username, plain string) (domain.User, error)
}

// AuthHandler はログイン・ログアウト画面のハンドラ。
type AuthHandler struct {
	renderer *Renderer
	session  *scs.SessionManager
	auth     Authenticator
}

// NewAuthHandler は AuthHandler を生成する。
func NewAuthHandler(renderer *Renderer, session *scs.SessionManager, auth Authenticator) *AuthHandler {
	return &AuthHandler{renderer: renderer, session: session, auth: auth}
}

// LoginForm はログイン画面を表示する。
func (h *AuthHandler) LoginForm(w http.ResponseWriter, r *http.Request) {
	h.renderer.RenderPage(w, r, "templates/login.html", nil)
}

// Login は認証を行い、成功時にセッションを確立してダッシュボードへリダイレクトする。
func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	username := r.FormValue("username")
	password := r.FormValue("password")

	user, err := h.auth.Authenticate(r.Context(), username, password)
	if err != nil {
		w.WriteHeader(http.StatusUnauthorized)
		h.renderer.RenderPageWithError(w, r, "templates/login.html", nil, "ユーザー名またはパスワードが正しくありません。")
		return
	}

	_ = h.session.RenewToken(r.Context())
	h.session.Put(r.Context(), sessionKeyUsername, user.Username())
	h.session.Put(r.Context(), sessionKeyRoles, strings.Join(user.Roles(), ","))
	http.Redirect(w, r, "/", http.StatusSeeOther)
}

// Logout はセッションを破棄してログイン画面へリダイレクトする。
func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	_ = h.session.Destroy(r.Context())
	http.Redirect(w, r, "/login", http.StatusSeeOther)
}

// SessionCurrentUser はセッションからカレントユーザーを解決しコンテキストへ格納するミドルウェア。
// スタブ認証（固定ロール）を置き換える。
func SessionCurrentUser(session *scs.SessionManager) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			username := session.GetString(r.Context(), sessionKeyUsername)
			if username != "" {
				rolesCSV := session.GetString(r.Context(), sessionKeyRoles)
				var roles []string
				if rolesCSV != "" {
					roles = strings.Split(rolesCSV, ",")
				}
				ctx := WithCurrentUser(r.Context(), CurrentUser{Username: username, Roles: roles})
				r = r.WithContext(ctx)
			}
			next.ServeHTTP(w, r)
		})
	}
}

// RequireAuth は未認証アクセスをログイン画面へリダイレクトする。
func RequireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if CurrentUserFrom(r.Context()).Username == "" {
			http.Redirect(w, r, "/login", http.StatusSeeOther)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// RoleAdmin は全機能へアクセスできる管理者ロール。
const RoleAdmin = "ROLE_ADMIN"

// RequireRole は指定ロールを持たないアクセスを 403 で拒否する。
// ROLE_ADMIN は全機能アクセス可能なため常に許可する。
func RequireRole(roles ...string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			user := CurrentUserFrom(r.Context())
			if user.HasRole(RoleAdmin) {
				next.ServeHTTP(w, r)
				return
			}
			for _, role := range roles {
				if user.HasRole(role) {
					next.ServeHTTP(w, r)
					return
				}
			}
			http.Error(w, "forbidden", http.StatusForbidden)
		})
	}
}
