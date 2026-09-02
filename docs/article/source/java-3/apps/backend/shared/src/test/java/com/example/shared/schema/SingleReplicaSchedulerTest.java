package com.example.shared.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <strong>状態をメモリに持つスケジューラは、Pod が 1 つであることを前提にしている</strong>
 * （TD-04・IT16）。
 *
 * <p>simulationms の {@code ContinuousRunScheduler} は、実行間隔（前回いつ始めたか）と
 * 停止の期限をプロセスのメモリで覚える。Pod が 2 つになると<strong>それぞれが自分の
 * 記憶で判断する</strong>——間隔は半分になり、同時実行数の上限は 2 倍まで許される。
 * どちらも「守っているつもり」のまま破れる。
 *
 * <p><strong>文章で残しても守られない</strong>（ADR-009 は 7 IT のあいだ半分しか
 * 守られなかった）。決定を検査に落とす。
 *
 * <p>直すのなら状態を DB へ移すことになる。そのときは<strong>この検査を消す</strong>
 * ——検査が消せることが、前提が無くなったことの証である。
 */
@DisplayName("継続実行の単一 Pod 前提")
class SingleReplicaSchedulerTest {

    private static final Path MANIFESTS = Path.of("..").toAbsolutePath().normalize()
            .resolve("../../ops/k8s/kustomize/base").normalize();

    /**
     * 状態をメモリで持つ仕組みを抱えるサービス。
     *
     * <p>いまは 1 つだけである。<strong>増えたらここに足す</strong>——足し忘れは
     * 「検査していない」ことになるため、その場でこの Javadoc を読む形にしておく。
     */
    private static final String SCHEDULER_SERVICE = "simulationms";

    private static final Pattern REPLICAS = Pattern.compile("^\\s*replicas:\\s*(\\d+)\\s*$",
            Pattern.MULTILINE);

    @Test
    @DisplayName("継続実行を持つサービスの Pod は 1 つに固定されている")
    void keepsTheSchedulerServiceSingleReplica() throws IOException {
        Path manifest = MANIFESTS.resolve(SCHEDULER_SERVICE + ".yaml");

        assertThat(manifest)
                .as("マニフェストが読めていない。検査が何も守らないまま緑になる")
                .isRegularFile();

        Matcher matcher = REPLICAS.matcher(Files.readString(manifest));

        assertThat(matcher.find())
                .as("%s のマニフェストに replicas の指定が無い。既定は 1 だが、"
                        + "**書いていないものは守られていない**", SCHEDULER_SERVICE)
                .isTrue();
        assertThat(matcher.group(1))
                .as("継続実行のスケジューラは状態をメモリに持つ（[ADR-032]）。"
                        + "Pod が増えると、実行間隔も同時実行数の上限も守られない"
                        + "——それぞれの Pod が自分の記憶で判断するためである")
                .isEqualTo("1");
    }
}
