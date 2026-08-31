package com.example.simulationms;

import com.example.shared.architecture.ServiceArchitectureTest;

/**
 * simulationms のアーキテクチャ規則。
 *
 * <p>規則の実体と適用は shared の {@link ServiceArchitectureTest} にある。ここで並べない。
 * 並べる形にすると、規則を足したときに写し漏れたサービスが無検査のまま残る。
 */
class ArchitectureTest extends ServiceArchitectureTest {

    @Override
    protected String serviceName() {
        return "simulationms";
    }

}
