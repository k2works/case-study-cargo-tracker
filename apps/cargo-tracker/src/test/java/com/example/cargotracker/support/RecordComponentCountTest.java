package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <strong>レコードの要素は 7 個までとする</strong>（IT14 の M9 / IT15 の M5。C3）。
 *
 * <p><strong>Checkstyle の {@code ParameterNumber}（max 7）はレコードを見ない。</strong>
 * 規則は宣言されているのに、<strong>対象が検査の視野の外にあった</strong>。
 * {@code RECORD_DEF} トークンでの有効化を試したが、本プロジェクトの Checkstyle 版は
 * 受け付けない。<strong>設定で守れないなら、書いて守る。</strong>
 *
 * <p>視野の外だったあいだに何が起きたか。{@code InvoiceView} は IT13 の 21 個から
 * IT16 の 27 個まで、<strong>誰にも止められずに育った</strong>。
 * 同型の引数が一列に並ぶと、<strong>位置を取り違えてもコンパイルが通る</strong>。
 * 並びが長いほど、足すときに「どこへ足すか」を考えなくなる。
 *
 * <p>ADR-009 の規則 1（{@code AFTER_COMMIT}）が既存の検査から見えなかったのと同じ形である
 * — <strong>宣言はされているが、対象に届いていない。</strong>
 */
@DisplayName("レコードの要素は 7 個までとする（C3）")
class RecordComponentCountTest {

    private static final int MAX_COMPONENTS = 7;

    /*
     * 本検査はメタテストの中に違反の形を文字列として持つ。除外しないと自分を違反として
     * 数える — この自己参照は IT16 で 3 度踏んだ。SourceScan が呼び出し元を既定で
     * 外すため、除外をここに書くことはもう無い（R8）。
     */

    /** {@code record 名前(...)} のヘッダ。ネストしたレコードも拾う。 */
    private static final Pattern RECORD_HEADER =
            Pattern.compile("record\\s+(\\w+)\\s*\\(([^)]*(?:\\([^)]*\\)[^)]*)*)\\)");

    /**
     * <strong>まだ分けていないレコードと、その要素数</strong>（IT16 時点の負債）。
     *
     * <p><strong>C3 の記録は実態より小さかった。</strong> 引き継ぎは
     * 「{@code InvoiceView} 26 個・{@code CancellationView} 19 個」の 2 件としていたが、
     * 数えると <strong>21 件</strong>あり、最大は {@code BookingView} の
     * <strong>35 要素</strong>だった。C2（7 → 39 クラス）とまったく同じ形である
     * — <strong>数え方が違えば、落とす判断の前提も違う。</strong>
     *
     * <p><strong>黙って通すのではなく、名前と数で残す</strong>（ADR-015 の {@code ALLOWED} と
     * 同じ形）。<strong>この表は縮む一方であるべきものである。</strong>
     * 要素を増やせば検査が落ち、分ければ表から消すことを別の検査が求める。
     */
    private static final Map<String, Integer> NOT_SPLIT_YET = new LinkedHashMap<>();

    static {
        // **数を書く。** 「大きい」ではなく「35 要素」と書けば、次に読む人は
        // それが 21 件のうちどれだけ重いかを判断できる。
        // **増やしたら検査が落ちる**（分けたら表からも消す）
        NOT_SPLIT_YET.put("CancellationView", 19);
        NOT_SPLIT_YET.put("ShipperView", 16);
        NOT_SPLIT_YET.put("BillableCargoSummary", 14);
        NOT_SPLIT_YET.put("HandlingActivityView", 13);
        NOT_SPLIT_YET.put("RouteProposalView", 13);
        NOT_SPLIT_YET.put("Candidate", 13);
        NOT_SPLIT_YET.put("TrackingExceptionView", 13);
        NOT_SPLIT_YET.put("CustomsDeclarationView", 12);
        NOT_SPLIT_YET.put("CorrectionRequestView", 11);
        NOT_SPLIT_YET.put("VoyageView", 11);
        NOT_SPLIT_YET.put("PendingCargoView", 10);
        NOT_SPLIT_YET.put("Request", 9);
        NOT_SPLIT_YET.put("CustomsStatusChangedEvent", 9);
        NOT_SPLIT_YET.put("CorrectionSummary", 8);
        NOT_SPLIT_YET.put("BookingNotificationView", 8);
        NOT_SPLIT_YET.put("NotificationContent", 8);
        NOT_SPLIT_YET.put("RoutableBooking", 8);
        NOT_SPLIT_YET.put("CargoExceptionRaisedEvent", 8);
        NOT_SPLIT_YET.put("TrackingInquiryView", 8);
    }

    /**
     * <strong>要素が 8 個以上のレコードを作らない。</strong>
     *
     * <p>違反があればレコード名と要素数を並べて落とす。
     */
    @Test
    void レコードの要素は七個までである() {
        List<String> violations = new ArrayList<>();
        for (SourceScan.SourceFile source : SourceScan.mainAndTest().sources()) {
            {
                for (Map.Entry<String, Integer> found : recordsIn(source.code())) {
                    Integer allowed = NOT_SPLIT_YET.get(found.getKey());
                    if (found.getValue() <= MAX_COMPONENTS) {
                        continue;
                    }
                    if (allowed != null && found.getValue() <= allowed) {
                        // **据え置きは「いまの数まで」である。** 増やすなら分ける
                        continue;
                    }
                    violations.add("%s（%d 要素・%s）"
                            .formatted(found.getKey(), found.getValue(), source.fileName()));
                }
            }
        }

        assertThat(violations)
                .as("""
                        レコードの要素が上限（7 個）を超えています（C3）。

                        **同型の引数が一列に並ぶと、位置を取り違えてもコンパイルが通ります。**
                        意味のまとまりごとに入れ子のレコードへ分けてください。
                        画面が呼ぶ名前は委譲するアクセサで残せます。

                        Checkstyle の ParameterNumber はレコードを見ないため、
                        この検査が代わりに数えています。""")
                .isEmpty();
    }

    /**
     * <strong>据え置きの表に、もう分けたものを残さない。</strong>
     *
     * <p>返したのに名前が残っていると、<strong>表が縮んでいないように見える</strong>。
     */
    @Test
    void 据え置きの表に分割済みのものを残さない() {
        List<String> stale = new ArrayList<>();
        for (SourceScan.SourceFile source : SourceScan.mainAndTest().sources()) {
            {
                for (Map.Entry<String, Integer> found : recordsIn(source.code())) {
                    if (NOT_SPLIT_YET.containsKey(found.getKey())
                            && found.getValue() <= MAX_COMPONENTS) {
                        stale.add(found.getKey());
                    }
                }
            }
        }

        assertThat(stale)
                .as("分けたレコードが据え置きの表に残っています。表から消してください")
                .isEmpty();
    }

    /**
     * <strong>検査そのものが働くことを確かめる</strong>（メタテスト）。
     *
     * <p><strong>フィクスチャは実コードの形で作る。</strong> 「最小の違反例」だけだと、
     * メタテストが緑でも実コードの違反を見逃す（ADR-015 で学んだ形）。
     * ここでは是正前の {@code InvoiceView} の形（コメント混じり・入れ子の型）を使う。
     */
    @Test
    void 実コードの形のレコードを数えられる() {
        String realShaped = """
                public record InvoiceView(
                        String invoiceNumber,
                        String bookingId,
                        String trackingNumber,
                        String shipperName,
                        /* 荷主 ID。連絡先を引くための鍵であり、画面には出さない */
                        String shipperId,
                        BigDecimal baseAmount,
                        BigDecimal discountPercent,
                        BigDecimal totalAmount) {
                }
                """;

        assertThat(recordsIn(realShaped))
                .as("コメントを挟んでも要素を数えられること")
                .anySatisfy(e -> {
                    assertThat(e.getKey()).isEqualTo("InvoiceView");
                    assertThat(e.getValue()).isEqualTo(8);
                });

        assertThat(recordsIn("public record Identity(String a, String b) {\n}\n"))
                .as("分けた形を違反にしないこと（常に落ちる検査で緑にしない）")
                .allSatisfy(e -> assertThat(e.getValue()).isLessThanOrEqualTo(MAX_COMPONENTS));
    }

    /** ソースに含まれるレコードの名前と要素数。 */
    private static List<Map.Entry<String, Integer>> recordsIn(String source) {
        List<Map.Entry<String, Integer>> found = new ArrayList<>();
        Matcher matcher = RECORD_HEADER.matcher(source);
        while (matcher.find()) {
            found.add(Map.entry(matcher.group(1), countComponents(matcher.group(2))));
        }
        return found;
    }

    /**
     * 要素の数を数える。
     *
     * <p><strong>入れ子の括弧の中のカンマを数えない</strong>（{@code Map<String, String>} など）。
     * コメントは先に取り除く。
     */
    private static int countComponents(String header) {
        String cleaned = header.replaceAll("/\\*.*?\\*/", " ").trim();
        if (cleaned.isEmpty()) {
            return 0;
        }
        int depth = 0;
        int count = 1;
        for (char c : cleaned.toCharArray()) {
            depth += nesting(c);
            if (c == ',' && depth == 0) {
                count++;
            }
        }
        return count;
    }

    /** 入れ子の深さの増減（開き括弧で +1、閉じ括弧で −1、それ以外は 0）。 */
    private static int nesting(char c) {
        if ("<([".indexOf(c) >= 0) {
            return 1;
        }
        return ">)]".indexOf(c) >= 0 ? -1 : 0;
    }

}
