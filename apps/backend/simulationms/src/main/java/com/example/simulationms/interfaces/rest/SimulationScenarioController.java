package com.example.simulationms.interfaces.rest;

import com.example.shared.auth.AuthenticatedUser;
import com.example.shared.auth.Role;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 実行できるシナリオを返す（US34）。
 *
 * <p><strong>システム管理者だけに開く。</strong>シミュレーションは業務データを作る操作であり、
 * 業務の担当者が誤って踏める場所には置かない。
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationScenarioController {

    @GetMapping("/scenarios")
    public List<ScenarioResponse> scenarios(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestHeader(name = AuthenticatedUser.ROLES_HEADER, required = false) String roles) {
        requireAdmin(userId, roles);

        return List.of(ScenarioResponse.from(Scenario.standardTransport()));
    }

    private void requireAdmin(String userId, String roles) {
        if (!AuthenticatedUser.of(userId, roles).hasAnyRole(Role.ROLE_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }

    /** 画面が読む形。工程の並びをそのまま返す——どこまで進むのかを先に示すためである。 */
    record ScenarioResponse(String id, List<StepResponse> steps) {

        static ScenarioResponse from(Scenario scenario) {
            return new ScenarioResponse(scenario.id(),
                    scenario.steps().stream().map(StepResponse::from).toList());
        }
    }

    record StepResponse(String step, String label, String role) {

        static StepResponse from(ScenarioStep step) {
            return new StepResponse(step.name(), step.label(), step.role());
        }
    }
}
