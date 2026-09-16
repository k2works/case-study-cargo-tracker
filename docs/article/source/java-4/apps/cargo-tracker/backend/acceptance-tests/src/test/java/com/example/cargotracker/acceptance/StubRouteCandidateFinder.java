package com.example.cargotracker.acceptance;

import com.example.cargotracker.booking.application.port.RouteCandidateFinder;
import com.example.cargotracker.booking.application.port.RouteSearchRequest;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteCandidate;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.shared.testing.AcceptanceFixtureTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 経路探索の代役。
 *
 * <p><b>routingms はこの文脈に居ない。</b> 受け入れテストは対象サービスだけを
 * 起動する（複数を同一 JVM に載せると設定とマイグレーションが衝突する）。
 * ここで返すのは<b>シナリオが「ある」と言った航海</b>だけで、路線網の正しさは
 * routingms 側の受け入れと契約テストが見る。</p>
 *
 * <p><b>探せなかったことは真似しない。</b> 代役が 0 件を返すのは「候補が無い」
 * であって「探せなかった」ではない（{@code RouteSearchUnavailable} は本物の
 * ACL だけが投げる）。</p>
 */
public class StubRouteCandidateFinder implements RouteCandidateFinder {

    /** シナリオが用意した航海の区間。**無ければ候補 0 件**（断らない）。 */
    private List<Leg> legs = List.of();

    /** 最後の入港日。**希望期限と突き合わせて超過日数を数える。** */
    private java.time.LocalDate arrival;

    /** {@code "JPTYO" から "USNYC" へ 3 区間で行ける航海がある} の実体。 */
    void reachable(String from, String to, int legCount) {
        List<Leg> built = new ArrayList<>();
        String origin = from;
        for (int i = 1; i <= legCount; i++) {
            String destination = i == legCount ? to : (i == 1 ? "SGSIN" : "USLAX");
            built.add(new Leg("V-MOL-00" + i, Location.of(origin), Location.of(destination),
                    AcceptanceFixtureTime.at(1 + i, 9), AcceptanceFixtureTime.at(5 + i, 18)));
            origin = destination;
        }
        this.legs = List.copyOf(built);
        this.arrival = AcceptanceFixtureTime.date(5L + legCount);
    }

    /**
     * 候補を返す。
     *
     * <p><b>超過日数は探索した側が数える</b>（正典）。代役でも数える——0 で
     * 固定すると、「間に合う候補が無い」というシナリオが常に緑になる。</p>
     */
    @Override
    public RouteCandidates find(RouteSearchRequest request) {
        if (legs.isEmpty()) {
            return new RouteCandidates(List.of(), false);
        }
        int overdueDays = (int) Math.max(0,
                java.time.temporal.ChronoUnit.DAYS.between(request.arrivalDeadline(), arrival));
        int transitDays = (int) java.time.temporal.ChronoUnit.DAYS.between(
                AcceptanceFixtureTime.today(), arrival);
        return new RouteCandidates(
                List.of(new RouteCandidate(legs, transitDays, true, overdueDays)), false);
    }

    /** 本物の ACL より優先する。routingms を呼びに行くと、この文脈では必ず落ちる。 */
    @TestConfiguration
    public static class Configuration {

        @Bean
        @Primary
        StubRouteCandidateFinder stubRouteCandidateFinder() {
            return new StubRouteCandidateFinder();
        }
    }
}
