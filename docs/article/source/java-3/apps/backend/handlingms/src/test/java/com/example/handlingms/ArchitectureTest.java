package com.example.handlingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.handlingms.domain.model.aggregates.CustomsDeclaration;
import com.example.shared.architecture.ServiceArchitectureTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * handlingms のアーキテクチャ規則。
 *
 * <p>規則の実体と適用は shared の {@link ServiceArchitectureTest} にあり、ここで並べない。
 * 並べる形にすると、規則を足したときに写し漏れたサービスが無検査のまま残る（IT6 の実例）。
 * このクラスが存在しない・基底を継承していないサービスは、shared の
 * ArchitectureRuleCoverageTest が検出する。
 */
class ArchitectureTest extends ServiceArchitectureTest {

    @Override
    protected String serviceName() {
        return "handlingms";
    }

    /**
     * <strong>[ADR-025] 決定 8。識別子は既存の値オブジェクトで持つ。</strong>
     *
     * <p>素の {@code String} に戻すと、ACL で型に変換している意味が消える。
     * handlingms は {@code CargoSnapshot} を通じて予約 ID を受け取っており、
     * そこで型にした値を集約が文字列へ戻すと、変換は「通り道の飾り」になる。
     *
     * <p><strong>{@code declarationId} は対象外である。</strong>サロゲートキーであり、
     * 守る規則が「空でない」だけ——値オブジェクトにする基準（[ADR-012]）を満たさない。
     * 除外を名指しで書くのは、<strong>あとから「なぜここだけ素なのか」を読めるように</strong>
     * するためである。
     */
    @Test
    @DisplayName("通関申告の識別子は、素の String で持たない")
    void customsDeclarationUsesExistingValueObjects() {
        List<String> rawIdentifiers = Arrays.stream(CustomsDeclaration.class.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getType() == String.class)
                .map(java.lang.reflect.Field::getName)
                // 業務の文章であって識別子ではないものは対象外
                .filter(name -> !"remarks".equals(name))
                .toList();

        assertThat(rawIdentifiers)
                .as("識別子が素の String で持たれている。ACL で型に変換した意味が消える")
                .isEmpty();

        // declarationId はサロゲートキーであり、値オブジェクトにしない（[ADR-012]）
        assertThat(Arrays.stream(CustomsDeclaration.class.getDeclaredFields())
                        .filter(field -> "id".equals(field.getName()))
                        .map(field -> field.getType().getSimpleName())
                        .toList())
                .as("サロゲートキーの型が変わっている。決定 8 の除外はここだけである")
                .containsExactly("Long");
    }
}
