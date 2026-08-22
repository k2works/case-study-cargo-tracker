package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.EditShipperUseCase;
import com.example.bookingms.application.internal.RegisterShipperCommand;
import com.example.bookingms.application.internal.RegisterShipperUseCase;
import com.example.bookingms.application.internal.RegistrationOutcome;
import com.example.bookingms.application.internal.SearchShipperUseCase;
import com.example.bookingms.domain.model.ContractNumber;
import com.example.bookingms.domain.model.CorporateContract;
import com.example.bookingms.domain.model.DiscountRate;
import com.example.bookingms.domain.model.ShipperProfile;
import com.example.bookingms.domain.model.ShipperType;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/shippers")
public class ShipperController {

    private final RegisterShipperUseCase registerShipper;
    private final SearchShipperUseCase searchShipper;
    private final EditShipperUseCase editShipper;
    private final Validator validator;

    public ShipperController(RegisterShipperUseCase registerShipper,
            SearchShipperUseCase searchShipper, EditShipperUseCase editShipper,
            Validator validator) {
        this.registerShipper = registerShipper;
        this.searchShipper = searchShipper;
        this.editShipper = editShipper;
        this.validator = validator;
    }

    @GetMapping
    public List<ShipperResponse> search(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestParam(name = "keyword", required = false) String keyword) {
        requireSales(userId, roles);
        return searchShipper.search(keyword).stream().map(ShipperResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ShipperResponse find(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable("id") Long id) {
        requireSales(userId, roles);
        return searchShipper.findById(id)
                .map(ShipperResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "指定された荷主が見つかりません"));
    }

    @PostMapping
    public ResponseEntity<ShipperRegistrationResponse> register(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestBody ShipperRequest request) {
        requireSales(userId, roles);
        validate(request);

        RegisterShipperCommand command = commandOf(request);
        RegistrationOutcome outcome = request.registerAnyway()
                ? registerShipper.registerAnyway(command)
                : registerShipper.register(command);

        return switch (outcome) {
            case RegistrationOutcome.Registered(var shipper) -> ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ShipperResponse.from(shipper));
            case RegistrationOutcome.DuplicateFound(var existing) -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new DuplicateShipperResponse(
                            "同じメールアドレスの荷主が既に登録されています",
                            ShipperResponse.from(existing)));
        };
    }


    /**
     * 登録済みの荷主を直す（US02 / #550）。
     *
     * <p>認可を入力の検査より先に置く順序は登録と同じ。{@code @Valid} を使わず本体で
     * 検査するのはそのため（[ADR-016]）。
     */
    @PutMapping("/{id}")
    public ShipperResponse edit(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @PathVariable("id") Long id,
            @RequestBody ShipperRequest request) {
        requireSales(userId, roles);
        validate(request);
        requireSameType(id, request.type());

        // 形式の検査はここではなく値オブジェクトが持つ。集約の例外と同じ扱い（400）になる
        ShipperProfile profile = ShipperProfile.of(
                request.name(), request.email(), request.address(), request.phone());
        return editShipper.edit(id, profile, contractOf(request))
                .map(ShipperResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "指定された荷主が見つかりません"));
    }

    /**
     * 種別の変更要求は、理由を添えて断る。
     *
     * <p>黙って無視すると、法人に個人（契約情報なし）を送ったときに集約が既存の種別で検査し、
     * 「法人荷主には契約番号が必要です」という<strong>原因と無関係な</strong> 400 が返る。
     * 直すべきは契約番号ではないので、利用者は何度直しても通らない。
     */
    private void requireSameType(Long id, ShipperType requested) {
        searchShipper.findById(id)
                .filter(existing -> existing.type() != requested)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "荷主種別は変更できません。種別が違うなら、それは別の荷主です");
                });
    }

    /**
     * 入力を値オブジェクトへ変換する。
     *
     * <p>不正な入力は集約に届く前にここで {@link IllegalArgumentException} になる。集約の
     * 例外と同じ扱い（400）にするため、変換も {@link #handleInvalidInput} の対象に入る。
     */
    private RegisterShipperCommand commandOf(ShipperRequest request) {
        return new RegisterShipperCommand(
                request.type(), request.name(), request.email(), request.address(), request.phone(),
                // 個人で契約情報が送られてきたら捨てずに渡す。拒否するのは集約の仕事であり、
                // ここで黙って捨てると「送ったのに保存されない」が起きる
                contractOf(request));
    }

    private CorporateContract contractOf(ShipperRequest request) {
        boolean corporate = request.type() == ShipperType.CORPORATE;
        boolean hasContractInput =
                request.contractNumber() != null || request.discountRatePercent() != null;
        if (!corporate && !hasContractInput) {
            return null;
        }
        return new CorporateContract(
                contractNumberOf(request.contractNumber()),
                request.discountRatePercent() == null
                        ? null
                        : DiscountRate.ofPercent(request.discountRatePercent()));
    }

    private ContractNumber contractNumberOf(String value) {
        return value == null || value.isBlank() ? null : ContractNumber.of(value);
    }

    /**
     * 入力の誤りは 400 で返す。
     *
     * <p>握りつぶすと、営業担当者には「登録したのに一覧に出ない」としか見えない。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(UserFacingMessage.of(e)));
    }

    /**
     * 荷主の登録・検索は営業担当者の業務である。
     *
     * <p>Gateway が認証（401）を担うため、ここで見るのは担当かどうか（403）だけになる。
     */
    private void requireSales(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_SALES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    /**
     * 入力の検査を認可のあとに行う。
     *
     * <p>{@code @Valid} は引数の解決時に走るため、権限の無い呼び出しでも本文が不正なら
     * 400 が返る。本人には「この操作はできない」ではなく「入力を直せ」と伝わり、
     * 権限が無いはずの相手にエンドポイントの入力仕様を教えることにもなる。
     */
    private void validate(ShipperRequest request) {
        Set<ConstraintViolation<ShipperRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
    }
}
