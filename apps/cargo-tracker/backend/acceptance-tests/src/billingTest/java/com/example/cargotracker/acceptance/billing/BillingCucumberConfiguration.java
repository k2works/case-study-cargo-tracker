package com.example.cargotracker.acceptance.billing;

import com.example.cargotracker.billing.BillingApplication;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 請求（billingms）の受け入れテストの土台。billingms だけを起動する。
 *
 * <p><b>他サービスとは別のソースセットに置く。</b> Cucumber は 1 つの glue
 * パッケージにつき 1 つのコンテキストしか持てない。</p>
 */
@CucumberContextConfiguration
@SpringBootTest(classes = BillingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BillingCucumberConfiguration extends AbstractAxonIntegrationTest {
}
