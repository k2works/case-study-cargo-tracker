package com.example.bookingms.application.internal;

import com.example.bookingms.domain.model.Shipper;

/**
 * 荷主登録の結果。
 *
 * <p>重複を例外にしないのは、それが失敗ではなく利用者への問いかけだから。営業担当者は
 * 既存の荷主を使うか、別の荷主として登録するかを、その場の事情で判断する。
 */
public sealed interface RegistrationOutcome {

    record Registered(Shipper shipper) implements RegistrationOutcome {
    }

    record DuplicateFound(Shipper existing) implements RegistrationOutcome {
    }
}
