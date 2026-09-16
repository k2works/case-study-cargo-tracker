package com.example.cargotracker.acceptance.simulation;

import com.example.cargotracker.auth.AuthApplication;
import com.example.cargotracker.billing.BillingApplication;
import com.example.cargotracker.booking.BookingApplication;
import com.example.cargotracker.gateway.GatewayApplication;
import com.example.cargotracker.handling.HandlingApplication;
import com.example.cargotracker.routing.RoutingApplication;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.simulation.SimulationApplication;
import com.example.cargotracker.tracking.TrackingApplication;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 業務シミュレーションのデモ項目を回す土台（UC23 / US33・US34）。
 *
 * <p><b>7 つの業務サービスと Gateway を同じ JVM に立てる。</b> このデモ項目が
 * 確かめたいのは「予約から精算まで端から端まで通ること」なので、1 つでも欠けると
 * 確かめたいものが無くなる。<b>代役に差し替えない</b>——経路探索を差し替えると、
 * 「候補が 0 件のときに止まる」ほうのシナリオが意味を失う。</p>
 *
 * <p><b>Gateway も本物を立てる。</b> 工程は Gateway 経由で叩く（[ADR-0020] 決定 2）
 * ので、ここを飛ばすと認可を踏まない——「シミュレーションは通るのに実際の操作は
 * 通らない」状態を検出できなくなる。</p>
 *
 * <p><b>ポートは先に決める。</b> simulationms は Gateway の URL を要り、Gateway は
 * simulationms の URL を要る（工程が Gateway を通って戻ってくる）。起動順で解けない
 * ので、空いているポートを先に押さえてから両方へ渡す。</p>
 */
public final class SimulationStack extends AbstractAxonIntegrationTest {

    /** 動作確認用の利用者と Gateway が同じ鍵を使う。片方だけだと全要求が 401 になる。 */
    private static final String JWT_SECRET =
            "acceptance-only-secret-for-simulation-demo-items-0123456789";

    /**
     * 認証の自動設定を外す。
     *
     * <p><b>1 つの JVM に載せた副作用である。</b> authms が持ち込む Spring Security が
     * classpath 上に居るので、自前の設定を持たないサービス（Gateway・業務 6 サービス）
     * にも既定の認証が掛かり、<b>本文の無い 401</b> を返す。本番では各サービスが
     * 別のプロセスなので起きない。<b>authms には掛けない</b>——あちらは自分の
     * 設定を持っており、それが自動設定を退ける。</p>
     */
    private static final String EXCLUDE_SECURITY =
            "--spring.autoconfigure.exclude="
                    + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure"
                    + ".UserDetailsServiceAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure.web.servlet"
                    + ".ServletWebSecurityAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure.actuate.web.servlet"
                    + ".ManagementWebSecurityAutoConfiguration";

    private static final List<ConfigurableApplicationContext> CONTEXTS = new ArrayList<>();

    private static Map<String, Integer> ports;
    private static boolean started;

    private SimulationStack() {
    }

    /** Gateway の入口。<b>ステップはここだけを叩く</b>（人と同じ経路）。 */
    public static String gatewayUrl() {
        start();
        return "http://localhost:" + ports.get("gateway");
    }

    /** 本番の設定を模した simulationms（実行が断られる側）。 */
    public static String disabledSimulationUrl() {
        start();
        return "http://localhost:" + ports.get("simulationDisabled");
    }

    /** 一度だけ立てる。<b>シナリオごとに立て直さない</b>——起動が支配的になる。 */
    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        ports = reservePorts("auth", "booking", "routing", "tracking", "handling",
                "billing", "simulation", "simulationDisabled", "gateway");

        launch(AuthApplication.class, "auth", "sim_auth", ports.get("auth"),
                "--cargo-tracker.demo-users=true",
                "--cargo-tracker.jwt.secret=" + JWT_SECRET);
        launch(BookingApplication.class, "booking", "sim_booking", ports.get("booking"),
                EXCLUDE_SECURITY,
                // **印を受け付ける環境として立てる。** 既定では断られるので、
                // これが無いと 1 工程目（荷主の登録）で止まる（US33 §3 の守り）。
                "--cargo-tracker.simulation.enabled=true");
        launch(RoutingApplication.class, "routing", "sim_routing", ports.get("routing"),
                EXCLUDE_SECURITY);
        launch(TrackingApplication.class, "tracking", "sim_tracking", ports.get("tracking"),
                EXCLUDE_SECURITY);
        launch(HandlingApplication.class, "handling", "sim_handling", ports.get("handling"),
                EXCLUDE_SECURITY);
        launch(BillingApplication.class, "billing", "sim_billing", ports.get("billing"),
                EXCLUDE_SECURITY);

        String gatewayUrl = "http://localhost:" + ports.get("gateway");
        launch(SimulationApplication.class, "simulation", "sim_simulation",
                ports.get("simulation"), EXCLUDE_SECURITY,
                "--cargo-tracker.simulation.enabled=true",
                // **継続実行も許可した実物を立てる**（US36）。実行そのものの許可とは
                // 別の段なので、片方だけ立てると §6 の断りを確かめたことにならない。
                "--cargo-tracker.simulation.schedule.enabled=true",
                // **間隔は短くする。** 本番の既定（30 秒）のままだと、受け入れが
                // 「頃合いが来るのを待つ」だけで数分伸びる。**0 にはしない**
                // ——間を空けないと業務が止まる（この局面に固有の危険 3）。
                "--cargo-tracker.simulation.schedule.interval=5s",
                // **設定は全部渡す。** 同じ JVM に 8 つ載せると application.yml が
                // classpath 上で衝突し、どれか 1 つしか読まれない——既定を
                // 当てにすると `maxConcurrent=0`（動かない稼働）で立ち上がる（実測）。
                "--cargo-tracker.simulation.schedule.max-concurrent=2",
                "--cargo-tracker.simulation.schedule.exception-ratio=0.2",
                "--cargo-tracker.simulation.gateway-url=" + gatewayUrl);
        // **断るほうも立てる。** 「本番では実行しない」は設定で決まるので、
        // 同じ実装を違う設定で立てないと確かめられない（US33 §4）。
        launch(SimulationApplication.class, "simulation", "sim_simulation_off",
                ports.get("simulationDisabled"), EXCLUDE_SECURITY,
                "--cargo-tracker.simulation.enabled=false",
                // 継続実行も許可しない（US36 §6）。
                "--cargo-tracker.simulation.schedule.enabled=false",
                "--cargo-tracker.simulation.schedule.interval=5s",
                "--cargo-tracker.simulation.schedule.max-concurrent=2",
                "--cargo-tracker.simulation.schedule.exception-ratio=0.2",
                "--cargo-tracker.simulation.gateway-url=" + gatewayUrl);

        startGateway();
        Runtime.getRuntime().addShutdownHook(new Thread(SimulationStack::stop));
    }

    /**
     * Gateway を立てる。
     *
     * <p><b>経路は起動引数で宣言する。</b> 同じ JVM に 7 つのサービスを載せると
     * {@code application.yml} が classpath 上で衝突し、どれか 1 つしか読まれない
     * ——Gateway の経路定義が消えると、すべての要求が 404 になる（実測）。
     * 本番では各サービスが別のプロセスなので起きない。</p>
     */
    private static void startGateway() {
        List<String> args = new ArrayList<>(List.of(
                "--spring.application.name=gatewayms",
                "--server.port=" + ports.get("gateway"),
                "--axon.axonserver.servers=" + AXON_SERVER.getAxonServerAddress(),
                "--cargo-tracker.jwt.secret=" + JWT_SECRET,
                EXCLUDE_SECURITY,
                // **Gateway 自身は DB を持たない。** それでも渡すのは、
                // 同じ JVM に載せた他サービスの `application.yml` が classpath 上で
                // 衝突し、既定の接続先（localhost:5432）を拾ってしまうためである。
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl() + "&currentSchema=sim_gateway",
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.flyway.enabled=false",
                "--cargo.context=gateway"));
        int index = 0;
        for (Map.Entry<String, String> entry : productionRoutes().entrySet()) {
            String route = "--spring.cloud.gateway.server.webmvc.routes[" + index + "]";
            args.add(route + ".id=" + entry.getKey());
            args.add(route + ".uri=http://localhost:" + ports.get(entry.getKey()));
            args.add(route + ".predicates[0]=" + entry.getValue());
            index++;
        }
        CONTEXTS.add(new SpringApplicationBuilder(GatewayApplication.class)
                .properties("spring.main.allow-bean-definition-overriding=true")
                .run(args.toArray(String[]::new)));
    }

    /** 本番の Gateway の設定。<b>書き写さずに読み取る</b>。 */
    private static final Path GATEWAY_CONFIG =
            Path.of("../gatewayms/src/main/resources/application.yml");

    private static final Pattern ROUTE_ID = Pattern.compile("^\\s*- id:\\s*(\\S+)\\s*$");
    private static final Pattern ROUTE_PREDICATES =
            Pattern.compile("^\\s*predicates:\\s*\\[(.+)\\]\\s*$");

    /**
     * 本番の Gateway が宣言している経路（id → 述語）。
     *
     * <p><b>書き写さない</b>（IT16 のレビュー N2）。受け入れスイートが経路を
     * 自分で並べていたので、<b>本番の yml から経路を消しても受け入れは緑のまま</b>
     * だった——ADR-0020 が「残っている窓」と呼んだものを、確かめる側が広げていた。</p>
     *
     * <p><b>差し替えるのは宛先だけ。</b> 同じ JVM に載せるので port は実行ごとに
     * 変わるが、<b>どのパスがどのサービスへ行くか</b>は本番と同じでなければ
     * 確かめたことにならない。</p>
     *
     * <p><b>読めなければ止まる。</b> 空の一覧で起動すると全部が 404 になり、
     * 「業務が壊れている」ように見える——原因から遠い形で落とさない。</p>
     */
    private static Map<String, String> productionRoutes() {
        List<String> lines;
        try {
            lines = Files.readAllLines(GATEWAY_CONFIG, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "本番の Gateway の設定を読めない: " + GATEWAY_CONFIG.toAbsolutePath(), e);
        }
        Map<String, String> routes = new LinkedHashMap<>();
        String id = null;
        for (String line : lines) {
            var idMatch = ROUTE_ID.matcher(line);
            if (idMatch.matches()) {
                id = idMatch.group(1);
                continue;
            }
            var predicateMatch = ROUTE_PREDICATES.matcher(line);
            if (id != null && predicateMatch.matches()) {
                routes.put(id, predicateMatch.group(1).trim());
                id = null;
            }
        }
        // **読み取れたことを数え上げて確かめる。** 空や欠けた一覧で起動すると
        // 全部が 404 になり、「業務が壊れている」ように見える——原因から遠い
        // 形で落とさない。宛先を差し替えられない経路があれば、そこで止める。
        if (routes.isEmpty()) {
            throw new IllegalStateException("本番の Gateway の経路を読み取れていない");
        }
        routes.keySet().stream()
                .filter(routeId -> !ports.containsKey(routeId))
                .findFirst()
                .ifPresent(routeId -> {
                    throw new IllegalStateException(
                            "本番の Gateway に、この受け入れが立てていないサービスの経路がある: "
                                    + routeId + "（スイートに足すか、経路を見直す）");
                });
        return routes;
    }

    private static void launch(Class<?> application, String service, String schema, int port,
            String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.application.name=" + service + "ms",
                "--spring.flyway.locations=classpath:db/migration/" + service,
                "--mybatis.mapper-locations=classpath*:mapper/*.xml",
                "--spring.config.import=optional:classpath:cargo-rates.yml",
                "--server.port=" + port,
                "--axon.axonserver.servers=" + AXON_SERVER.getAxonServerAddress(),
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.flyway.schemas=" + schema,
                "--spring.flyway.default-schema=" + schema,
                "--spring.flyway.create-schemas=true",
                // **application.yml は 1 つしか読まれない。** 同じ JVM に 7 つ載せると
                // classpath 上で衝突し、どれか 1 つの設定だけが効く。サービスごとに
                // 違う値は起動引数で明示的に渡す（退避先の経路は接頭辞で振り分ける）。
                "--cargo.context=" + service));
        args.addAll(List.of(extra));
        CONTEXTS.add(new SpringApplicationBuilder(application)
                .properties("spring.main.allow-bean-definition-overriding=true")
                .run(args.toArray(String[]::new)));
    }

    private static synchronized void stop() {
        for (int i = CONTEXTS.size() - 1; i >= 0; i--) {
            CONTEXTS.get(i).close();
        }
        CONTEXTS.clear();
    }

    /**
     * 空いているポートを押さえる。
     *
     * <p><b>0 番に任せられない。</b> 相互に URL を要る 2 つ（Gateway と
     * simulationms）は、起動してからでは互いの番号を知れない。</p>
     */
    private static Map<String, Integer> reservePorts(String... names) {
        Map<String, Integer> reserved = new LinkedHashMap<>();
        List<ServerSocket> held = new ArrayList<>();
        try {
            for (String name : names) {
                ServerSocket socket = new ServerSocket(0);
                held.add(socket);
                reserved.put(name, socket.getLocalPort());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("空きポートを押さえられませんでした", e);
        } finally {
            for (ServerSocket socket : held) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // 押さえを離すだけ。閉じられなくても起動時に分かる。
                }
            }
        }
        return reserved;
    }
}
