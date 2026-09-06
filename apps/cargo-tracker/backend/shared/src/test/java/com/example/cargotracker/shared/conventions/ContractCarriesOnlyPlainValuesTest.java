package com.example.cargotracker.shared.conventions;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

/**
 * 契約は文字列・数値・日付だけを運ぶ（ADR-0001 / domain-model.md「置かないもの」）。
 *
 * <p><b>列挙型を載せない。</b> {@code CargoType}・{@code TransportStatus}・
 * {@code BookingStatus} は、同じ名前でも BC ごとに値と意味が違う。契約に載せると
 * 片方の BC が値を足しただけで、もう一方が復元できなくなる。</p>
 *
 * <p><b>識別子型も載せない。</b> {@code TrackingNumber}・{@code VoyageNumber} は
 * BC ごとに別の型で、それぞれの不変条件を持つ。契約に載せると、片方の BC の都合で
 * 契約が動く。</p>
 *
 * <p><b>名簿方式にしない。</b> 走査で対象を導く。手書きの名簿は、載せ忘れたものほど
 * 漏れる。</p>
 */
class ContractCarriesOnlyPlainValuesTest {

    private static final String CONTRACT = "com.example.cargotracker.shared.contract";

    /** 契約に載せてよい型。ここを広げるときは ADR-0001 も同じ変更で直す。 */
    private static final Set<Class<?>> PLAIN = Set.of(
            String.class, int.class, Integer.class, long.class, Long.class,
            boolean.class, Boolean.class, double.class, Double.class,
            BigDecimal.class, Instant.class, LocalDate.class);

    private static List<Class<?>> contractRecords() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        var factory = new CachingMetadataReaderFactory(resolver);
        var found = new java.util.ArrayList<Class<?>>();
        for (var resource : resolver.getResources(
                "classpath*:" + CONTRACT.replace('.', '/') + "/**/*.class")) {
            String name = factory.getMetadataReader(resource).getClassMetadata().getClassName();
            Class<?> type = Class.forName(name);
            if (type.isRecord()) {
                found.add(type);
            }
        }
        return List.copyOf(found);
    }

    @Test
    @DisplayName("契約に列挙型・識別子型を載せていない（入れ子も見る）")
    void carriesOnlyPlainValues() throws Exception {
        List<Class<?>> records = contractRecords();
        assertThat(records)
                .as("契約が 1 つも見つからないなら、検査は「守っている」ではなく「調べていない」")
                .hasSizeGreaterThanOrEqualTo(4);

        for (Class<?> type : records) {
            for (RecordComponent component : type.getRecordComponents()) {
                assertComponentIsPlain(type, component);
            }
        }
    }

    private static void assertComponentIsPlain(Class<?> owner, RecordComponent component) {
        Class<?> raw = component.getType();
        if (List.class.equals(raw)) {
            // List の中身まで見る。要素だけ列挙型にした違反を素通りさせない。
            Class<?> element = ResolvableType.forType(component.getGenericType())
                    .getGeneric(0).resolve();
            assertPlain(owner, component.getName() + " の要素", element);
            return;
        }
        assertPlain(owner, component.getName(), raw);
    }

    private static void assertPlain(Class<?> owner, String where, Class<?> type) {
        if (type != null && type.isRecord()
                && type.getName().startsWith(CONTRACT)) {
            // 契約の中の入れ子（LegDto など）は、それ自体が走査対象なので通す。
            return;
        }
        assertThat(PLAIN)
                .as("%s の %s に %s を載せている。契約は文字列・数値・日付だけを運ぶ",
                        owner.getSimpleName(), where, type)
                .contains(type);
    }
}
