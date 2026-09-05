package com.example.cargotracker.billing;

import com.example.cargotracker.shared.archunit.AbstractServiceArchitectureTest;

/** Billing サービスに境界の規則を適用する。規則の本体は shared の testFixtures にある。 */
class BillingArchitectureTest extends AbstractServiceArchitectureTest {

    @Override
    protected String servicePackage() {
        return "com.example.cargotracker.billing";
    }
}
