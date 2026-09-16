package com.example.cargotracker.shared.infrastructure.crypto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * ローカルと CI 用の鍵置き場（ADR-0003 決定 2）。本番は AWS KMS の実装を当てる。
 *
 * <p>破棄はファイルの削除。KMS の {@code ScheduleKeyDeletion}（7 日待機）に相当する
 * 猶予はここには無いので、削除演習の所要時間は本番と違う。</p>
 */
public final class LocalFileShipperKeyRepository implements ShipperKeyRepository {

    private static final int KEY_LENGTH = 32; // AES-256
    private final Path directory;
    private final SecureRandom random = new SecureRandom();

    public LocalFileShipperKeyRepository(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("鍵置き場を作れませんでした: " + directory, e);
        }
    }

    private Path keyFile(String shipperId) {
        // shipperId をそのままファイル名にするとパス区切りで外へ出られる。
        return directory.resolve(shipperId.replaceAll("[^A-Za-z0-9_-]", "_") + ".key");
    }

    @Override
    public byte[] createOrGet(String shipperId) {
        return find(shipperId).orElseGet(() -> {
            byte[] key = new byte[KEY_LENGTH];
            random.nextBytes(key);
            try {
                Files.write(keyFile(shipperId), key);
            } catch (IOException e) {
                // 鍵を作れないならコマンドを拒否する。平文で書くより拒否するほうがよい。
                throw new UncheckedIOException("鍵を作れませんでした: shipperId=" + shipperId, e);
            }
            return key;
        });
    }

    @Override
    public Optional<byte[]> find(String shipperId) {
        Path file = keyFile(shipperId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("鍵を読めませんでした: shipperId=" + shipperId, e);
        }
    }

    @Override
    public void destroy(String shipperId) {
        try {
            Files.deleteIfExists(keyFile(shipperId));
        } catch (IOException e) {
            throw new UncheckedIOException("鍵を破棄できませんでした: shipperId=" + shipperId, e);
        }
    }
}
