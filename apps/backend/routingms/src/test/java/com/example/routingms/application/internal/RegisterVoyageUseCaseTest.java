package com.example.routingms.application.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.routingms.application.port.VoyageRepository;
import com.example.routingms.application.port.VoyageSearchCriteria;
import com.example.routingms.domain.model.CargoType;
import com.example.routingms.domain.model.CarrierMovement;
import com.example.routingms.domain.model.Schedule;
import com.example.routingms.domain.model.Voyage;
import com.example.routingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("航海スケジュールの登録・更新")
class RegisterVoyageUseCaseTest {

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");

    private final List<Voyage> stored = new ArrayList<>();

    /**
     * 偽の保存先。
     *
     * <p>渡されたものはそのまま保つ。一部を捨てると、ユースケースが渡し忘れても結果が
     * 同じになり、検査が本番の誤りを判別しなくなる。
     */
    private final VoyageRepository repository = new VoyageRepository() {
        @Override
        public Voyage save(Voyage voyage) {
            stored.removeIf(v -> v.voyageNumber().equals(voyage.voyageNumber()));
            Voyage saved = Voyage.restore((long) (stored.size() + 1), voyage.voyageNumber(),
                    voyage.vesselName(), voyage.carrierName(), voyage.supportedCargoTypes(),
                    voyage.schedule());
            stored.add(saved);
            return saved;
        }

        @Override
        public Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber) {
            return stored.stream().filter(v -> v.voyageNumber().equals(voyageNumber)).findFirst();
        }

        @Override
        public List<Voyage> search(VoyageSearchCriteria criteria, int limit) {
            return List.copyOf(stored);
        }

        @Override
        public int countMatching(VoyageSearchCriteria criteria) {
            return stored.size();
        }
    };

    private final RegisterVoyageUseCase useCase = new RegisterVoyageUseCase(repository);

    private static CarrierMovement leg(Location from, Location to, String departure, String arrival) {
        return CarrierMovement.of(from, to, Instant.parse(departure), Instant.parse(arrival));
    }

    private static RegisterVoyageCommand command(String number, String vessel,
            Set<CargoType> supported, List<CarrierMovement> movements) {
        return new RegisterVoyageCommand(VoyageNumber.of(number), vessel, "日本郵船",
                supported, Schedule.of(movements));
    }

    private static RegisterVoyageCommand tokyoToShanghai(String number, String vessel) {
        return command(number, vessel, Set.of(CargoType.GENERAL),
                List.of(leg(TOKYO, SHANGHAI, "2026-10-01T09:00:00Z", "2026-10-03T18:00:00Z")));
    }

    @Nested
    @DisplayName("新規登録")
    class Registration {

        @Test
        @DisplayName("登録すると読み戻せる")
        void registers() {
            VoyageOutcome outcome = useCase.register(tokyoToShanghai("V2001", "さくら丸"));

            assertThat(outcome).isInstanceOf(VoyageOutcome.Registered.class);
            assertThat(stored).hasSize(1);
        }
    }

    @Nested
    @DisplayName("航海番号が既にあるとき")
    class WhenNumberAlreadyExists {

        /**
         * 「登録できません」で終わらせない。
         *
         * <p>経路設計者が同じ番号を入れるのは、多くの場合スケジュールの差し替えである。
         * 拒否するとその場で仕事が止まり、別の番号を作る（同じ航海が 2 つになる）か、
         * 一覧から探し直すことになる。差分を見せて上書きを選ばせる。
         */
        @Test
        @DisplayName("拒否せず、何が変わるかを見せて上書きを選ばせる")
        void offersDifferenceInsteadOfRejecting() {
            useCase.register(tokyoToShanghai("V2002", "さくら丸"));

            VoyageOutcome outcome = useCase.register(tokyoToShanghai("V2002", "つばき丸"));

            assertThat(outcome).isInstanceOf(VoyageOutcome.AlreadyExists.class);
            VoyageOutcome.AlreadyExists existing = (VoyageOutcome.AlreadyExists) outcome;
            assertThat(existing.difference().hasChanges()).isTrue();
            assertThat(existing.difference().changes())
                    .anySatisfy(change -> assertThat(change.item()).isEqualTo("船名"));
            // 上書きを選ぶまでは、既存は変わらない
            assertThat(stored.get(0).vesselName()).isEqualTo("さくら丸");
        }

        /**
         * 変わらないなら、そう言う。
         *
         * <p>差分の無い上書きに「本当に上書きしますか」と聞くのは、利用者に判断できない
         * 問いを投げていることになる。
         */
        @Test
        @DisplayName("内容が同じなら「変更ありません」と分かる")
        void reportsNoChangeWhenIdentical() {
            useCase.register(tokyoToShanghai("V2003", "さくら丸"));

            VoyageOutcome outcome = useCase.register(tokyoToShanghai("V2003", "さくら丸"));

            VoyageOutcome.AlreadyExists existing = (VoyageOutcome.AlreadyExists) outcome;
            assertThat(existing.difference().hasChanges()).isFalse();
            assertThat(existing.difference().changes()).isEmpty();
        }

        @Test
        @DisplayName("寄港地の変更も差分として見える")
        void showsScheduleChanges() {
            useCase.register(tokyoToShanghai("V2004", "さくら丸"));

            VoyageOutcome outcome = useCase.register(command("V2004", "さくら丸",
                    Set.of(CargoType.GENERAL), List.of(
                            leg(TOKYO, SHANGHAI, "2026-10-01T09:00:00Z", "2026-10-03T18:00:00Z"),
                            leg(SHANGHAI, LOS_ANGELES, "2026-10-04T08:00:00Z",
                                    "2026-10-18T12:00:00Z"))));

            VoyageOutcome.AlreadyExists existing = (VoyageOutcome.AlreadyExists) outcome;
            assertThat(existing.difference().changes())
                    .anySatisfy(change -> {
                        assertThat(change.item()).isEqualTo("寄港地");
                        assertThat(change.before()).isEqualTo("JPTYO → CNSHA");
                        assertThat(change.after()).isEqualTo("JPTYO → CNSHA → USLAX");
                    });
        }

        @Test
        @DisplayName("対応できる貨物種別の変更も差分として見える")
        void showsCargoTypeChanges() {
            useCase.register(tokyoToShanghai("V2005", "さくら丸"));

            VoyageOutcome outcome = useCase.register(command("V2005", "さくら丸",
                    Set.of(CargoType.GENERAL, CargoType.HAZARDOUS),
                    List.of(leg(TOKYO, SHANGHAI, "2026-10-01T09:00:00Z", "2026-10-03T18:00:00Z"))));

            VoyageOutcome.AlreadyExists existing = (VoyageOutcome.AlreadyExists) outcome;
            assertThat(existing.difference().changes())
                    .anySatisfy(change -> assertThat(change.item()).isEqualTo("対応できる貨物種別"));
        }
    }

    @Nested
    @DisplayName("上書きを選んだとき")
    class WhenOverwriteChosen {

        @Test
        @DisplayName("上書きすると新しい内容になる")
        void overwrites() {
            useCase.register(tokyoToShanghai("V2006", "さくら丸"));

            VoyageOutcome outcome = useCase.overwrite(tokyoToShanghai("V2006", "つばき丸"));

            assertThat(outcome).isInstanceOf(VoyageOutcome.Registered.class);
            assertThat(stored).hasSize(1);
            assertThat(stored.get(0).vesselName()).isEqualTo("つばき丸");
        }

        /**
         * 存在しない航海番号への上書きは登録として扱わない。
         *
         * <p>上書きの画面は「既にある航海を差し替える」文脈であり、そこから新しい航海が
         * 生まれると、番号の打ち間違いが新規登録になる。
         */
        @Test
        @DisplayName("存在しない航海番号は上書きできない")
        void cannotOverwriteWhatDoesNotExist() {
            VoyageOutcome outcome = useCase.overwrite(tokyoToShanghai("V9999", "つばき丸"));

            assertThat(outcome).isInstanceOf(VoyageOutcome.NotFound.class);
            assertThat(stored).isEmpty();
        }
    }
}
