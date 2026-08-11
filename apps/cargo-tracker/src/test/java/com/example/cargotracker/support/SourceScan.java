package com.example.cargotracker.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * <strong>ソースを走査する検査の土台</strong>（IT17 の R8 / R9）。
 *
 * <p>走査する検査は IT17 の時点で 10 本あり、いずれも「{@code Files.walk} して
 * {@code .java} を集める」「自分を除外する」を各自で書いていた。IT16 では
 * <strong>その自己除外を 3 度書き忘れ、3 度とも症状が出てから直した。</strong>
 *
 * <p>本クラスは<strong>自分の除外を既定にする</strong>。{@link #main()} / {@link #test()}
 * は呼び出し元のテストクラスのファイルを自動で外すため、<strong>書き忘れても起きない。</strong>
 *
 * <p>{@link SourceFile#code()} はコメントと文字列リテラルを空白に置き換えて返す（R9）。
 * <strong>行番号は保たれる</strong>ため、違反の場所を指す用途はそのまま使える。
 * SQL のように<strong>文字列リテラルの中身こそが検査対象</strong>である検査は
 * {@link SourceFile#text()} を使う — <strong>どちらが正しいかは検査ごとに違う。</strong>
 *
 * @see SourceScanTest 本クラス自身の検査（メタテスト）
 */
public final class SourceScan {

    /** 本番コードの根。 */
    public static final Path MAIN_ROOT = Path.of("src/main/java");

    /** テストコードの根。 */
    public static final Path TEST_ROOT = Path.of("src/test/java");

    private final List<Path> roots;
    private final Set<String> excluded;

    private SourceScan(List<Path> roots, Set<String> excluded) {
        this.roots = roots;
        this.excluded = excluded;
    }

    /** 本番コードを走査する。呼び出し元のテスト自身は除外される。 */
    public static SourceScan main() {
        return of(MAIN_ROOT);
    }

    /** テストコードを走査する。呼び出し元のテスト自身は除外される。 */
    public static SourceScan test() {
        return of(TEST_ROOT);
    }

    /** 本番コードとテストコードの両方を走査する。呼び出し元のテスト自身は除外される。 */
    public static SourceScan mainAndTest() {
        return of(MAIN_ROOT, TEST_ROOT);
    }

    /** 指定した根を走査する。呼び出し元のテスト自身は除外される。 */
    public static SourceScan of(Path... roots) {
        return new SourceScan(List.of(roots), new LinkedHashSet<>(Set.of(callerFileName())));
    }

    /** 除外するファイル名を足す（支援クラス自身など、正当な例外を明示する）。 */
    public SourceScan excluding(String... fileNames) {
        Set<String> next = new LinkedHashSet<>(excluded);
        next.addAll(List.of(fileNames));
        return new SourceScan(roots, next);
    }

    /** ファイル名の接尾辞で絞り込んだ走査を返す。 */
    public List<Path> filesEndingWith(String suffix) {
        return files().stream()
                .filter(p -> p.getFileName().toString().endsWith(suffix))
                .toList();
    }

    /** 走査対象の {@code .java} ファイル。 */
    public List<Path> files() {
        List<Path> found = new java.util.ArrayList<>();
        for (Path declared : roots) {
            Path root = resolve(declared);
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !excluded.contains(p.getFileName().toString()))
                        .sorted()
                        .forEach(found::add);
            } catch (IOException e) {
                throw new UncheckedIOException("ソースを走査できません: " + root, e);
            }
        }
        return List.copyOf(found);
    }

    /** 走査対象のソース（パスと本文）。 */
    public List<SourceFile> sources() {
        return files().stream().map(SourceFile::read).toList();
    }

    /** ソース 1 ファイル。 */
    public record SourceFile(Path path, String text) {

        static SourceFile read(Path path) {
            try {
                return new SourceFile(path, Files.readString(path));
            } catch (IOException e) {
                throw new UncheckedIOException("ソースを読めません: " + path, e);
            }
        }

        /** ファイル名（{@code Xxx.java}）。 */
        public String fileName() {
            return path.getFileName().toString();
        }

        /**
         * コメントと文字列リテラルを空白に置き換えた本文（R9）。
         *
         * <p>行番号は保たれる。
         */
        public String code() {
            return codeOf(text);
        }
    }

    /**
     * コメント・文字列リテラル・文字リテラルの中身を空白に置き換える（R9）。
     *
     * <p>改行はそのまま残すため、<strong>行番号がずれない</strong>。
     */
    public static String codeOf(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            int next = blankTokenAt(source, out, i);
            if (next < 0) {
                out.append(source.charAt(i));
                i++;
            } else {
                i = next;
            }
        }
        return out.toString();
    }

    /**
     * 位置 {@code i} から始まるコメント・リテラルを空白化する。
     *
     * @return 空白化した次の位置。コメントでもリテラルでもなければ {@code -1}
     */
    private static int blankTokenAt(String source, StringBuilder out, int i) {
        if (source.startsWith("//", i)) {
            return blankUntil(source, out, i, "\n", false);
        }
        if (source.startsWith("/*", i)) {
            out.append("  ");
            return blankUntil(source, out, i + 2, "*/", true);
        }
        if (source.startsWith("\"\"\"", i)) {
            out.append("\"\"\"");
            int end = blankUntil(source, out, i + 3, "\"\"\"", true);
            out.append("\"\"\"");
            return end;
        }
        char c = source.charAt(i);
        if (c == '"' || c == '\'') {
            out.append(c);
            int end = blankQuoted(source, out, i + 1, c);
            out.append(c);
            return end;
        }
        return -1;
    }

    /** {@code end} の手前まで空白化する。{@code consumeEnd} なら終端も空白化して進む。 */
    private static int blankUntil(String source, StringBuilder out, int from, String end,
            boolean consumeEnd) {
        int i = from;
        while (i < source.length() && !source.startsWith(end, i)) {
            out.append(source.charAt(i) == '\n' ? '\n' : ' ');
            i++;
        }
        if (consumeEnd && i < source.length()) {
            i += end.length();
        }
        return i;
    }

    /** 引用符の中身をエスケープを考慮して空白化する。閉じ引用符の次の位置を返す。 */
    private static int blankQuoted(String source, StringBuilder out, int from, char quote) {
        int i = from;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\' && i + 1 < source.length()) {
                out.append("  ");
                i += 2;
                continue;
            }
            if (c == quote || c == '\n') {
                break;
            }
            out.append(' ');
            i++;
        }
        return i < source.length() && source.charAt(i) == quote ? i + 1 : i;
    }

    /**
     * 根を実在するディレクトリに解決する。
     *
     * <p>作業ディレクトリはモジュール直下のことも、リポジトリのルートのこともある。
     * <strong>片方だけを前提にすると、実行の仕方で結果が変わる</strong>
     * （{@code MapperTableOwnershipTest} が単独で持っていた配慮を、全検査に広げた）。
     */
    private static Path resolve(Path declared) {
        if (Files.isDirectory(declared)) {
            return declared;
        }
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && current != null; i++) {
            for (Path candidate : List.of(current.resolve(declared),
                    current.resolve("apps/cargo-tracker").resolve(declared))) {
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
            current = current.getParent();
        }
        throw new IllegalStateException("走査する根が見つかりません: " + declared);
    }

    /** 呼び出し元（本クラス以外で最初に現れるクラス）のソースファイル名。 */
    private static String callerFileName() {
        return StackWalker.getInstance()
                .walk(frames -> frames
                        .map(StackWalker.StackFrame::getClassName)
                        .filter(name -> !name.equals(SourceScan.class.getName()))
                        .findFirst()
                        .orElseThrow())
                .replaceAll("\\$.*$", "")
                .replaceAll("^.*\\.", "") + ".java";
    }
}
