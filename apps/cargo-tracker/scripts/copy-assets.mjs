import { copyFileSync, mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

// node_modules の静的アセットを public/vendor へ配置する（WebJars 相当）
const COPIES = [
  ['node_modules/htmx.org/dist/htmx.min.js', 'public/vendor/htmx/htmx.min.js'],
  ['node_modules/bootstrap/dist/css/bootstrap.min.css', 'public/vendor/bootstrap/bootstrap.min.css'],
];

for (const [src, dest] of COPIES) {
  mkdirSync(dirname(dest), { recursive: true });
  copyFileSync(src, dest);
  console.log(`copied ${src} -> ${dest}`);
}
