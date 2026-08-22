package com.example.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;

/**
 * 全サービス共通のアーキテクチャ規則。
 *
 * <p>各サービスの ArchitectureTest から呼び出す。規則をここに集約することで、
 * サービスごとに検査内容がずれる（＝一部だけ緩い）状態を防ぐ。
 * 適用漏れ自体は {@code ArchitectureRuleCoverageTest}（shared のメタテスト）が検出する。
 */
public final class HexagonalArchitectureRules {

    private HexagonalArchitectureRules() {
    }

    /**
     * ヘキサゴナル 4 層（domain / application / infrastructure / interfaces）の依存方向を検査する。
     * 依存は常に外から内へ向かう。domain は誰にも依存しない。
     */
    public static List<ArchRule> layerRules(String basePackage) {
        String domain = basePackage + ".domain..";
        String application = basePackage + ".application..";
        String infrastructure = basePackage + ".infrastructure..";
        String interfaces = basePackage + ".interfaces..";

        return List.of(
                noClasses().that().resideInAPackage(domain)
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(application, infrastructure, interfaces)
                        .as("domain は他のどの層にも依存しない")
                        .allowEmptyShould(true),
                noClasses().that().resideInAPackage(application)
                        .should().dependOnClassesThat()
                        .resideInAnyPackage(infrastructure, interfaces)
                        .as("application は infrastructure と interfaces に依存しない")
                        .allowEmptyShould(true),
                noClasses().that().resideInAPackage(infrastructure)
                        .should().dependOnClassesThat().resideInAPackage(interfaces)
                        .as("infrastructure は interfaces に依存しない")
                        .allowEmptyShould(true),
                noClasses().that().resideInAPackage(domain)
                        .should().dependOnClassesThat()
                        .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.apache.ibatis..")
                        .as("domain はフレームワークに依存しない（純粋な業務ロジックに保つ）")
                        .allowEmptyShould(true));
    }

    /**
     * サービス境界の独立性を検査する。他サービスのパッケージを直接参照してはならない。
     * サービス間の連携は HTTP / メッセージング経由であり、共有は shared（共有カーネル）に限る。
     */
    public static ArchRule serviceIsolationRule(String serviceName) {
        return noClasses().that().resideInAPackage("com.example." + serviceName + "..")
                .should().dependOnClassesThat(
                        com.tngtech.archunit.base.DescribedPredicate.describe(
                                "他サービスのパッケージ",
                                javaClass -> javaClass.getPackageName().startsWith("com.example.")
                                        && !javaClass.getPackageName().startsWith("com.example." + serviceName)
                                        && !javaClass.getPackageName().startsWith("com.example.shared")))
                .as("他サービスのクラスを直接参照しない（連携は HTTP / メッセージング経由）")
                .allowEmptyShould(true);
    }

    /**
     * 共有カーネルの範囲を検査する（ADR-001 / architecture_backend.md）。
     *
     * <p>{@link #serviceIsolationRule(String)} は {@code com.example.shared} をまるごと除外している。
     * 除外がある以上、そこに置けば全サービスから使えてしまうため、共有カーネルは放っておくと太る。
     * 太った共有カーネルはサービスの独立性を静かに失わせる（1 箇所の変更が 7 サービスの再デプロイになる）。
     *
     * <p>そこで<strong>置いてよいパッケージを列挙し、列挙に無いものを違反とする</strong>。
     * 「置いてはいけないもの」を列挙する形にすると、思いつかなかったものが素通りする。
     *
     * <p>共有してよいのは「全サービスが同じ意味で使い、かつ 1 箇所にしないと壊れるもの」に限る。
     * <ul>
     *   <li>{@code domain.model} — Location（UN/LOCODE）。4 コンテキストが同じ地点を指す
     *   <li>{@code auth} — Gateway と各サービスの認証契約（ADR-004 / ADR-007）。
     *       ヘッダ名を書き写すと、Gateway 側で変えても誰も落ちない
     * </ul>
     *
     * <p>業務ロジック・DTO・ユーティリティはここに置かない。共有したくなったら、それは
     * 本当に共有カーネルかを問い直す合図である（多くはサービス側の重複のほうが安い）。
     */
    public static ArchRule sharedKernelScopeRule() {
        return classes().that().resideInAPackage("com.example.shared..")
                .should().resideInAnyPackage(SHARED_KERNEL_PACKAGES)
                .as("共有カーネルに置けるのは地点と認証契約だけ（新しい種類を足すなら ADR で決める）")
                .allowEmptyShould(true);
    }

    /** 共有カーネルに置いてよいパッケージ。ここに無いものは違反になる。 */
    private static final String[] SHARED_KERNEL_PACKAGES = {
        "com.example.shared",
        "com.example.shared.domain",
        "com.example.shared.domain.model",
        "com.example.shared.auth"
    };

    /**
     * ADR-004 の分担を検査する。署名検証は gatewayms に一元化し、各サービスは
     * Gateway が付与した検証済みクレームのロールだけを見る。
     *
     * <p>この規則が無いと「Spring Security を素直に入れたら署名検証まで付いてきた」という形で
     * 鍵の管理が 7 サービスに拡散する。ADR-004 が最も恐れる失敗モードを構造で止める。
     *
     * <p>gatewayms（検証）と authms（発行）は鍵を持つ 2 サービスであり、この規則の対象外とする。
     * authms 側は代わりに {@link #noTokenVerificationRule(String)} で「発行はするが検証はしない」
     * ことを検査する。依存の有無だけで判断すると、発行のために入れたライブラリで検証も
     * 始められてしまう。
     */
    public static ArchRule noJwtDependencyRule(String serviceName) {
        return noClasses().that().resideInAPackage("com.example." + serviceName + "..")
                .should().dependOnClassesThat().resideInAnyPackage(JWT_LIBRARY_PACKAGES)
                .as("gatewayms / authms 以外は JWT ライブラリに依存しない（ADR-004）")
                .allowEmptyShould(true);
    }

    /**
     * トークンの検証（パース）を行っていないことを検査する。
     *
     * <p>authms は発行のために JWT ライブラリを持つが、検証は行わない。依存の有無ではなく
     * 「検証の入口を呼んでいないこと」で判定する。
     */
    public static ArchRule noTokenVerificationRule(String serviceName) {
        return noClasses().that().resideInAPackage("com.example." + serviceName + "..")
                .should(new ArchCondition<JavaClass>("JWT の検証 API を呼ぶ") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        javaClass.getMethodCallsFromSelf().stream()
                                .filter(call -> call.getTargetOwner().getName().startsWith("io.jsonwebtoken")
                                        && VERIFICATION_METHODS.contains(call.getName()))
                                .forEach(call -> events.add(SimpleConditionEvent.violated(
                                        javaClass, call.getDescription())));
                    }
                })
                .as("署名検証は gatewayms だけが行う（ADR-004）")
                .allowEmptyShould(true);
    }

    /**
     * JWT を扱うライブラリのパッケージ。
     *
     * <p>ADR-004 が恐れるのは「Spring Security を素直に入れたら署名検証が付いてきた」ことなので、
     * 特定のライブラリ名で書かない。jjwt だけを禁じても、resource-server を入れれば
     * 同じことが起きる。
     */
    private static final String[] JWT_LIBRARY_PACKAGES = {
        "io.jsonwebtoken..",
        "org.springframework.security.oauth2..",
        "com.nimbusds..",
        "org.springframework.security.web.."
    };

    /** JWT の検証を始める入口となるメソッド名。 */
    private static final java.util.Set<String> VERIFICATION_METHODS =
            java.util.Set.of("parser", "parserBuilder", "parseSignedClaims", "parseClaimsJws");

    /**
     * 入力の検査を、認可より先に走らせないことを検査する（ADR-016）。
     *
     * <p>{@code @Valid} / {@code @Validated} をパラメータに付けると、Spring は<strong>メソッド本体に
     * 入る前に</strong>検証を走らせる。すると権限の無い呼び出しでも本文が不正なら 400 が返り、
     * 本人には「この操作はできない」ではなく「入力を直せ」と伝わる。権限が無いはずの相手に
     * エンドポイントの入力仕様を教えることにもなる。
     *
     * <p>そのため<strong>入力の検査はメソッド本体で明示的に呼ぶ</strong>（認可のあと）。
     *
     * <p>対象は<strong>認可の対象となるメソッド</strong>、すなわち Gateway が付けた利用者ヘッダ
     * （{@code AuthenticatedUser.USER_ID_HEADER}）を受け取るものに限る。ログインのように認可が
     * 存在しない入口には、隠すべき権限差が無い。免除するサービスを名簿で挙げるのではなく、
     * <strong>コードの形（利用者ヘッダを受け取るか）から対象を導く</strong>ため、新しい
     * エンドポイントを足せばそれだけで対象になる。
     */
    public static ArchRule validationAfterAuthorizationRule() {
        // noClasses() は条件を反転させるため、違反イベントを足す条件と噛み合わない
        // （違反が「満たした」扱いになり、何を書いても緑になる）。classes() で書く。
        return classes()
                .should(new ArchCondition<JavaClass>("認可の対象となるメソッドのパラメータに @Valid / @Validated を付けない（ADR-016）") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        javaClass.getMethods().stream()
                                .filter(HexagonalArchitectureRules::subjectToAuthorization)
                                .forEach(method -> method.getParameters().forEach(parameter ->
                                        parameter.getAnnotations().stream()
                                                .map(annotation -> annotation.getRawType().getName())
                                                .filter(VALIDATION_ANNOTATIONS::contains)
                                                .forEach(name -> events.add(SimpleConditionEvent.violated(method,
                                                        "%s#%s のパラメータに %s が付いている。認可より先に検証が走る（ADR-016）"
                                                                .formatted(javaClass.getSimpleName(),
                                                                        method.getName(), name))))));
                    }
                })
                .as("入力の検査は認可のあとに、メソッド本体で行う（ADR-016）")
                .allowEmptyShould(true);
    }

    /** Gateway が付けた利用者ヘッダを受け取るメソッドは、認可の対象である。 */
    private static boolean subjectToAuthorization(com.tngtech.archunit.core.domain.JavaMethod method) {
        return method.getParameters().stream()
                .flatMap(parameter -> parameter.getAnnotations().stream())
                .filter(annotation -> REQUEST_HEADER_ANNOTATION.equals(annotation.getRawType().getName()))
                .anyMatch(annotation -> USER_ID_HEADER.equals(annotation.get("value").orElse(null))
                        || USER_ID_HEADER.equals(annotation.get("name").orElse(null)));
    }

    private static final String REQUEST_HEADER_ANNOTATION =
            "org.springframework.web.bind.annotation.RequestHeader";

    /** 本番の定数をそのまま使う。書き写すと、ヘッダ名を変えたときに検査だけが取り残される。 */
    private static final String USER_ID_HEADER = com.example.shared.auth.AuthenticatedUser.USER_ID_HEADER;

    /** メソッド本体に入る前に検証を走らせてしまう注釈。 */
    private static final java.util.Set<String> VALIDATION_ANNOTATIONS = java.util.Set.of(
            "jakarta.validation.Valid",
            "org.springframework.validation.annotation.Validated");

    /**
     * サービスの本番クラス（テストを除く）を読み込む。
     *
     * <p>1 件も読めていない場合は落とす。0 件のまま規則を評価しても常に緑になり、
     * 「検査しているつもりで何も見ていない」状態に気づけない。
     */
    public static JavaClasses importProductionClasses(String basePackage) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
        if (!classes.iterator().hasNext()) {
            throw new IllegalStateException(
                    "%s のクラスが 1 件も読み込めていません。この状態では規則を評価しても常に緑になります"
                            .formatted(basePackage));
        }
        return classes;
    }

    /**
     * メッセージ基盤のパッケージ。IT6 でイベントを出すときに、この一覧を同じ変更で見直す。
     *
     * <p><strong>型を名指しで列挙しない。</strong>「置いてはいけないものを挙げる」形にすると、
     * 思いつかなかったもの（{@code RabbitMessagingTemplate}・{@code AmqpAdmin}・
     * {@code ApplicationEventPublisher} など）が素通りする。同じファイルの
     * {@link #sharedKernelScopeRule()} が同じ理由で「置いてよいものを列挙する」形を採っている。
     */
    private static final java.util.List<String> MESSAGING_PACKAGES = java.util.List.of(
            "org.springframework.amqp.",
            "org.springframework.cloud.stream.",
            "org.springframework.context.ApplicationEventPublisher");

    /**
     * ドメインイベントをまだ発行しない（[ADR-019] 決定 3）。
     *
     * <p>イベント基盤（RabbitMQ / Spring Cloud Stream）は IT6 である。依存だけが先に入ると、
     * 「発行しているつもり」で誰も受け取っていない状態が生まれ、しかも実行時まで分からない。
     *
     * <p><strong>IT6 でイベントを発行するときは、この検査を同じ変更で外す。</strong>
     * 外し忘れると、発行を足した瞬間に理由の分からない赤になる。
     */
    public static ArchRule noEventPublishingRule() {
        return classes()
                .should(new ArchCondition<JavaClass>("メッセージ基盤に依存しない（ADR-019 決定 3。IT6 で解禁）") {
                    @Override
                    public void check(JavaClass javaClass, ConditionEvents events) {
                        javaClass.getDirectDependenciesFromSelf().stream()
                                .map(dependency -> dependency.getTargetClass().getName())
                                .filter(name -> MESSAGING_PACKAGES.stream()
                                        .anyMatch(name::startsWith))
                                .distinct()
                                .forEach(name -> events.add(SimpleConditionEvent.violated(javaClass,
                                        "%s が %s に依存している。イベント発行は IT6（ADR-019 決定 3）"
                                                .formatted(javaClass.getSimpleName(), name))));
                    }
                })
                .as("ドメインイベントはまだ発行しない（ADR-019 決定 3）")
                .allowEmptyShould(true);
    }
}
