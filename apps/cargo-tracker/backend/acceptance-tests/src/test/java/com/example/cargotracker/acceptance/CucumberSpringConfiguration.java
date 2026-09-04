package com.example.cargotracker.acceptance;

import com.example.cargotracker.booking.BookingApplication;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 受け入れテストの土台。bookingms を実際に起動し、API を叩いて確かめる。
 *
 * <p>コンテナの立て方は {@link AbstractAxonIntegrationTest} に合わせる。以前はここで
 * 同じものを別に組み立てていたので、起動猶予やスキーマ分離を片方だけ直す形になっていた
 * （実際に IT2 で起動猶予の食い違いが出た）。</p>
 *
 * <p>Axon Server は本番と同じ形（DCB 有効）で立てる。ここを緩めると、
 * 受け入れテストが緑でも本番だけ動かない。</p>
 */
@CucumberContextConfiguration
@SpringBootTest(classes = BookingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration extends AbstractAxonIntegrationTest {
}
