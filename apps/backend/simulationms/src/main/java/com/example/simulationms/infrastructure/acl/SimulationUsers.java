package com.example.simulationms.infrastructure.acl;

import java.util.Map;

/**
 * 工程を踏む利用者の名簿（[ADR-030] 決定 2）。
 *
 * <p>ロールごとに<strong>別の利用者</strong>を持つ。1 つの利用者に全ロールを与えると、
 * 本番には存在しない権限の持ち主ができ、認可の検査を素通りする。
 *
 * <p><strong>載っていないロールは断る。</strong>既定の利用者へ落とすと、名簿に載せ忘れた
 * ロールの工程ほど静かに別人として実行される。
 */
public record SimulationUsers(Map<String, String> usernameByRole, String password) {

    public SimulationUsers {
        if (usernameByRole == null || usernameByRole.isEmpty()) {
            throw new IllegalArgumentException("工程を踏む利用者が 1 人も設定されていません");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("利用者の合言葉が設定されていません");
        }
        usernameByRole = Map.copyOf(usernameByRole);
    }

    public static SimulationUsers of(Map<String, String> usernameByRole, String password) {
        return new SimulationUsers(usernameByRole, password);
    }

    public String usernameFor(String role) {
        String username = usernameByRole.get(role);
        if (username == null) {
            throw new IllegalStateException(
                    "この工程を踏む利用者が設定されていません: " + role
                            + "（app.simulation-users.usernames に追加する）");
        }
        return username;
    }
}
