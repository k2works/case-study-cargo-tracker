package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTypeTest {

    @Test
    void 遅延例外は緊急フラグ不要() {
        assertThat(ExceptionType.DELAY.isEscalationRequired()).isFalse();
    }

    @Test
    void 破損例外は緊急フラグ不要() {
        assertThat(ExceptionType.DAMAGE.isEscalationRequired()).isFalse();
    }

    @Test
    void 紛失例外は緊急フラグが必要() {
        assertThat(ExceptionType.LOST.isEscalationRequired()).isTrue();
    }

    @Test
    void 各例外種別の表示名が正しい() {
        assertThat(ExceptionType.DELAY.getDisplayName()).isEqualTo("遅延");
        assertThat(ExceptionType.DAMAGE.getDisplayName()).isEqualTo("破損");
        assertThat(ExceptionType.LOST.getDisplayName()).isEqualTo("紛失");
    }
}
