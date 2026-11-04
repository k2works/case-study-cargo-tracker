package cargotracker.app

import cargotracker.testsupport.TestDatabase
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import scala.concurrent.Await
import scala.concurrent.duration.*
import slick.jdbc.PostgresProfile.api.*

/** ブラウザ E2E（Playwright）用の起動口。使い捨ての PostgreSQL（Testcontainers）にマイグレーションを当て、 開発用の認証つきで固定ポートに立てる。接続情報は
  * 毎回の乱数で、リポジトリにも環境変数にも置かない（R-10）。本番の起動口ではない（テスト配下）。
  */
object E2eServer:
  def main(args: Array[String]): Unit =
    val port = args.headOption.flatMap(_.toIntOption).getOrElse(18080)
    given system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "e2e-server", LocalServer.actorSystemConfig)
    val testDb = TestDatabase.fresh()
    val db =
      Database.forURL(testDb.url, testDb.user, testDb.password, driver = "org.postgresql.Driver")
    val config = ServerConfig(
      testDb.url,
      testDb.user,
      testDb.password,
      "127.0.0.1",
      port,
      devAuthEnabled = true
    )
    val server = Await.result(LocalServer.start(config, db), 60.seconds)
    println(s"E2E_SERVER_READY port=${server.port}")
    sys.addShutdownHook { Await.ready(server.stop(), 10.seconds); system.terminate() }
    Thread.currentThread().join()
