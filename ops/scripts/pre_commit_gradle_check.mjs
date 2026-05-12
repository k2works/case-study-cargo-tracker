#!/usr/bin/env node

/**
 * Pre-commit hook script for Java source files.
 * Runs Gradle check task in the apps project.
 */

import { execSync } from 'child_process';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(__dirname, '../../apps/backend');
// Windows の cmd は cwd を PATH に含めないため、.bat も明示的に相対パスで指定する必要がある
const gradlew = process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew';

console.log('Running Gradle check in apps/backend/...');

try {
    execSync(`${gradlew} check -x test`, {
        cwd: projectRoot,
        stdio: 'inherit',
        timeout: 300000,
    });
    console.log('Gradle check passed.');
} catch (error) {
    console.error('Gradle check failed. Please fix the issues before committing.');
    process.exit(1);
}
