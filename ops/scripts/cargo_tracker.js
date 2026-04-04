'use strict';

import { execSync, spawn } from 'child_process';
import { openUrl } from './shared.js';

const CARGO_TRACKER_DIR = 'apps/cargo-tracker';
const GRADLEW = './gradlew';

/**
 * Gradle コマンドを実行する
 * @param {string} task - Gradle タスク名
 * @param {string[]} args - 追加引数
 */
function runGradle(task, args = []) {
    const cmd = [GRADLEW, task, ...args].join(' ');
    execSync(cmd, {
        cwd: CARGO_TRACKER_DIR,
        stdio: 'inherit',
    });
}

/**
 * cargo-tracker 用 Gulp タスクを登録
 * @param {import('gulp')} gulp
 */
export default function cargoTrackerTasks(gulp) {

    // Development server (default profile / H2)
    gulp.task('cargo-tracker:dev', (done) => {
        console.log('Starting cargo-tracker (default profile / H2)...');
        const child = spawn(GRADLEW, ['bootRun'], {
            cwd: CARGO_TRACKER_DIR,
            stdio: 'inherit',
            shell: true,
        });
        child.on('close', (code) => done(code ? new Error(`Exit code: ${code}`) : undefined));
    });

    // Development server (product profile / PostgreSQL)
    gulp.task('cargo-tracker:dev:product', (done) => {
        console.log('Starting cargo-tracker (product profile / PostgreSQL)...');
        const child = spawn(GRADLEW, ['bootRun', "--args='--spring.profiles.active=product'"], {
            cwd: CARGO_TRACKER_DIR,
            stdio: 'inherit',
            shell: true,
        });
        child.on('close', (code) => done(code ? new Error(`Exit code: ${code}`) : undefined));
    });

    // TDD mode (continuous test)
    gulp.task('cargo-tracker:tdd', (done) => {
        console.log('Starting TDD mode (continuous test)...');
        const child = spawn(GRADLEW, ['test', '--continuous'], {
            cwd: CARGO_TRACKER_DIR,
            stdio: 'inherit',
            shell: true,
        });
        child.on('close', (code) => done(code ? new Error(`Exit code: ${code}`) : undefined));
    });

    // Run tests
    gulp.task('cargo-tracker:test', (done) => {
        try {
            runGradle('test', ['jacocoTestReport']);
            done();
        } catch (e) {
            done(e);
        }
    });

    // Build
    gulp.task('cargo-tracker:build', (done) => {
        try {
            runGradle('build');
            done();
        } catch (e) {
            done(e);
        }
    });

    // Quality check
    gulp.task('cargo-tracker:check', (done) => {
        try {
            runGradle('check');
            done();
        } catch (e) {
            done(e);
        }
    });

    // Open in browser
    gulp.task('cargo-tracker:open', (done) => {
        openUrl('http://localhost:8080');
        done();
    });
}
