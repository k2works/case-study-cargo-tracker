package com.example.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Location")
class LocationTest {

    @Nested
    @DisplayName("生成")
    class Creation {

        @Test
        @DisplayName("UN/LOCODE と名称から生成できる")
        void createsFromUnLocodeAndName() {
            Location tokyo = Location.of("JPTYO", "Tokyo");

            assertThat(tokyo.unLocode()).isEqualTo("JPTYO");
            assertThat(tokyo.name()).isEqualTo("Tokyo");
        }

        @Test
        @DisplayName("UN/LOCODE が 5 文字でない場合は拒否する")
        void rejectsUnLocodeOfWrongLength() {
            assertThatThrownBy(() -> Location.of("JPTY", "Tokyo"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("UN/LOCODE");
        }

        @Test
        @DisplayName("UN/LOCODE の国コードが英字でない場合は拒否する")
        void rejectsNonAlphabeticCountryCode() {
            assertThatThrownBy(() -> Location.of("1PTYO", "Tokyo"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("UN/LOCODE が未指定の場合は拒否する")
        void rejectsNullUnLocode() {
            assertThatThrownBy(() -> Location.of(null, "Tokyo"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("UN/LOCODE");
        }

        @Test
        @DisplayName("名称が未指定の場合は拒否する")
        void rejectsNullName() {
            assertThatThrownBy(() -> Location.of("JPTYO", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("名称");
        }

        @Test
        @DisplayName("名称が空の場合は拒否する")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Location.of("JPTYO", " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("名称");
        }
    }

    @Nested
    @DisplayName("同一性")
    class Identity {

        @Test
        @DisplayName("UN/LOCODE が同じなら等価とみなす")
        void equalWhenUnLocodeMatches() {
            assertThat(Location.of("JPTYO", "Tokyo"))
                    .isEqualTo(Location.of("JPTYO", "東京"))
                    .hasSameHashCodeAs(Location.of("JPTYO", "東京"));
        }

        @Test
        @DisplayName("UN/LOCODE が異なれば等価ではない")
        void notEqualWhenUnLocodeDiffers() {
            assertThat(Location.of("JPTYO", "Tokyo"))
                    .isNotEqualTo(Location.of("USNYC", "New York"));
        }

        @Test
        @DisplayName("自分自身とは等価であり、Location でないものとは等価ではない")
        void comparesWithSelfAndOtherTypes() {
            Location tokyo = Location.of("JPTYO", "Tokyo");

            assertThat(tokyo).isEqualTo(tokyo).isNotEqualTo("JPTYO").isNotEqualTo(null);
        }
    }

    @Nested
    @DisplayName("表示")
    class Display {

        @Test
        @DisplayName("名称と UN/LOCODE を併記して表示する")
        void showsNameWithUnLocode() {
            assertThat(Location.of("JPTYO", "Tokyo")).hasToString("Tokyo (JPTYO)");
        }
    }
}
