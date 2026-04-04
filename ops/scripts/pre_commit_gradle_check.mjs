#!/usr/bin/env node

/**
 * Pre-commit hook script for Java source files.
 * Runs Gradle check task in the cargo-tracker project.
 */

import { execSync } from 'child_process';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(__dirname, '../../apps/cargo-tracker');

console.log('Running Gradle check in apps/cargo-tracker...');

try {
    execSync('./gradlew check', {
        cwd: projectRoot,
        stdio: 'inherit',
        timeout: 300000,
    });
    console.log('Gradle check passed.');
} catch (error) {
    console.error('Gradle check failed. Please fix the issues before committing.');
    process.exit(1);
}
