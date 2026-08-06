package cargotracker.support

import play.api.mvc.Session
import play.api.test.FakeRequest

import java.time.Instant

/** E2E テストで認証済みセッションを持つ FakeRequest を作るユーティリティ。 */
object AuthenticatedRequestSupport:

  private val testSession: Map[String, String] = Map(
    "username" -> "test-admin",
    "roles" -> "Sales,RouteDesigner,Tracker,Settlement,MasterAdmin",
    "lastAccessedAt" -> Instant.now.toString
  )

  extension [A](req: FakeRequest[A])
    /** セッションに認証情報を付与する。 */
    def withAuthenticatedSession: FakeRequest[A] =
      req.withSession(testSession.toSeq*)
