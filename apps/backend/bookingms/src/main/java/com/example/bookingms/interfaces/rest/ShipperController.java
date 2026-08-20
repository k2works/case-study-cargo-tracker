package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.RegisterShipperCommand;
import com.example.bookingms.application.internal.RegisterShipperUseCase;
import com.example.bookingms.application.internal.RegistrationOutcome;
import com.example.bookingms.application.internal.SearchShipperUseCase;
import com.example.bookingms.domain.model.ContractNumber;
import com.example.bookingms.domain.model.CorporateContract;
import com.example.bookingms.domain.model.DiscountRate;
import com.example.bookingms.domain.model.ShipperType;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    public ShipperController(RegisterShipperUseCase registerShipper,
            SearchShipperUseCase searchShipper) {
        this.registerShipper = registerShipper;
        this.searchShipper = searchShipper;
    }

    @GetMapping
    public List<ShipperResponse> search(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestParam(name = "keyword", required = false) String keyword) {
        requireSales(userId, roles);
        return searchShipper.search(keyword).stream().map(ShipperResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<ShipperRegistrationResponse> register(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @Valid @RequestBody ShipperRequest request) {
        requireSales(userId, roles);

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
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
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
}
