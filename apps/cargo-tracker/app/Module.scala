import com.google.inject.AbstractModule
import play.api.db.DBApi
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}

import javax.inject.{Inject, Singleton}

/** Play（HikariCP）のデータソースを ScalikeJDBC のコネクションプールとして登録する。 */
@Singleton
class ScalikeJdbcInitializer @Inject() (dbApi: DBApi):
  ConnectionPool.singleton(new DataSourceConnectionPool(dbApi.database("default").dataSource))

class Module extends AbstractModule:
  override def configure(): Unit =
    bind(classOf[ScalikeJdbcInitializer]).asEagerSingleton()
