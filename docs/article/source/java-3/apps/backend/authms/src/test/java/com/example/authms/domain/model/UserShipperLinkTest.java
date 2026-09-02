package com.example.authms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 利用者と荷主の紐付け（US33）。 */
@DisplayName("利用者と荷主の紐付け")
class UserShipperLinkTest {

    @Test
    @DisplayName("利用者 ID と荷主 ID を保持する")
    void keepsTheExplicitLink() {
        UserShipperLink link = new UserShipperLink("shipper01", 1L);

        assertThat(link.username()).isEqualTo("shipper01");
        assertThat(link.shipperId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("利用者 ID は必須")
    void requiresUsername() {
        assertThatThrownBy(() -> new UserShipperLink(" ", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("荷主 ID は正の値")
    void requiresPositiveShipperId() {
        assertThatThrownBy(() -> new UserShipperLink("shipper01", 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
