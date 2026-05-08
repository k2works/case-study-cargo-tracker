package com.example.bookingms;

import com.example.bookingms.domain.ports.CargoEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class BookingApplicationTests {

    @MockitoBean
    CargoEventPublisher cargoEventPublisher;

    @Test
    void contextLoads() {
    }
}
