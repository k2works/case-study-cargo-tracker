package cargotracker.support

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import org.scalatest.Suite
import org.testcontainers.utility.DockerImageName
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

/** 実 PostgreSQL（Testcontainers）をスイート全体で共有する統合テスト基盤。 */
trait PostgresContainerSupport extends TestContainerForAll:
  self: Suite =>

  if Option(System.getProperty("api.version")).isEmpty then System.setProperty("api.version", "1.48")

  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:16-alpine"))

  def buildApp(container: PostgreSQLContainer): Application =
    GuiceApplicationBuilder()
      .configure(
        "db.default.url" -> container.jdbcUrl,
        "db.default.username" -> container.username,
        "db.default.password" -> container.password
      )
      .build()
