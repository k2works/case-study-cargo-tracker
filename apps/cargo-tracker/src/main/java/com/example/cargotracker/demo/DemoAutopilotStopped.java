package com.example.cargotracker.demo;

/**
 * 自動実行が途中で止まった。
 *
 * <p><strong>捕まえた例外をそのまま投げ直さない。</strong> 投げ直すと、呼ぶ側は
 * 「業務のサービスが拒んだ」のか「進み具合の記録に失敗した」のかを区別できない。
 * <strong>この型で包まれているものは、止まったことが既に記録されている。</strong>
 */
final class DemoAutopilotStopped extends RuntimeException {

    private static final long serialVersionUID = 1L;

    DemoAutopilotStopped(String message, Throwable cause) {
        super(message, cause);
    }
}
