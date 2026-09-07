package com.example.cargotracker.acceptance.tracking;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.TrackingApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 追跡（trackingms）の受け入れテストの土台。trackingms だけを起動する。
 *
 * <p><b>他サービスとは別のソースセットに置く。</b> Cucumber は 1 つの glue
 * パッケージにつき 1 つのコンテキストしか持てず、起動するサービスが違えば
 * コンテキストも別になる。</p>
 */
@CucumberContextConfiguration
@SpringBootTest(classes = TrackingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TrackingCucumberConfiguration extends AbstractAxonIntegrationTest {
}
