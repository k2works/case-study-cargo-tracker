package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * イテレーション計画の受入基準が名指しした検査は、実在する（IT12 レビューの懸念）。
 *
 * <p><b>書いただけの検査は、書いていないのと同じである。</b> 受入基準の表の
 * 「検査の所在」にクラス名を書いても、そのクラスが無ければ何も守っていない。
 * 計画を書いた時点では実在しなくてよい（これから書くのだから）が、<b>クローズの
 * ときには実在していなければならない</b>。</p>
 *
 * <p><b>走査するのは「対象になりうるもの」の側である。</b> 計画に現れる
 * {@code ...Test} / {@code ...IT} という語をすべて拾ってから、実在するかを見る。
 * 実在するものだけを数えると、書き間違えたものほど漏れる。</p>
 *
 * <p>フロントエンドの検査（{@code *.test.tsx}）も同じ形で見る——バックエンドだけ
 * 見ると、画面側の検査名を書き間違えても緑になる。</p>
 */
class IterationPlanChecksExistTest {

    private static final Path REPOSITORY_ROOT = Path.of("..", "..", "..", "..");
    private static final Path PLAN_DIR = REPOSITORY_ROOT.resolve("docs/development/cargo-tracker");

    /** 検査の名前らしきもの。`CustomsProjectionIT#countsBusinessDays...` の形も拾う。 */
    private static final Pattern CHECK_NAME =
            Pattern.compile("`([A-Z][A-Za-z0-9]*(?:Test|IT))(?:#[A-Za-z0-9_]+)?`");

    /** フロントエンドの検査。 */
    private static final Pattern FRONTEND_CHECK_NAME =
            Pattern.compile("`([A-Za-z0-9.]+\\.test\\.tsx?)`");

    /**
     * 直近の計画だけを見る。
     *
     * <p>過去の計画に出てくる検査は、その後の整理で消えていることがある
     * （消したこと自体は正しい）。<b>いま守ろうとしている約束</b>を対象にする。</p>
     */
    private static final String CURRENT_PLAN = "iteration_plan-13.md";

    @Test
    @DisplayName("計画が名指しした検査（バックエンド）が実在する")
    void backendChecksNamedInThePlanExist() throws IOException {
        Set<String> declared = namesIn(CHECK_NAME);
        assertThat(declared)
                .as("計画から検査名を 1 つも拾えていない（検査が空振りしている）")
                .isNotEmpty();

        Set<String> existing = new HashSet<>();
        try (Stream<Path> paths = Files.walk(REPOSITORY_ROOT.resolve("apps/cargo-tracker/backend"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .forEach(path -> existing.add(
                            path.getFileName().toString().replace(".java", "")));
        }

        List<String> missing = new ArrayList<>(declared);
        missing.removeAll(existing);
        assertThat(missing)
                .as("計画が名指しした検査が実在しない。書いただけの検査は何も守らない")
                .isEmpty();
    }

    @Test
    @DisplayName("計画が名指しした検査（フロントエンド）が実在する")
    void frontendChecksNamedInThePlanExist() throws IOException {
        Set<String> declared = namesIn(FRONTEND_CHECK_NAME);
        assertThat(declared).isNotEmpty();

        Set<String> existing = new HashSet<>();
        try (Stream<Path> paths =
                Files.walk(REPOSITORY_ROOT.resolve("apps/cargo-tracker/frontend/src"))) {
            paths.filter(path -> path.toString().endsWith(".tsx")
                            || path.toString().endsWith(".ts"))
                    .forEach(path -> existing.add(path.getFileName().toString()));
        }

        List<String> missing = new ArrayList<>(declared);
        missing.removeAll(existing);
        assertThat(missing)
                .as("計画が名指しした画面の検査が実在しない")
                .isEmpty();
    }

    private static Set<String> namesIn(Pattern pattern) throws IOException {
        String plan = Files.readString(PLAN_DIR.resolve(CURRENT_PLAN), StandardCharsets.UTF_8);
        Set<String> names = new HashSet<>();
        Matcher matcher = pattern.matcher(plan);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
