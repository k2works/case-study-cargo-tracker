package com.example.cargotracker.shared.testing;

import java.util.concurrent.atomic.AtomicInteger;
import org.axonframework.test.server.AxonServerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 統合テストの基底クラス。Axon Server（DCB 有効）と PostgreSQL を立てる。
 *
 * <p>コンテナは static にして全テストで使い回す。テストごとに立て直すと、
 * Axon Server の起動が支配的になって統合テストを書かなくなる。</p>
 *
 * <p><b>DCB を有効にする理由。</b> {@code @EventSourced(tagKey)} は DCB 前提で、
 * 無効な context では接続そのものが確立しない（IT1 スパイクで実測）。本番と同じ形で
 * 立てないと、統合テストが緑でも本番だけ動かない。</p>
 *
 * <p><b>スキーマを分ける理由。</b> 投影は {@code token_entry} のセグメントを
 * 掴んで動く。複数のテストクラスが同じスキーマを見ると、前のコンテキストが掴んだ
 * ままのセグメントを次が取れず、{@code Failed to start bean 'axon-start-lifecycle-handler'}
 * で起動に失敗する。落ちるテストが実行順で変わるので、原因が追いにくい形で出る。
 * クラスごとにスキーマを分けて、掴み合いを起こさないようにする。</p>
 */
public abstract class AbstractAxonIntegrationTest {

    /**
     * Axon Server コンテナを本番と同じ形で組み立てる。
     *
     * <p><b>組み立て方をここ 1 か所に置く。</b> 同じ内容を各テストで書くと、起動猶予の
     * ような設定を片方だけ直すことになる（IT2 で実際に 3 か所に散り、直し漏れた 1 つが
     * 全体ビルドだけで落ちた）。自分でコンテナの生死を操るテスト（停止試験）も
     * この組み立てを使う。</p>
     */
    public static AxonServerContainer axonServerContainer() {
        return new AxonServerContainer("axoniq/axonserver:2026.0.4")
                .withDevMode(true)
                .withDcbContext(true)
                // Axon Server は起動が遅い（設定の初期化だけで数十秒）。開発機が
                // 混んでいると Testcontainers の既定 60 秒を超えて落ちる。落ちると
                // 「壊れた」ように見えるが、待てば上がる。k8s の
                // initialDelaySeconds: 120 と同じ理由で長めに取る。
                //
                // 3 分では足りなかった（IT2 で実測）。kind クラスタや SonarQube が
                // 同じ Docker で動いていると、全体ビルドの終盤で 3 分を超える。
                // 単独実行では 2 分で通るので、これは検査の失敗ではなく待ち時間の
                // 問題である。長さで吸収する。
                .withStartupTimeout(java.time.Duration.ofMinutes(6));
    }

    protected static final AxonServerContainer AXON_SERVER = axonServerContainer();

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();

    static {
        AXON_SERVER.start();
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String schema = "it_" + SCHEMA_SEQUENCE.incrementAndGet();
        registry.add("axon.axonserver.servers", AXON_SERVER::getAxonServerAddress);
        registry.add("spring.datasource.url",
                () -> POSTGRES.getJdbcUrl() + "&currentSchema=" + schema);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.schemas", () -> schema);
        registry.add("spring.flyway.default-schema", () -> schema);
        registry.add("spring.flyway.create-schemas", () -> "true");
    }
}
