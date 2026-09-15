package com.example.cargotracker.simulation.interfaces.rest;

import com.example.cargotracker.simulation.application.SimulationScheduleService;
import com.example.cargotracker.simulation.infrastructure.query.SimulationScheduleQueries;
import com.example.cargotracker.simulation.infrastructure.query.SimulationScheduleQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 継続実行の開始・停止と統計（S94 / US36）。
 *
 * <p><b>入口を足したら、意図した相手以外が通る道を書き出す</b>（IT16 の教訓）。
 * この入口を叩けるのは<b>管理者だけ</b>（Gateway の宣言 {@code /api/v1/simulation/**}）。
 * 通る相手と起きることを 1 つずつ:</p>
 *
 * <table>
 *   <caption>この入口で起きること</caption>
 *   <tr><th>誰が</th><th>何が起きるか</th><th>気づけるか</th></tr>
 *   <tr><td>管理者</td><td>業務データが増え続ける</td>
 *       <td>S94 に稼働と件数が出る。止める入口も同じ画面にある</td></tr>
 *   <tr><td>営業・経理など他のロール</td><td>403。何も起きない</td>
 *       <td>Gateway が断る（`RoleAuthorizationDeclarationTest` が数え上げる）</td></tr>
 *   <tr><td>許可していない環境の管理者</td><td>断られる</td>
 *       <td>既定は無効（US36 §6）。実行の許可とは別の段である</td></tr>
 * </table>
 *
 * <p><b>静かに効かせない。</b> 継続実行は業務の一覧を汚しうるので、
 * 生成したデータはすべて由来の印が付き（ADR-0020 決定 4）、業務の一覧からは
 * 外れる。印が付かない経路をここから作らない。</p>
 */
@RestController
@RequestMapping("/api/v1/simulation/schedule")
public class SimulationScheduleController {

    private final SimulationScheduleService schedules;
    private final SimulationScheduleQueryHandler queries;

    public SimulationScheduleController(SimulationScheduleService schedules,
            SimulationScheduleQueryHandler queries) {
        this.schedules = schedules;
        this.queries = queries;
    }

    /**
     * 継続実行を始める（US36 §受入基準 4）。
     *
     * <p><b>種は指定できる</b>（§3）。指定しなければ作って記録する——記録しないと
     * あとから同じ並びを再現できない。</p>
     */
    @PostMapping
    public ResponseEntity<StartedSchedule> start(
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @RequestBody(required = false) StartRequest request) {
        // **断りの判断はここに書き直さない**（許可されているか・稼働が 1 本か）。
        String scheduleId = schedules.start(request == null ? null : request.seed(), username);
        return ResponseEntity
                .created(java.net.URI.create("/api/v1/simulation/schedule"))
                .body(new StartedSchedule(scheduleId));
    }

    /**
     * 継続実行を止める（US36 §受入基準 4）。
     *
     * <p><b>すぐには止まらない。</b> 走っている実行は最後まで終える——
     * 応答は「止めると決めた」ことで、止まりきったかは S94 が出す。</p>
     */
    @DeleteMapping
    public ResponseEntity<Void> stop() {
        schedules.stop();
        return ResponseEntity.accepted().build();
    }

    /** いまの稼働と統計（S94 / §3・§8）。<b>動いていなければ 204</b>。 */
    @GetMapping
    public ResponseEntity<SimulationScheduleQueries.ScheduleView> active() {
        SimulationScheduleQueries.ScheduleView view = queries.findActive();
        return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
    }

    /** 開始の入力。<b>種は任意</b>（指定しなければ作って記録する）。 */
    public record StartRequest(Long seed) {
    }

    /** 始めた稼働。 */
    public record StartedSchedule(String scheduleId) {
    }
}
