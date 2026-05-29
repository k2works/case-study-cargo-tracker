package com.example.authms.web;

import com.example.authms.application.AuthService;
import com.example.authms.domain.Role;
import com.example.authms.domain.User;
import com.example.authms.security.JwtTokenProvider;
import com.example.authms.web.dto.LoginRequest;
import com.example.authms.web.dto.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthController controller;

    @Test
    @DisplayName("US00: 認証成功で JWT を返す")
    void 認証成功でトークンを返す() {
        User user = new User(1L, "admin", "hashed", Role.ROLE_ADMIN, 0, null);
        when(authService.authenticate("admin", "password")).thenReturn(user);
        when(jwtTokenProvider.generateToken("admin", "ROLE_ADMIN")).thenReturn("jwt-token");

        ResponseEntity<LoginResponse> response = controller.login(
                new LoginRequest("admin", "password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isEqualTo("jwt-token");
        assertThat(response.getBody().role()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("US00: ログアウトは 204 No Content")
    void ログアウトは204() {
        ResponseEntity<Void> response = controller.logout();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
