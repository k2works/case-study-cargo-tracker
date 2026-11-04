package cargotracker.booking.usecase

import cargotracker.sharedkernel.{InvalidValue, Principal}
import java.security.MessageDigest
import scala.concurrent.{ExecutionContext, Future}

/** 冪等性の共通処理（ADR-003）。 */
private object Idempotency:

  def requestHash(request: Product): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(request.toString.getBytes("UTF-8"))
      .map("%02x".format(_))
      .mkString

  def validateKey(key: String): Either[InvalidValue, String] =
    Either.cond(key.trim.nonEmpty, key.trim, InvalidValue("idempotencyKey", "空にできない"))

  /** 同じキーの最初の結果があれば返す。内容が違えば拒む。無ければ `run` を実行し、成功したときだけ結果を残す。 */
  def once(
      store: IdempotencyStore,
      owner: Principal,
      key: String,
      hash: String
  )(
      run: => Future[Either[UseCaseError, IdempotentOutput]]
  )(using ExecutionContext): Future[Either[UseCaseError, IdempotentOutput]] =
    store.find(owner.userId, key).flatMap {
      case Some(stored) if stored.requestHash == hash => Future.successful(Right(stored.output))
      case Some(_) => Future.successful(Left(UseCaseError.IdempotencyKeyReused))
      case None    =>
        run.flatMap {
          case Right(output) =>
            store.save(owner.userId, key, StoredResult(hash, output)).map(_ => Right(output))
          case left => Future.successful(left)
        }
    }
