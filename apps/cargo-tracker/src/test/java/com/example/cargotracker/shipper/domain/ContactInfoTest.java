package com.example.cargotracker.shipper.domain;

import com.example.cargotracker.shipper.domain.model.valueobjects.ContactInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ContactInfo")
class ContactInfoTest {

    @Test
    @DisplayName("メールアドレスと電話番号から ContactInfo を生成できる")
    void createContactInfo() {
        ContactInfo contact = new ContactInfo("test@example.com", "090-1234-5678");
        assertThat(contact.email()).isEqualTo("test@example.com");
        assertThat(contact.phone()).isEqualTo("090-1234-5678");
    }

    @Test
    @DisplayName("電話番号は任意項目（null 可）")
    void phoneIsOptional() {
        ContactInfo contact = new ContactInfo("test@example.com", null);
        assertThat(contact.email()).isEqualTo("test@example.com");
        assertThat(contact.phone()).isNull();
    }

    @Test
    @DisplayName("メールアドレスが null は受け入れない")
    void rejectNullEmail() {
        assertThatThrownBy(() -> new ContactInfo(null, "090-1234-5678"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("メールアドレスが空文字は受け入れない")
    void rejectEmptyEmail() {
        assertThatThrownBy(() -> new ContactInfo("", "090-1234-5678"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("メールアドレスは @ を含む必要がある")
    void rejectInvalidEmail() {
        assertThatThrownBy(() -> new ContactInfo("not-an-email", "090-1234-5678"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("同じ値なら等価")
    void equality() {
        ContactInfo a = new ContactInfo("a@example.com", "03-0000-0000");
        ContactInfo b = new ContactInfo("a@example.com", "03-0000-0000");
        assertThat(a).isEqualTo(b);
    }
}
