package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.cargotracker.routing.domain.model.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * US25: 既存航海スケジュールを更新する。受入基準に 1:1 で対応させる。
 *
 * <p><strong>本ストーリーの主眼は「差分を見てから確定する」ことにある。</strong>
 * 運航変更は日常的に起き、そのたびに登録済みの内容が上書きされる。
 * <strong>何がどう変わるのかを見ないまま確定できる形にしない</strong> —
 * 出発時刻が 1 本ずれただけで、その便を使う経路候補の到着日が全部動く。
 *
 * <p><strong>キャンセルで何も変わらないことを確かめる。</strong> 「確認画面を出した」
 * だけでは、確認せずに戻った利用者を守れない（IT6 の安全装置の教訓）。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "router", roles = "ROUTER")
@DisplayName("US25 既存航海スケジュールを更新する")
class VoyageRescheduleTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private VoyageRepository repository;

    private String voyageNumber;

    @BeforeEach
    void 航海を登録する() throws Exception {
        voyageNumber = "V" + UUID.randomUUID().toString().substring(0, 8);
        // **準備は必ず経路設計者として行う。** ロールを変えたテストでも
        // 前提の航海は登録されていなければならない
        mockMvc.perform(送信("/voyages", 登録フォーム())
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors
                                .user("router").roles("ROUTER")))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * <strong>他のテストと同じ港を使わない。</strong>
     *
     * <p>本クラスはテストごとに 1 便を登録する。よく使われる港（JPOSA → USLAX）で
     * 登録すると、**同じ港で検索している他のテストの期待する便を 1 ページ目から
     * 押し出す**。原因でないテストが落ちる形を作らない。
     */
    private Map<String, String> 登録フォーム() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("voyageNumber", voyageNumber);
        values.put("vesselName", "さくら丸");
        values.put("carrierName", "日本海運");
        values.put("cargoTypes", "GENERAL");
        values.put("capacityWeightKg", "100000");
        values.put("movements[0].departure", "JPHKT");
        values.put("movements[0].arrival", "TWKHH");
        values.put("movements[0].departureTime", "2026-09-01T10:00");
        values.put("movements[0].arrivalTime", "2026-09-14T06:00");
        return values;
    }

    /** 出発が 2 日遅れ、船名も変わった運航変更。 */
    private Map<String, String> 変更フォーム() {
        Map<String, String> values = 登録フォーム();
        values.put("vesselName", "あさひ丸");
        values.put("movements[0].departureTime", "2026-09-03T10:00");
        values.put("movements[0].arrivalTime", "2026-09-16T06:00");
        return values;
    }

    private MockHttpServletRequestBuilder 送信(String path, Map<String, String> values) {
        var request = post(path).with(csrf());
        values.forEach(request::param);
        return request;
    }

    /** 受入基準: 既存の航海番号を指定して既登録スケジュールを呼び出せる。 */
    @Test
    void 既登録のスケジュールを編集画面に呼び出せる() throws Exception {
        mockMvc.perform(get("/voyages/{n}/edit", voyageNumber))
                .andExpect(status().isOk())
                .andExpect(view().name("voyage/form"))
                .andExpect(content().string(Matchers.containsString("さくら丸")))
                .andExpect(content().string(Matchers.containsString("JPHKT")))
                .andExpect(content().string(Matchers.containsString("TWKHH")));
    }

    /**
     * <strong>編集画面の発着日時がそのまま送り返せる形で描画される。</strong>
     *
     * <p>`datetime-local` は**秒以下が付いた値を受け付けず、入力欄を空にする**。
     * 空のまま送られると発着日時を失い、利用者は編集を開いただけで内容を失う。
     * **「画面が開ける」ことは「編集できる」ことを意味しない。**
     */
    @Test
    void 編集画面の発着日時が入力欄の形式で出る() throws Exception {
        String body = mockMvc.perform(get("/voyages/{n}/edit", voyageNumber))
                .andReturn().getResponse().getContentAsString();

        var matcher = java.util.regex.Pattern
                .compile("id=\"departureTime0\"[^>]*value=\"([^\"]*)\"")
                .matcher(body);
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}");
    }

    /** 発着日時が空の更新は 500 にせず、入力の誤りとして差し戻す。 */
    @Test
    void 発着日時が空の更新は差し戻される() throws Exception {
        Map<String, String> values = 変更フォーム();
        values.put("movements[0].departureTime", "");

        mockMvc.perform(送信("/voyages/" + voyageNumber, values))
                .andExpect(status().isOk())
                .andExpect(view().name("voyage/form"));

        assertThat(船名()).isEqualTo("さくら丸");
    }

    /** 存在しない航海番号は 404。**URL を直接編集しただけで 500 にしない。** */
    @Test
    void 存在しない航海の編集画面は404になる() throws Exception {
        mockMvc.perform(get("/voyages/{n}/edit", "V00000000"))
                .andExpect(status().isNotFound());
    }

    /**
     * 受入基準: <strong>既存内容と更新内容の差分が確認画面に表示される。</strong>
     *
     * <p>変わらない項目まで並べると、変わったものが埋もれる。**変わった項目だけ**を出す。
     */
    @Test
    void 差分が確認画面に表示される() throws Exception {
        mockMvc.perform(送信("/voyages/" + voyageNumber + "/edit", 変更フォーム()))
                .andExpect(status().isOk())
                .andExpect(view().name("voyage/confirm"))
                .andExpect(content().string(Matchers.containsString("さくら丸")))
                .andExpect(content().string(Matchers.containsString("あさひ丸")))
                // 変更前後の時刻がどちらも読める
                .andExpect(content().string(Matchers.containsString("09-01")))
                .andExpect(content().string(Matchers.containsString("09-03")));
    }

    /** <strong>確認画面を出しただけでは更新しない。</strong> */
    @Test
    void 差分を表示した時点では更新されない() throws Exception {
        mockMvc.perform(送信("/voyages/" + voyageNumber + "/edit", 変更フォーム()));

        assertThat(船名()).isEqualTo("さくら丸");
    }

    /** 受入基準: 差分確認後に「更新する」を選択すると上書き更新される。 */
    @Test
    void 差分を確認して確定すると上書きされる() throws Exception {
        mockMvc.perform(送信("/voyages/" + voyageNumber, 変更フォーム()))
                .andExpect(status().is3xxRedirection());

        var voyage = repository.findByVoyageNumber(new VoyageNumber(voyageNumber)).orElseThrow();
        assertThat(voyage.vesselName().value()).isEqualTo("あさひ丸");
        assertThat(voyage.schedule().carrierMovements().getFirst().departureTime().toString())
                .contains("2026-09-03");
    }

    /**
     * 受入基準: <strong>「キャンセル」を選択した場合、既存スケジュールは変更されない。</strong>
     *
     * <p>キャンセルは確認画面から一覧へ戻るだけであり、**更新の要求を出さない**。
     * それを確かめるため、確定を送らずに一覧を開いて内容を見る。
     */
    @Test
    void キャンセルすると既存スケジュールは変わらない() throws Exception {
        mockMvc.perform(送信("/voyages/" + voyageNumber + "/edit", 変更フォーム()));

        mockMvc.perform(get("/voyages/{n}", voyageNumber))
                .andExpect(content().string(Matchers.containsString("さくら丸")))
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString("あさひ丸"))));
    }

    /** 受入基準: 更新後、航海スケジュール検索の結果に更新内容が反映される。 */
    @Test
    void 更新内容が検索結果に反映される() throws Exception {
        mockMvc.perform(送信("/voyages/" + voyageNumber, 変更フォーム()));

        mockMvc.perform(get("/voyages").param("origin", "JPHKT"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("あさひ丸")));
    }

    /**
     * <strong>登録と同じ業務ルールが更新でも効く。</strong>
     *
     * <p>登録時にだけ検査すると、**更新経由で不正なスケジュールを作れる**。
     */
    @Test
    void 出発が到着より後の区間には更新できない() throws Exception {
        Map<String, String> values = 変更フォーム();
        values.put("movements[0].departureTime", "2026-09-20T10:00");

        mockMvc.perform(送信("/voyages/" + voyageNumber, values))
                .andExpect(status().isOk())
                .andExpect(view().name("voyage/form"));

        assertThat(船名()).isEqualTo("さくら丸");
    }

    /** <strong>航海番号は変えられない。</strong> 変えられると別の便を上書きできてしまう。 */
    @Test
    void 航海番号を変えた更新は受け付けない() throws Exception {
        Map<String, String> values = 変更フォーム();
        values.put("voyageNumber", "V99999999");

        mockMvc.perform(送信("/voyages/" + voyageNumber, values))
                .andExpect(status().isBadRequest());

        assertThat(船名()).isEqualTo("さくら丸");
    }

    /** <strong>更新の入口がある。</strong> 航海詳細から編集へ行ける（到達性）。 */
    @Test
    void 航海詳細から編集画面へ行ける() throws Exception {
        mockMvc.perform(get("/voyages/{n}", voyageNumber))
                .andExpect(content().string(Matchers.containsString(
                        "/voyages/" + voyageNumber + "/edit")));
    }

    /** 経路設計者以外は更新できない。 */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 営業担当者は航海を更新できない() throws Exception {
        mockMvc.perform(get("/voyages/{n}/edit", voyageNumber))
                .andExpect(status().isForbidden());
    }

    private String 船名() {
        return repository.findByVoyageNumber(new VoyageNumber(voyageNumber))
                .orElseThrow().vesselName().value();
    }
}
