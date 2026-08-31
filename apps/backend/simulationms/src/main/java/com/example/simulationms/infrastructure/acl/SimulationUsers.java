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
 *
 * <p><strong>実業務の利用者は借りない</strong>（IT15）。借りると、その利用者本人も
 * 「シミュレーション由来」として荷主を登録できる——精算の締めから消せる操作が
 * 実の営業担当者の手に渡る。専用の帯（{@value #USERNAME_PREFIX}）だけを受け入れ、
 * 設定した時点で断る。設定を間違えたら起動しない側に倒す。
 */
public record SimulationUsers(Map<String, String> usernameByRole, String password) {

    /** シミュレーション専用の利用者名の帯。 */
    public static final String USERNAME_PREFIX = "sim-";

    public SimulationUsers {
        if (usernameByRole == null || usernameByRole.isEmpty()) {
            throw new IllegalArgumentException("工程を踏む利用者が 1 人も設定されていません");
        }
        for (Map.Entry<String, String> entry : usernameByRole.entrySet()) {
            String username = entry.getValue();
            if (username == null || !username.startsWith(USERNAME_PREFIX)) {
                throw new IllegalArgumentException(
                        "シミュレーションは専用の利用者としてのみ入れます: " + entry.getKey()
                                + " = " + username + "（" + USERNAME_PREFIX + " で始まる名前にする）");
            }
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
