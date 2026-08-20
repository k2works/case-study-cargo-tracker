package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.BookCargoCommand;
import com.example.bookingms.application.internal.BookCargoUseCase;
import com.example.bookingms.application.internal.SearchCargoUseCase;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoType;
import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/bookings")
public class CargoBookingController {

    private final BookCargoUseCase bookCargo;
    private final SearchCargoUseCase searchCargo;
    private final LocationRepository locations;

    public CargoBookingController(BookCargoUseCase bookCargo, SearchCargoUseCase searchCargo,
            LocationRepository locations) {
        this.bookCargo = bookCargo;
        this.searchCargo = searchCargo;
        this.locations = locations;
    }

    @GetMapping
    public BookingListResponse search(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @RequestParam(name = "type", required = false) CargoType type,
            @RequestParam(name = "keyword", required = false) String keyword) {
        requireSales(userId, roles);

        SearchCargoUseCase.Result result = searchCargo.search(type, keyword);
        return new BookingListResponse(
                result.cargoes().stream().map(BookingResponse::from).toList(),
                result.totalCount(), result.limit(), result.truncated());
    }

    /** 地点の選択肢。画面に UN/LOCODE を直接入力させないために返す。 */
    @GetMapping("/locations")
    public List<LocationResponse> locations(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireSales(userId, roles);
        return locations.findAll().stream().map(LocationResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<BookingResponse> book(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles,
            @Valid @RequestBody BookingRequest request) {
        requireSales(userId, roles);

        Cargo booked = bookCargo.book(new BookCargoCommand(
                request.shipperId(), request.type(), request.weightKg(), request.quantity(),
                request.description(), request.lengthCm(), request.widthCm(), request.heightCm(),
                request.originUnLocode(), request.destinationUnLocode(),
                request.departureDate(), request.arrivalDeadline(),
                request.hazardousClass(), request.unNumber(), request.properShippingName(),
                request.minCelsius(), request.maxCelsius()));

        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booked));
    }

    /**
     * 入力の誤りは理由を添えて 400 で返す。
     *
     * <p>理由を返さないと、営業担当者は「登録したのに一覧に出ない」としか見えない。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(UserFacingMessage.of(e)));
    }

    /**
     * 貨物予約は営業担当者の業務である（ADR-008）。
     *
     * <p>{@code ROLE_SHIPPER} には開かない。利用者と荷主を結ぶキーが無く「自分の予約だけ」に
     * 絞り込めないため、開くと全荷主の予約が見える。US18（IT6）で紐付けと同時に広げ直す。
     */
    private void requireSales(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_SALES)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }
}
