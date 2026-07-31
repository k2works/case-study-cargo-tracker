'use strict';

/**
 * Gulpfile that loads tasks from the script directory
 */

import 'dotenv/config';
import gulp from 'gulp';
import mkdocsTasks from './ops/scripts/mkdocs.js';
import journalTasks from './ops/scripts/journal.js';
import vaultTasks from './ops/scripts/vault.js';
import sshTasks from './ops/scripts/ssh.js';
import sonarLocalTasks from './ops/scripts/sonar_local.js';
import developTasks from './ops/scripts/develop.js';
import archLintTasks from './ops/scripts/arch_lint.js';

// Load gulp tasks from script modules
mkdocsTasks(gulp);
journalTasks(gulp);
vaultTasks(gulp);
sshTasks(gulp);
sonarLocalTasks(gulp);
developTasks(gulp);
archLintTasks(gulp);

export const spec = gulp.series('mkdocs:serve', 'mkdocs:open');

// Export gulp to make it available to the gulp CLI
export default gulp;
