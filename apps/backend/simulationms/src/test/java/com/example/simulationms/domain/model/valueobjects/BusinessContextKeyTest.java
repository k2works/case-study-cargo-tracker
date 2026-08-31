package com.example.simulationms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 引き継ぐ識別子の名前と、その和名。
 *
 * <p><strong>和名は「何番号か」を人へ伝えるためにある。</strong>実行結果の画面は
 * 素の文字列を並べるだけだったため、管理者は工程名から番号の種別を推測していた。
 * 現場では管理者が自分で開くのではなく<strong>営業に番号を伝える</strong>ので、
 * 種別が読めないと伝えられない。
 */
@DisplayName("引き継ぐ識別子の名前")
class BusinessContextKeyTest {

    @Test
    @DisplayName("すべての名前に和名がある")
    void everyKeyHasALabel() {
        List<String> keys = declaredKeys();

        assertThat(keys).as("名前が 1 つも読み取れていない場合、この検査は何も守らない")
                .isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (BusinessContextKey.NONE.equals(key)) {
                continue;
            }
            String label = BusinessContextKey.labelOf(key);
            if (label == null || label.isBlank()) {
                missing.add(key);
            }
        }

        assertThat(missing).as("和名が無い識別子。足した名前は名乗り出ないので、"
                + "ここで全件を回して確かめる").isEmpty();
    }

    @Test
    @DisplayName("何も生まない工程には和名が無い")
    void noneHasNoLabel() {
        assertThat(BusinessContextKey.labelOf(BusinessContextKey.NONE)).isNull();
    }

    @Test
    @DisplayName("知らない名前は素通りさせない")
    void rejectsAnUnknownKey() {
        assertThatThrownBy(() -> BusinessContextKey.labelOf("unknownKey"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknownKey");
    }

    /** 定数を書き写さず、クラスから読む。写すと、足した名前が検査から漏れる。 */
    private static List<String> declaredKeys() {
        List<String> keys = new ArrayList<>();
        for (Field field : BusinessContextKey.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())
                    && Modifier.isStatic(field.getModifiers())
                    && field.getType() == String.class) {
                try {
                    keys.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(field.getName(), e);
                }
            }
        }
        return keys;
    }
}
