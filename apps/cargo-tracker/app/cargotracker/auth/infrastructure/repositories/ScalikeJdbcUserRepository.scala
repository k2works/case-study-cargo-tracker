package cargotracker.auth.infrastructure.repositories

import cargotracker.auth.domain.model.aggregates.User
import cargotracker.auth.domain.model.repositories.UserRepository
import cargotracker.auth.domain.model.valueobjects.{PasswordHash, Role}
import scalikejdbc.*

import javax.inject.Singleton

/** ScalikeJDBC ベースの [[UserRepository]] 実装。
  *
  *   - `users` と `user_roles` の 2 テーブルを join で読み出し、ユーザー単位に集約する
  *   - 書き込みはトランザクション内で `users` をアップサートし、`user_roles` を再生成する
  */
@Singleton
class ScalikeJdbcUserRepository extends UserRepository:

  override def findByUsername(username: String): Option[User] =
    DB.readOnly { implicit session =>
      val rows =
        sql"""
          SELECT u.username, u.email, u.password, u.enabled, ur.role
          FROM users u
          LEFT JOIN user_roles ur ON ur.user_id = u.id
          WHERE u.username = $username
        """
          .map { rs =>
            (
              rs.string("username"),
              rs.string("email"),
              rs.string("password"),
              rs.boolean("enabled"),
              Option(rs.stringOpt("role")).flatten
            )
          }
          .list
          .apply()

      if rows.isEmpty then None
      else
        val (uname, email, hash, enabled, _) = rows.head
        val roles = rows.flatMap(_._5).flatMap(Role.fromName).toSet
        if roles.isEmpty then None
        else
          User
            .reconstruct(
              username = uname,
              email = email,
              password = PasswordHash.unsafeFromHash(hash),
              roles = roles,
              enabled = enabled
            )
            .toOption
    }

  override def save(user: User): Unit =
    DB.localTx { implicit session =>
      val existingId: Option[Long] =
        sql"SELECT id FROM users WHERE username = ${user.username}"
          .map(_.long("id"))
          .single
          .apply()

      val userId: Long = existingId match
        case Some(id) =>
          sql"""
            UPDATE users
            SET email = ${user.email},
                password = ${user.password.value},
                enabled = ${user.enabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = $id
          """.update.apply()
          id
        case None =>
          sql"""
            INSERT INTO users (username, email, password, enabled)
            VALUES (${user.username}, ${user.email}, ${user.password.value}, ${user.enabled})
          """.updateAndReturnGeneratedKey.apply()

      sql"DELETE FROM user_roles WHERE user_id = $userId".update.apply()

      user.roles.foreach { role =>
        sql"""
          INSERT INTO user_roles (user_id, role)
          VALUES ($userId, ${role.toString})
        """.update.apply()
      }
    }
