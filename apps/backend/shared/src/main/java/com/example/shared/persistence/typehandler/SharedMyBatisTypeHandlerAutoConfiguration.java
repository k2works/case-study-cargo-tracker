package com.example.shared.persistence.typehandler;

import org.apache.ibatis.session.Configuration;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * MyBatis の TypeHandler を全マイクロサービスで共通利用するための AutoConfiguration。
 *
 * <p>{@link ZonedDateTimeTypeHandler}・{@link LocalDateTimeTypeHandler} を
 * {@code TypeHandlerRegistry.register(Class, TypeHandler)} 経由で登録し、
 * {@code UNDEFINED} 経路にも登録することで MyBatis 標準ハンドラを確実に上書きする。
 *
 * <p>{@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * に登録されているため、shared モジュールをクラスパスに含む全マイクロサービスで自動的に有効になる。
 *
 * <p>PostgreSQL JDBC は {@code timestamptz} カラムから {@code ZonedDateTime}・{@code LocalDateTime}
 * への直接変換をサポートしないため、ローカルでの本番擬似検証や AWS 本番運用時にも本ハンドラが必要となる。
 * H2 のメモリ DB では直接変換が動くため、開発時には恩恵を実感しづらいが、
 * 環境差を吸収する役割を果たす。
 */
@AutoConfiguration(after = MybatisAutoConfiguration.class)
@ConditionalOnClass({Configuration.class, ConfigurationCustomizer.class})
public class SharedMyBatisTypeHandlerAutoConfiguration {

    @Bean
    public ConfigurationCustomizer sharedMyBatisTypeHandlerCustomizer() {
        return configuration -> {
            configuration.getTypeHandlerRegistry().register(ZonedDateTime.class, new ZonedDateTimeTypeHandler());
            configuration.getTypeHandlerRegistry().register(LocalDateTime.class, new LocalDateTimeTypeHandler());
        };
    }
}
