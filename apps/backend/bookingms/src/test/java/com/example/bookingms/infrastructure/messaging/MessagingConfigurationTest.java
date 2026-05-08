package com.example.bookingms.infrastructure.messaging;

import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.bookingms.domain.ports.CargoEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessagingConfigurationTest {

    private final MessagingConfiguration configuration = new MessagingConfiguration();

    @Test
    void jsonMessageConverterを生成できる() {
        MessageConverter converter = configuration.jacksonMessageConverter();

        assertThat(converter).isInstanceOf(JacksonJsonMessageConverter.class);
    }

    @Test
    void rabbitMqCargoEventPublisherがRabbitTemplateにconverterを設定する() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MessageConverter converter = configuration.jacksonMessageConverter();

        CargoEventPublisher publisher = configuration.rabbitMqCargoEventPublisher(rabbitTemplate, converter);

        verify(rabbitTemplate).setMessageConverter(converter);
        assertThat(publisher).isInstanceOf(RabbitMqCargoEventPublisher.class);
    }

    @Test
    void noOpCargoEventPublisherは例外を投げない() {
        CargoEventPublisher publisher = configuration.noOpCargoEventPublisher();

        assertThatCode(() -> publisher.publishCargoRouted(new CargoRoutedEvent("B1", "JPTYO", "CNSHA")))
                .doesNotThrowAnyException();
    }
}
