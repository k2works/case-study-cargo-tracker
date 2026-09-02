package com.example.cargotracker.contract;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * ゴールデン JSON を作り直す道具。既定では動かさない。
 *
 * <p>形を変えるときはまず Upcaster を考える。ここを回して上書きすると、
 * 過去のイベントが読めなくなったことに誰も気づけない。</p>
 */
@Disabled("ゴールデンを作り直すときだけ手で外す")
class GoldenWriter {

    @Test
    void write() throws Exception {
        var converter = new JacksonConverter();
        Path dir = Path.of("src/test/resources/golden");
        Files.createDirectories(dir);
        ContractEventGoldenTest.contractEvents().forEach(event -> {
            try {
                Files.writeString(dir.resolve(event.getClass().getSimpleName() + ".json"),
                        new String(converter.convert(event, byte[].class), StandardCharsets.UTF_8),
                        StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
