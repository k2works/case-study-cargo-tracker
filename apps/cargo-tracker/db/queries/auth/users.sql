-- name: FindUserByUsername :one
SELECT id, username, email, password, enabled
FROM users
WHERE username = $1;

-- name: ListUserRoles :many
SELECT role FROM user_roles WHERE user_id = $1 ORDER BY role;
