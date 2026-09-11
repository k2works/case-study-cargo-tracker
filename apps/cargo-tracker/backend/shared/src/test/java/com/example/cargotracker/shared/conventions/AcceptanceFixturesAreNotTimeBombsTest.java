package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 受け入れフィクスチャの日時は「今」から導く（IT12 H.3 / IT13 負債枠 1）。
 *
 * <p><b>固定日付は時限式になる。</b> 一覧は既定で出港済みを外し、予約は過ぎた
 * 到着期限を断る。書いた日付を現実の時刻が追い越した瞬間に、<b>直していないのに
 * 赤くなる</b>——IT12 では出発日時 {@code 2026-09-10T09:00:00Z} 固定のせいで、
 * 同じ日の 08:00 UTC の CI は緑、09:51 UTC の CI は 13 件が赤になった。</p>
 *
 * <p>この検査は 2 つを見る。</p>
 *
 * <ul>
 *   <li><b>ステップ定義（.java）には日付リテラルを書かない。</b> 日付そのものが
 *       検査したい対象ではないので、{@code AcceptanceFixtureTime} から導く。
 *       「十分に過去」が要る場合もそこから取る——書き方を 1 か所に集めておかないと、
 *       次に書く人が literal に戻る。</li>
 *   <li><b>シナリオ（.feature）の日付は、今日から {@value #LEAD_DAYS} 日以内の未来に
 *       しない。</b> シナリオの日付は業務の例として読ませたいので literal を許すが、
 *       期限が近づいたら<b>壊れる前に赤にする</b>。赤くなったときの直し方は日付を
 *       先送りすることではなく、その値を「今」から導く形にすることである。</li>
 * </ul>
 */
class AcceptanceFixturesAreNotTimeBombsTest {

    /** 期限切れの何日前に赤くするか。直す時間を残すための猶予。 */
    private static final int LEAD_DAYS = 30;

    /** 日付リテラル。<b>形を選り好みしない</b>——{@code YYYY-MM-DD} を含む行はすべて拾う。 */
    private static final Pattern DATE = Pattern.compile("(2\\d{3})-(\\d{2})-(\\d{2})");

    /** 書き方を集めた置き場。ここだけは literal を持つ（持たせないと誰も導けない）。 */
    private static final String FIXTURE_TIME = "AcceptanceFixtureTime.java";

    private static Path backendRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("settings.gradle.kts が見つかりません");
    }

    private static List<Path> acceptanceFiles(String suffix) throws IOException {
        Path root = backendRoot().resolve("acceptance-tests/src");
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(suffix)).sorted().toList();
        }
    }

    @Test
    @DisplayName("ステップ定義に日付リテラルを書かない（「今」から導く）")
    void stepDefinitionsDeriveDatesFromNow() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : acceptanceFiles(".java")) {
            if (file.getFileName().toString().equals(FIXTURE_TIME)) {
                continue;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                // 説明の文章（javadoc・行コメント）は読ませたいので対象にしない。
                String code = line.replaceFirst("//.*", "").trim();
                if (code.startsWith("*")) {
                    continue;
                }
                if (DATE.matcher(code).find()) {
                    offenders.add(backendRoot().relativize(file) + ":" + (i + 1) + " " + code);
                }
            }
        }

        assertThat(offenders)
                .as("AcceptanceFixtureTime から導く（固定日付は現実の時刻に追い越される）")
                .isEmpty();
    }

    @Test
    @DisplayName("シナリオの日付が期限切れに近づいたら、壊れる前に赤くする")
    void featureDatesAreNotAboutToExpire() throws IOException {
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(LEAD_DAYS);
        List<String> expiring = new ArrayList<>();

        for (Path file : acceptanceFiles(".feature")) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                Matcher matcher = DATE.matcher(lines.get(i));
                while (matcher.find()) {
                    LocalDate date = LocalDate.of(Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
                    // 過ぎた日付は動かない（もう追い越されている）。危ないのは
                    // 「まだ未来だが、もうすぐ過去になる」もの。
                    if (date.isAfter(today) && date.isBefore(horizon)) {
                        expiring.add(backendRoot().relativize(file) + ":" + (i + 1)
                                + " " + date + "（あと " + (date.toEpochDay() - today.toEpochDay())
                                + " 日）");
                    }
                }
            }
        }

        assertThat(expiring)
                .as("日付を先送りするのではなく、「今」から導く形に直す")
                .isEmpty();
    }
}
