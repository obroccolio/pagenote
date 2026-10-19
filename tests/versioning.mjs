import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { checkReleaseVersion } from '../scripts/check-release-version.mjs';
const root = await fs.mkdtemp(path.join(os.tmpdir(), 'pagenote-version-test-'));
const version = '2.0.0-beta.5';
try {
  await fs.writeFile(
    path.join(root, 'package.json'),
    JSON.stringify({ version }),
  );
  const lock = { version, packages: { '': { version } } };
  await fs.writeFile(
    path.join(root, 'package-lock.json'),
    JSON.stringify(lock),
  );
  for (const name of ['README.md', 'README.en.md'])
    await fs.writeFile(
      path.join(root, name),
      '[Download](https://github.com/example/app/releases/download/v' +
        version +
        '/app.zip)',
    );
  await fs.writeFile(
    path.join(root, 'CHANGELOG.md'),
    '## ' + version + ' — 2026-10-16\n',
  );
  await checkReleaseVersion(root, { release: true, tag: 'v' + version });
  const readmePath = path.join(root, 'README.md');
  const validReadme = await fs.readFile(readmePath, 'utf8');
  await fs.writeFile(
    readmePath,
    validReadme + '\n当前公开版本：2.0.0-beta.4。',
  );
  await assert.rejects(
    checkReleaseVersion(root, { release: true }),
    /public version text/,
  );
  await fs.writeFile(readmePath, validReadme);
  await assert.rejects(
    checkReleaseVersion(root, { release: true, tag: 'v2.0.0-beta.4' }),
    /tag/,
  );
  await fs.writeFile(
    path.join(root, 'README.md'),
    '[Download](https://github.com/example/app/releases/download/v2.0.0-beta.4/app.zip)',
  );
  await assert.rejects(checkReleaseVersion(root, { release: true }), /README/);
  await checkReleaseVersion(root);
  lock.packages[''].version = '2.0.0-beta.4';
  await fs.writeFile(
    path.join(root, 'package-lock.json'),
    JSON.stringify(lock),
  );
  await assert.rejects(checkReleaseVersion(root), /lock/);
  const androidVersion = '1.0.0-beta.2';
  const devVersion = '1.0.1-dev.5';
  await fs.writeFile(
    path.join(root, 'package.json'),
    JSON.stringify({ version: devVersion }),
  );
  lock.version = devVersion;
  lock.packages[''].version = devVersion;
  await fs.writeFile(
    path.join(root, 'package-lock.json'),
    JSON.stringify(lock),
  );
  await fs.mkdir(path.join(root, 'android', 'app'), { recursive: true });
  await fs.writeFile(
    path.join(root, 'android', 'app', 'build.gradle.kts'),
    'versionCode = 2\nversionName = "1.0.0"\nversionNameSuffix = "-beta.2"\n',
  );
  await fs.writeFile(
    path.join(root, 'CHANGELOG.md'),
    '## Android 1.0.0-beta.2 — 2026-10-19\n',
  );
  const androidRelease = await checkReleaseVersion(root, {
    release: true,
    tag: 'android-v' + androidVersion,
  });
  assert.equal(androidRelease.version, androidVersion);
  assert.equal(androidRelease.tag, 'android-v' + androidVersion);
  assert.equal(androidRelease.channel, 'android');
  const detectedAndroidRelease = await checkReleaseVersion(root, {
    release: true,
  });
  assert.equal(detectedAndroidRelease.channel, 'android');
  console.log(
    'PASS desktop and Android version, lockfile, release links, changelog and tag drift gates',
  );
} finally {
  const relative = path.relative(os.tmpdir(), root);
  assert.ok(
    relative.startsWith('pagenote-version-test-') &&
      !relative.includes(path.sep),
  );
  await fs.rm(root, { recursive: true, force: true });
}
