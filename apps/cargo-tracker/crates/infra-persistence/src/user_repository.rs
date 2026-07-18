//! 認証ユーザーの永続化とパスワード検証（Security Context）。

use argon2::Argon2;
use argon2::password_hash::{
    PasswordHash, PasswordHasher, PasswordVerifier, SaltString, rand_core::OsRng,
};
use shared_kernel::Role;
use sqlx::PgPool;
use sqlx::Row;

/// 認証済みユーザー（ロール付き）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AuthenticatedUser {
    /// ユーザー ID（サロゲートキー）。
    pub id: i64,
    /// ログイン名。
    pub username: String,
    /// 付与されたロール。
    pub roles: Vec<Role>,
}

/// 認証リポジトリのエラー。
#[derive(Debug, thiserror::Error)]
pub enum AuthError {
    /// 永続化層のエラー。
    #[error("auth backend error: {0}")]
    Backend(String),
    /// パスワードハッシュ処理のエラー。
    #[error("password hashing error: {0}")]
    Hashing(String),
}

/// 平文パスワードを argon2 でハッシュ化する。
///
/// # Errors
///
/// ハッシュ生成に失敗した場合は `AuthError::Hashing` を返す。
pub fn hash_password(password: &str) -> Result<String, AuthError> {
    let salt = SaltString::generate(&mut OsRng);
    Argon2::default()
        .hash_password(password.as_bytes(), &salt)
        .map(|h| h.to_string())
        .map_err(|e| AuthError::Hashing(e.to_string()))
}

/// パスワードハッシュと平文を照合する。不正なハッシュや不一致は `false`。
#[must_use]
pub fn verify_password(hash: &str, password: &str) -> bool {
    match PasswordHash::new(hash) {
        Ok(parsed) => Argon2::default()
            .verify_password(password.as_bytes(), &parsed)
            .is_ok(),
        Err(_) => false,
    }
}

/// PostgreSQL による認証ユーザーリポジトリ。
pub struct SqlxUserRepository {
    pool: PgPool,
}

impl SqlxUserRepository {
    /// コネクションプールからリポジトリを生成する。
    #[must_use]
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    fn backend<E: std::fmt::Display>(e: E) -> AuthError {
        AuthError::Backend(e.to_string())
    }

    /// ユーザーを作成する（初期セットアップ・テスト用）。パスワードはハッシュ化して保存する。
    ///
    /// # Errors
    ///
    /// 永続化・ハッシュ化に失敗した場合はエラーを返す。
    pub async fn create_user(
        &self,
        username: &str,
        email: &str,
        password: &str,
        roles: &[Role],
    ) -> Result<i64, AuthError> {
        let hash = hash_password(password)?;
        let row = sqlx::query(
            r"INSERT INTO users (username, email, password) VALUES ($1, $2, $3) RETURNING id",
        )
        .bind(username)
        .bind(email)
        .bind(hash)
        .fetch_one(&self.pool)
        .await
        .map_err(Self::backend)?;
        let id: i64 = row.try_get("id").map_err(Self::backend)?;

        for role in roles {
            sqlx::query(r"INSERT INTO user_roles (user_id, role) VALUES ($1, $2)")
                .bind(id)
                .bind(role.as_str())
                .execute(&self.pool)
                .await
                .map_err(Self::backend)?;
        }
        Ok(id)
    }

    /// ユーザー名で認証情報（ユーザー + パスワードハッシュ）を取得する。
    ///
    /// # Errors
    ///
    /// 永続化層のエラー時は `AuthError::Backend` を返す。存在しない場合は `Ok(None)`。
    pub async fn find_credentials(
        &self,
        username: &str,
    ) -> Result<Option<(AuthenticatedUser, String)>, AuthError> {
        let row = sqlx::query(
            r"SELECT id, username, password FROM users WHERE username = $1 AND enabled = TRUE",
        )
        .bind(username)
        .fetch_optional(&self.pool)
        .await
        .map_err(Self::backend)?;

        let Some(row) = row else {
            return Ok(None);
        };
        let id: i64 = row.try_get("id").map_err(Self::backend)?;
        let username: String = row.try_get("username").map_err(Self::backend)?;
        let password: String = row.try_get("password").map_err(Self::backend)?;

        let role_rows = sqlx::query(r"SELECT role FROM user_roles WHERE user_id = $1")
            .bind(id)
            .fetch_all(&self.pool)
            .await
            .map_err(Self::backend)?;
        let mut roles = Vec::new();
        for r in role_rows {
            let role_str: String = r.try_get("role").map_err(Self::backend)?;
            if let Ok(role) = Role::parse(&role_str) {
                roles.push(role);
            }
        }

        Ok(Some((
            AuthenticatedUser {
                id,
                username,
                roles,
            },
            password,
        )))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ハッシュ化したパスワードは検証できる() {
        let hash = hash_password("SecurePass123!").expect("hash");
        assert!(verify_password(&hash, "SecurePass123!"));
        assert!(!verify_password(&hash, "wrong"));
    }

    #[test]
    fn 不正なハッシュ文字列は検証に失敗する() {
        assert!(!verify_password("not-a-hash", "anything"));
    }
}
