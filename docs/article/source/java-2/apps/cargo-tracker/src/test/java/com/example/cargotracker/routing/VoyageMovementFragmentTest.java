package com.example.cargotracker.routing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 区間追加の htmx 化（IT3 レビュー M3 / IT4 返済枠 C2）。
 *
 * <p>素の JavaScript による行の複製をやめ、他の画面（荷主検索モーダル）と
 * 同じ htmx の部分更新に揃える。<strong>入力途中の内容を失わせない</strong>という
 * IT3 の判断は変えない。追加するのは行だけで、既存の入力欄には触れない。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "router", roles = "ROUTER")
class VoyageMovementFragmentTest extends PostgreSQLIntegrationTestBase {

    /** 指定した添字の入力欄を返す。**添字がずれると Spring のバインドが壊れる。** */
    @Test
    void 指定した添字の入力欄が返る() throws Exception {
        mockMvc.perform(get("/voyages/movements").param("index", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("movements[1].departure")))
                .andExpect(content().string(Matchers.containsString("movements[1].arrivalTime")));
    }

    /** 追加ボタンは<strong>次の添字</strong>を指して返る。連続で押せる。 */
    @Test
    void 追加ボタンが次の添字を指す() throws Exception {
        mockMvc.perform(get("/voyages/movements").param("index", "1"))
                .andExpect(content().string(Matchers.containsString("index=2")));
    }

    /** 添字を負にしても壊れない。**URL を直接編集しただけで 500 にしない。** */
    @Test
    void 不正な添字でも500にならない() throws Exception {
        mockMvc.perform(get("/voyages/movements").param("index", "-5"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("movements[0].departure")));
    }

    /** 経路設計者以外は開けない。**部分更新の入口も画面と同じ認可で守る。** */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 営業担当者は開けない() throws Exception {
        mockMvc.perform(get("/voyages/movements").param("index", "1"))
                .andExpect(status().isForbidden());
    }
}
