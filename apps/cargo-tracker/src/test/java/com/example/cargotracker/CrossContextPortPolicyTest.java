package com.example.cargotracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.support.SourceScan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BC 越しに<strong>状態を変える</strong>同期ポートは、
 * <strong>失敗が届く先を名簿に書く</strong>（ADR-021）。
 *
 * <p>IT14 で、入金確認の後に予約を精算済みにする {@code settle} の戻り値が
 * 捨てられていた。<strong>「入金確認済みだが予約が精算済みでない」請求書が
 * ログにも画面にも残らない。</strong> その予約は精算後も引取記録を訂正できてしまう
 * （US36 のガードが {@code booking_status} に依存しているため）。
 * テストは全緑、SonarQube も PASS のまま、レビューで初めて出た。
 *
 * <p><strong>「戻り値を使っているか」では守れない。</strong> ログに書くだけでも
 * 「使っている」と言えてしまう。<strong>問うべきは、できなかったことが
 * 人に届くかである。</strong>
 *
 * <p>だから名簿にする。<strong>状態を変えるポートを新設した瞬間に問いが立ち、
 * 答えを書くまで赤になる。</strong> レビューまで待たない
 * （C16 でダッシュボードの案内をロール列挙から名簿に変えたのと同じ形）。
 */
@DisplayName("状態を変える ACL ポートは失敗の届け先を名簿に書く（ADR-021）")
class CrossContextPortPolicyTest {

    /**
     * <strong>問い合わせの接頭辞。</strong> これで始まるメソッドは状態を変えない。
     *
     * <p><strong>語の切れ目で見る。</strong> 単なる前方一致にすると
     * {@code issue}（追跡番号の発行）が {@code is} に一致し、
     * <strong>状態を変えるポートが問い合わせとして素通りする</strong>。
     * 実際に最初の実装がそうなっており、名簿との突き合わせで気づいた。
     *
     * <p><strong>名前で判定することの弱さは承知している。</strong> {@code findAndDelete}
     * のような名前を付ければすり抜ける。だが<strong>そういう名前を付けたこと自体が
     * レビューで見える</strong>のに対し、名簿の空欄は見えない。守る対象を選んでいる。
     */
    private static final Pattern QUERY_NAME = Pattern.compile(
            "^(?:find|is|has|get|count|exists)(?:[A-Z].*)?$");

    /**
     * <strong>状態を変える同期ポートの名簿</strong>（ADR-021）。
     *
     * <p>鍵は {@code ポート名.メソッド名}、値は
     * <strong>「なぜ同期か」と「失敗がどこに出るか」</strong>である。
     *
     * <p><strong>ここに書けないなら、それはイベントにすべき経路である。</strong>
     * 「操作している人が何もできない」なら、同期にする理由が無い。
     */
    private static final Map<String, String> SYNCHRONOUS_COMMANDS = new LinkedHashMap<>();

    static {
        SYNCHRONOUS_COMMANDS.put("BookingSettlementPort.settle",
                "US23 の受入基準そのもの（入金確認後に予約状態も精算済になる）。"
                        + "経理担当者がその場で気づいて手を打てる。"
                        + "失敗は請求書詳細の警告と監査ログに出る");
        SYNCHRONOUS_COMMANDS.put("InvoiceNotificationPort.notifyIssued",
                "「請求書が届いていない」と言われたときに答えるための記録。"
                        + "宛先が無いことは経理担当者が直せる。"
                        + "失敗は請求書詳細の警告と監査ログに出る");
        SYNCHRONOUS_COMMANDS.put("TrackingPort.issue",
                "追跡番号を発行できたかは営業担当者の操作の結果そのものである。"
                        + "失敗は画面のメッセージに出る");
        SYNCHRONOUS_COMMANDS.put("CargoRouteAssignments.assign",
                "拒否の理由（端点不一致・状態不正）を経路設計者にその場で返す必要がある。"
                        + "失敗は画面のメッセージに出る");
    }

    /**
     * インタフェース直下のメソッド宣言（本体を持たないもの）。
     *
     * <p>ネストしたレコードとその中のメソッドは本体（<code>{</code>）を持つため
     * 一致しない。
     */
    private static final Pattern PORT_METHOD = Pattern.compile(
            "\\n {4}(?:[\\w.<>,\\[\\] ]+?) (\\w+)\\(([^;{]*)\\);");

    /**
     * <strong>状態を変えるポートはすべて名簿にある。</strong>
     *
     * <p>違反があれば<strong>ポート名・メソッド名</strong>を並べて落とす。
     */
    @Test
    void 状態を変えるポートは名簿に載っている() throws IOException {
        List<String> unregistered = new ArrayList<>();
        for (Path port : portFiles()) {
            String portName = port.getFileName().toString().replace(".java", "");
            for (String method : commandMethodsIn(Files.readString(port))) {
                String key = portName + "." + method;
                if (!SYNCHRONOUS_COMMANDS.containsKey(key)) {
                    unregistered.add(key);
                }
            }
        }

        assertThat(unregistered)
                .as("""
                        BC 越しに状態を変える ACL ポートが名簿にありません（ADR-021）。
                        「なぜ同期か」と「失敗がどこに出るか」を書いてください。
                        **書けないなら、それはドメインイベントにすべき経路です。**
                        操作している人が何もできないなら、同期にする理由がありません。""")
                .isEmpty();
    }

    /**
     * <strong>名簿に幽霊を残さない。</strong>
     *
     * <p>ポートを消しても名簿の行が残ると、<strong>次に読む人は
     * まだ同期の経路があると思い込む</strong>。
     */
    @Test
    void 名簿に実在しないポートを残さない() throws IOException {
        Set<String> actual = new LinkedHashSet<>();
        for (Path port : portFiles()) {
            String portName = port.getFileName().toString().replace(".java", "");
            for (String method : commandMethodsIn(Files.readString(port))) {
                actual.add(portName + "." + method);
            }
        }

        assertThat(SYNCHRONOUS_COMMANDS.keySet())
                .as("実在しないポートが名簿に残っています")
                .isSubsetOf(actual);
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong>「最小の違反例」だけだと、
     * メタテストが緑でも実コードの違反を見逃す。ここでは実際のポートの形
     * （Javadoc つき・複数行にまたがる引数・ネストしたレコード）をそのまま使う。
     */
    @Test
    void 実コードの形の状態変更を検出できる() {
        String realShapedPort = """
                public interface InvoiceNotificationPort {

                    /**
                     * 精算書を発行したことを伝えて記録する。
                     *
                     * @return 記録できたなら {@code true}
                     */
                    boolean notifyIssued(
                            String bookingId, String invoiceNumber, BigDecimal totalAmount,
                            LocalDate dueDate, String actor);

                    /** 発行済みの精算書を引く。 */
                    List<Summary> findIssued(String shipperId);

                    record Summary(String invoiceNumber, BigDecimal amount) {
                        public boolean isLarge() {
                            return amount.signum() > 0;
                        }
                    }
                }
                """;

        assertThat(commandMethodsIn(realShapedPort))
                .as("複数行にまたがる宣言を拾い、問い合わせとネストしたレコードは拾わないこと")
                .containsExactly("notifyIssued");
    }

    /** 状態を変えるメソッド名（問い合わせの接頭辞で始まらないもの）。 */
    private static Set<String> commandMethodsIn(String source) {
        Set<String> commands = new LinkedHashSet<>();
        Matcher matcher = PORT_METHOD.matcher("\n" + source);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!QUERY_NAME.matcher(name).matches()) {
                commands.add(name);
            }
        }
        return commands;
    }

    /**
     * 走査対象。<strong>空なら検査は何も見ていない</strong>。
     *
     * <p>走査の根が変わったときに<strong>静かに 0 件になり、緑のまま何も検査しない</strong>
     * 形を防ぐ（R8 で {@link SourceScan} へ寄せた際に歯止めを足した）。
     */
    private static List<Path> portFiles() {
        List<Path> found = portFilesScan();
        if (found.isEmpty()) {
            throw new AssertionError("ACL ポート が 1 つも見つかりません。検査は何も見ていません");
        }
        return found;
    }

    private static List<Path> portFilesScan() {
        return SourceScan.main().excluding("package-info.java").files().stream()
                .filter(p -> p.toString().replace('\\', '/').contains("outboundservices/acl"))
                .toList();
    }
}
