package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.RegisterShipperCommand;
import com.example.bookingms.application.internal.RegisterShipperUseCase;
import com.example.bookingms.application.internal.RegistrationOutcome;
import com.example.bookingms.application.internal.SearchShipperUseCase;
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

        RegisterShipperCommand command = new RegisterShipperCommand(
                request.type(), request.name(), request.email(), request.address(), request.phone());
        RegistrationOutcome outcome = request.registerAnyway()
                ? registerShipper.registerAnyway(command)
                : registerShipper.register(command);

        return switch (outcome) {
            case RegistrationOutcome.Registered registered -> ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ShipperResponse.from(registered.shipper()));
            case RegistrationOutcome.DuplicateFound duplicate -> ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new DuplicateShipperResponse(
                            "同じメールアドレスの荷主が既に登録されています",
                            ShipperResponse.from(duplicate.existing())));
        };
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
