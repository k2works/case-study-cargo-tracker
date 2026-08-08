package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

/**
 * クラスパス上の SQL をディレクトリ単位で読む。
 *
 * <p><strong>1 ファイルだけを読む検査を書かないために用意している。</strong>
 * 設定・シード・マイグレーションを突き合わせる検査は、ファイルが増えた瞬間に
 * 効かなくなる。実際、管理者を {@code V801} で足したとき {@code V800} だけを
 * 読んでいた検査がすり抜け、開発環境のログイン画面に管理者が現れなかった
 * （IT5 の P3）。<strong>装置があること自体は、守られている証拠にならない。</strong>
 *
 * <p>ファイルを名指しする代わりにディレクトリを指す。名指しをやめれば、
 * 増えたファイルは黙って検査の対象になる。
 */
public final class SqlResources {

    private SqlResources() {
    }

    /**
     * クラスパス上のディレクトリ配下の {@code .sql} をすべて連結して返す。
     *
     * @param classpathDirectory 例: {@code db/seed}
     */
    public static String readAll(String classpathDirectory) throws IOException {
        URL dir = SqlResources.class.getClassLoader().getResource(classpathDirectory);
        assertThat(dir).as("クラスパス上に %s があること", classpathDirectory).isNotNull();

        File[] files = new File(dir.getPath()).listFiles((d, name) -> name.endsWith(".sql"));
        assertThat(files).as("%s に SQL があること", classpathDirectory).isNotNull().isNotEmpty();

        StringBuilder all = new StringBuilder();
        for (File file : files) {
            all.append(Files.readString(file.toPath())).append('\n');
        }
        return all.toString();
    }
}
