package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.CreateEstimateUseCase;
import com.example.bookingms.application.internal.EstimateQuote;
import com.example.bookingms.application.port.EstimateRepository;
import com.example.bookingms.application.port.RouteCandidateUnavailableException;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 輸送見積（US01）。
 *
 * <p><strong>営業担当者だけが使う。</strong>荷主に「いくらで何日か」を答えるのは営業の
 * 仕事であり、経理や経路設計者とは職掌が違う。<strong>画面に出す・出さないでは
 * 守れない</strong>——URL を直接叩かれる。
 *
 * <p><strong>認可を入力検証より先に置く。</strong>権限の無い相手に入力仕様を教えない。
 */
@RestController
@RequestMapping("/api/v1/estimates")
public class EstimateController {

    private final CreateEstimateUseCase createEstimate;

    private final EstimateRepository estimates;

    public EstimateController(CreateEstimateUseCase createEstimate,
            EstimateRepository estimates) {
        this.createEstimate = createEstimate;
        this.estimates = estimates;
    }

    /** 見積の一覧（**新しい順**）。 */
    @GetMapping
    public List<EstimateResponse> list(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireSales(userId, roles);
        return estimates.findAll().stream().map(EstimateResponse::from).toList();
    }

    /** 見積 1 件。 */
    @GetMapping("/{estimateId}")
    public EstimateResponse detail(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable String estimateId) {
        requireSales(userId, roles);

        return estimates.findById(estimateId)
                .map(EstimateResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "見積が見つかりません"));
    }

    /**
     * 候補を探す（受入基準 01-2・01-3・01-5）。
     *
     * <p><strong>保存しない。</strong>営業担当者は候補を見てから作成を決める。
     */
    @PostMapping("/quotes")
    public EstimateQuoteResponse quote(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody CreateEstimateRequest request) {
        requireSales(userId, roles);

        try {
            EstimateQuote quote = createEstimate.quote(request.toCommand());
            return EstimateQuoteResponse.from(quote);
        } catch (RouteCandidateUnavailableException unavailable) {
            // **相手に届いていないことを言う**（IT11 レビューと同じ形）。
            // 500 にすると、待てば直るのか自分の操作が悪いのかが分からない
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "経路の検索ができませんでした。しばらくしてからお試しください。");
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    /** 見積を作る（受入基準 01-4）。**見積番号が発行される。** */
    @PostMapping
    public ResponseEntity<EstimateResponse> create(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody CreateEstimateRequest request) {
        requireSales(userId, roles);

        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(EstimateResponse.from(createEstimate.create(request.toCommand())));
        } catch (RouteCandidateUnavailableException unavailable) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "経路の検索ができませんでした。しばらくしてからお試しください。");
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    private void requireSales(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_SALES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }
}
