import fs from 'fs';
import path from 'path';

const srcDir = path.resolve('dist');
const destDir = path.resolve('../backend/src/main/resources/static');

try {
  if (fs.existsSync(srcDir)) {
    fs.rmSync(destDir, { recursive: true, force: true });
    fs.mkdirSync(destDir, { recursive: true });
    fs.cpSync(srcDir, destDir, { recursive: true });
    console.log('[copy-build] Successfully synchronized frontend/dist -> backend/src/main/resources/static');
  }
} catch (err) {
  console.warn('[copy-build] Copy notice:', err.message);
}
