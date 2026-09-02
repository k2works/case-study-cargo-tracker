package com.example.routingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.architecture.DialectSmoke;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 方言スモーク。全クエリが PostgreSQL（本番）と H2（ローカル・開発環境）の両方で
 * 解釈できることを確かめる。
 *
 * <p>方言差は「本番で緑・ローカルで赤」も「ローカルで緑・本番で赤」も同じ頻度で起きる。
 * この検査が見るのは「その DB がその SQL を理解できるか」までであり、結果の正しさは
 * 統合テストで確かめる。
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("方言スモーク（PostgreSQL）")
class DialectSmokeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("全クエリが PostgreSQL で解釈できる")
    void allStatementsAreParseableOnPostgres() {
        List<String> sqls = DialectSmoke.statementsOf(sqlSessionFactory.getConfiguration());

        assertThat(sqls).as("1 つも SQL が集まっていない場合、この検査は何も守らない").isNotEmpty();
        assertThat(DialectSmoke.unparseable(dataSource, sqls))
                .as("PostgreSQL で解釈できない SQL がある")
                .isEmpty();
    }

    /** H2（ローカルの手軽な起動先・Heroku 開発環境）での解釈を確かめる。 */
    @SpringBootTest
    @ExtendWith(SpringExtension.class)
    @DisplayName("方言スモーク（H2）")
    static class H2Dialect {

        @Autowired
        private SqlSessionFactory sqlSessionFactory;

        @Autowired
        private DataSource dataSource;

        @Test
        @DisplayName("全クエリが H2 で解釈できる")
        void allStatementsAreParseableOnH2() {
            List<String> sqls = DialectSmoke.statementsOf(sqlSessionFactory.getConfiguration());

            assertThat(sqls).as("1 つも SQL が集まっていない場合、この検査は何も守らない").isNotEmpty();
            assertThat(DialectSmoke.unparseable(dataSource, sqls))
                    .as("H2 で解釈できない SQL がある")
                    .isEmpty();
        }
    }
}
