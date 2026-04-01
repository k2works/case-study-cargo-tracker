import { spawnSync } from 'node:child_process';

const stagedFiles = process.argv.slice(2);
const hasJavaChanges = stagedFiles.some((file) => file.endsWith('.java'));

if (!hasJavaChanges) {
  console.log('No staged Java files. Skipping Gradle check.');
  process.exit(0);
}

const command = process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew';
const result = spawnSync(command, ['check'], {
  cwd: 'apps/cargo-tracker',
  stdio: 'inherit',
  shell: process.platform === 'win32',
});

if (result.status !== 0) {
  process.exit(result.status ?? 1);
}
