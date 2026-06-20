package cargotracker.auth.domain

import org.mindrot.jbcrypt.BCrypt

/** bcrypt によりハッシュ化されたパスワード。
  *
  *   - 平文からの生成は [[PasswordHash.fromPlain]] でバリデーション付き
  *   - 永続化からの復元は [[PasswordHash.unsafeFromHash]]（既にハッシュ済みの文字列を信頼する）
  *   - 等価判定は [[matches]] により平文と照合する
  */
opaque type PasswordHash = String

object PasswordHash:

  /** パスワードに関するドメインエラー */
  sealed trait Error
  case object PasswordTooShort extends Error

  private val MinLength = 8
  private val Cost = 12

  /** 平文パスワードから bcrypt ハッシュを生成する。
    *
    * 8 文字未満は [[PasswordTooShort]] を返す。
    */
  def fromPlain(raw: String): Either[Error, PasswordHash] =
    Option(raw).filter(_.length >= MinLength) match
      case Some(valid) => Right(BCrypt.hashpw(valid, BCrypt.gensalt(Cost)))
      case None => Left(PasswordTooShort)

  /** 永続化層から取得したハッシュ文字列をそのまま値オブジェクトとして包む。
    *
    * 既にハッシュ済みであることを呼び出し側が保証する。
    */
  def unsafeFromHash(hashed: String): PasswordHash = hashed

  extension (h: PasswordHash)
    /** 平文を本ハッシュと照合する。 */
    def matches(raw: String): Boolean = BCrypt.checkpw(raw, h)

    /** 永続化のために生の文字列を取り出す。 */
    def value: String = h
