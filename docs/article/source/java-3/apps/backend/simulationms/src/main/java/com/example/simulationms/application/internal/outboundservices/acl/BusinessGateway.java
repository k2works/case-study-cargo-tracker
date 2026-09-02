package com.example.simulationms.application.internal.outboundservices.acl;

import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import java.util.Map;

/**
 * 業務 API を呼ぶ唯一の出口（[ADR-030] 決定 2）。
 *
 * <p><strong>ポートは 1 本にする。</strong>各サービスへ直接つなぐ ACL を 6 本作ると、
 * そのうち 1 本でも内部 API を向いた時点で「本番と同じ経路」が崩れる。出口を絞ることで、
 * 内部 API を使っていないことを 1 か所で確かめられる。
 *
 * <p><strong>実在の利用者としてログインして呼ぶ。</strong>{@code system:} principal も
 * 内部 API も使わない。使うと認可を素通りする経路を新設することになり、
 * 「シミュレーションは通るのに実利用者の操作は 403 で止まる」状態を検出できなくなる。
 */
public interface BusinessGateway {

    /**
     * 1 つの工程を、その工程を踏むロールの利用者として実行する。
     *
     * @param step 実行する工程。踏む人のロールを持っている
     * @param context これまでの工程が生成した識別子（荷主コード・予約番号・追跡番号など）
     * @return その工程が生成した識別子。生成しない工程は空文字を返す
     * @throws BusinessCallFailedException 業務 API が失敗した場合。<strong>理由を持つ</strong>
     *     ——「失敗しました」だけでは、経路候補が 0 件なのか設定が違うのかを切り分けられない
     */
    String execute(ScenarioStep step, Map<String, String> context);
}
