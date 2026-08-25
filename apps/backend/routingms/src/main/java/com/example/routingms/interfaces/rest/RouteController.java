package com.example.routingms.interfaces.rest;

import com.example.routingms.application.internal.FindRouteCandidatesUseCase;
import com.example.routingms.domain.model.CargoType;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 経路候補算出（US08・[ADR-017]）。
 *
 * <p>単数の「最適経路」ではなく、<strong>推奨順に並んだ複数の候補</strong>を返す。
 * 経路設計者は見比べて選ぶのであり、システムが 1 つに決めるのではない。
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final FindRouteCandidatesUseCase findRouteCandidates;

    public RouteController(FindRouteCandidatesUseCase findRouteCandidates) {
        this.findRouteCandidates = findRouteCandidates;
    }

    /**
     * 経路候補を算出する。
     *
     * <p><strong>期限は日付で受け取る</strong>（[ADR-017] 決定 3）。業務上「9 月 30 日まで」は
     * 「30 日中に着けばよい」を意味し、業務タイムゾーンでのその日の終わりまでを期限とする。
     * 日付を送って日時で受け取る形にすると、実バックエンドでだけ落ちる（IT3 の欠陥）。
     *
     * <p>{@code origin} には任意の地点を指定できる。貨物の現在地を起点にした再設計（US28）を
     * 同じ入口で行うためである。
     *
     * @param maxTransshipments 積み替えの上限。省略時は既定値。候補が無かったときに
     *     条件を緩めて再算出するために受け取る
     */
    @GetMapping
    public RouteCandidateListResponse find(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestParam(name = "origin", required = false) String origin,
            @RequestParam(name = "destination", required = false) String destination,
            @RequestParam(name = "deadline", required = false) String deadline,
            @RequestParam(name = "cargoType", required = false) String cargoType,
            @RequestParam(name = "maxTransshipments", required = false) String maxTransshipments,
            @RequestParam(name = "earliestDeparture", required = false) String earliestDeparture,
            // **誤配のあとの組み直しでは期限で弾かない**（US28-4・[ADR-026] 決定 4）。
            // 弾くと組み直す手段そのものが無くなる。緩めるのはここだけで、出発地・目的地・
            // 貨物種別・積み替えの上限は今までどおり効く
            @RequestParam(name = "reroute", required = false) String reroute) {
        // 認可は入力の検査より先に行う（ADR-016）
        requireRoutingPlanner(userId, roles);

        return RouteCandidateListResponse.from(findRouteCandidates.find(origin, destination,
                parseDeadline(deadline), parseCargoType(cargoType),
                parseMaxTransshipments(maxTransshipments),
                parseDate(earliestDeparture, "出発希望日"),
                "true".equalsIgnoreCase(reroute)));
    }

    /**
     * 値の変換は<strong>メソッド本体で</strong>行う（ADR-016）。
     *
     * <p>パラメータを {@code LocalDate} や列挙型で受け取ると、Spring は<strong>認可より先に</strong>
     * 変換を試み、失敗すると既定の 400 を返す。権限の無い相手に「どの項目がどんな形か」を
     * 教えることになる。{@code @Valid} を外すだけでは足りない。
     *
     * <p>これは実バックエンドでのみ再現する（MockMvc の変換は同じようには振る舞わない）。
     * 回帰は kind 統合環境に対する検査で固定する。
     */
    private LocalDate parseDeadline(String value) {
        return parseDate(value, "到着期限");
    }

    /** 日付の項目は同じ形で受ける。項目ごとに書き分けると、片方だけ形式が変わる。 */
    private LocalDate parseDate(String value, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException _) {
            // 入力値そのものは返さない（IT2 の決定）。何の項目が誤っているかだけを伝える
            throw new IllegalArgumentException(
                    "%sは「2026-09-30」の形式で指定してください".formatted(what));
        }
    }

    private CargoType parseCargoType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CargoType.valueOf(value);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("貨物種別の指定が正しくありません");
        }
    }

    private Integer parseMaxTransshipments(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("積み替えの上限は数値で指定してください");
        }
    }

    /**
     * 経路候補を引けるのは、経路設計者か<strong>既知のサービス</strong>である。
     *
     * <p>bookingms は経路の確定時に、選ばれた旅程がいまも成り立つかをここで再検証する
     * （[ADR-019]）。その呼び出しは利用者の代理ではないためロールを持たない。ロールだけを
     * 見ていると、経路を確定する瞬間にだけ 403 になる（IT5 はこの状態のまま出荷し、
     * 実環境の往復を通すまで誰も気づかなかった）。
     *
     * <p><strong>名簿に無い主体は通さない。</strong>「システムらしい名前なら通す」形にすると、
     * 載せ忘れた主体ほど素通りする。許すのはここに挙げたものだけである。
     *
     * <p>この入口は<strong>参照のみ</strong>で副作用が無い。書き込みを伴う操作を
     * 同じ検査で守ってはいけない。
     */
    private static final java.util.Set<String> TRUSTED_SERVICE_PRINCIPALS =
            java.util.Set.of("system:bookingms");

    private void requireRoutingPlanner(String userId, String roles) {
        if (TRUSTED_SERVICE_PRINCIPALS.contains(userId)) {
            return;
        }
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_ROUTING)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    /**
     * 入力の誤りは理由を添えて 400 で返す。
     *
     * <p>「経路が見つかりません」と「港の指定が間違っています」は別のことである。
     * 前者を後者として伝えると、経路設計者は条件を緩め続けて時間を使う。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(UserFacingMessage.of(e)));
    }
}
