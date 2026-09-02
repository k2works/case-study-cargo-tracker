package com.example.trackingms;

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
 * 方言スモーク。全クエリが PostgreSQL（本番）で解釈できることを確かめる。
 *
 * <p>H2 側の検証は {@link H2Dialect} が担う。方言差は「本番で緑・ローカルで赤」も
 * 「ローカルで緑・本番で赤」も同じ頻度で起きるため、両方向を見る。
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
        List<String> sqls = DialectSmoke.statementsOf(sqlSessionFactory.getConfiguration(), SAMPLES);

        assertThat(sqls).as("1 つも SQL が集まっていない場合、この検査は何も守らない").isNotEmpty();
        assertThat(DialectSmoke.unparseable(dataSource, sqls))
                .as("PostgreSQL で解釈できない SQL がある")
                .isEmpty();
    }

    /**
     * 動的 SQL の代表パラメータ。
     *
     * <p>{@code <foreach>} を含むステートメントは、値が無いと SQL を組み立てられない。
     * 足し忘れると {@link DialectSmoke#statementsOf} が落ちるため、素通りはしない。
     */
    private static final java.util.Map<String, Object> SAMPLES = java.util.Map.of(
            "trackingNumbers", List.of("TRK-20260823-0001"));

    /** H2（ローカルの手軽な起動先）での解釈を確かめる。Docker を必要としない。 */
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
            List<String> sqls = DialectSmoke.statementsOf(sqlSessionFactory.getConfiguration(), SAMPLES);

            assertThat(sqls).as("1 つも SQL が集まっていない場合、この検査は何も守らない").isNotEmpty();
            assertThat(DialectSmoke.unparseable(dataSource, sqls))
                    .as("H2 で解釈できない SQL がある")
                    .isEmpty();
        }
    }
}
