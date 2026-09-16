// PACK THE SITE INTO THE APK.
//
// Copies the live site into app/src/main/assets and makes the two edits a
// packaged build needs. Run it before every gradle build; it is the only step
// between "pushed to sixthpath.com" and "shipped in the app".
//
//   node pack.mjs
//
// Paths inside the game are absolute (/js/sim/…, the import map's keys), and
// WebViewAssetLoader mounts the asset folder at "/", so nothing is rewritten.

import fs from 'node:fs';
import path from 'node:path';

const SRC = 'C:/Users/adogu/quantum-last';
const DST = path.join(import.meta.dirname, 'app/src/main/assets');

// What the app actually loads. Editor scratch pages (panel-preview.html,
// scene-probe.html, the old /echoes RPG) are not shipped — they would double
// the APK for pages no player can reach.
const TAKE = ['index.html', 'css', 'js', 'img', 'snd', 'fonts', 'manifest.webmanifest'];

function copy(src, dst) {
  const st = fs.statSync(src);
  if (st.isDirectory()) {
    fs.mkdirSync(dst, { recursive: true });
    for (const e of fs.readdirSync(src)) copy(path.join(src, e), path.join(dst, e));
  } else {
    fs.copyFileSync(src, dst);
  }
}

// ICINI BOSALT, KLASORU SILME. Windows'ta assets klasorunun KENDISINE bir
// tutamak (Explorer penceresi, bir kabugun calisma dizini, indeksleyici)
// yetiyor ve rmSync EPERM/"resource busy" ile oluyor -- olculdu, 2026-09-06:
// klasor bombostu ve yine de silinemedi. Cocuklarini silmek ayni sonucu
// veriyor ve bu tuzaga hic girmiyor.
fs.mkdirSync(DST, { recursive: true });
for (const e of fs.readdirSync(DST)) fs.rmSync(path.join(DST, e), { recursive: true, force: true });
for (const t of TAKE) copy(path.join(SRC, t), path.join(DST, t));

// ---- the two packaged-build edits ------------------------------------------
const idx = path.join(DST, 'index.html');
let h = fs.readFileSync(idx, 'utf8');
const before = h;

// 1. GOOGLE FONTS. css/sim.css already @font-faces the woff2 files out of
//    fonts/, so the stylesheet link was redundant even on the web. Offline it
//    is worse than redundant: the page waits on a DNS lookup that cannot
//    resolve before it paints.
h = h.replace(/[ \t]*<link rel="preconnect" href="https:\/\/fonts\.[^>]*>\r?\n/g, '');
h = h.replace(/[ \t]*<link[^>]*fonts\.googleapis\.com\/css2[^>]*>\r?\n/g, '');

// 2. THE SERVICE WORKER. Its job on the web is to hold a copy of the site for
//    offline play. Here the assets ARE the app; a second cached copy of them
//    inside the WebView is dead weight that can also serve a stale build after
//    an update. Registration is cut; the existing unregister path stays, so an
//    install that ever ran one cleans itself up.
h = h.replace(/navigator\.serviceWorker\.register\((['"])\/sw\.js[^)]*\)/g,
              'Promise.reject(new Error("packaged build: no service worker"))');

if (h === before) throw new Error('index.html: neither edit matched — check the markup');
fs.writeFileSync(idx, h);

// ---- report ----------------------------------------------------------------
let bytes = 0, files = 0;
(function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p); else { bytes += fs.statSync(p).size; files++; }
  }
})(DST);

const build = (fs.readFileSync(idx, 'utf8').match(/[?&]v=(\d+)/) || [])[1] || '?';

// ---- the android version follows the web build -----------------------------
// versionCode used to be a hand-written constant, so every bundle shipped as the
// same number and Play refused all but the first ("Version code 562 has already
// been used"). The packaged build number is the only version this app has; the
// gradle file is now written from it on every pack, and the two cannot drift.
if (/^\d+$/.test(build)) {
  const gradle = new URL('./app/build.gradle', import.meta.url);
  const g0 = fs.readFileSync(gradle, 'utf8');
  const g1 = g0
    .replace(/versionCode\s+\d+/, `versionCode ${build}`)
    .replace(/versionName\s+"[^"]*"/, `versionName "1.0.${build}"`);
  if (g1 !== g0) { fs.writeFileSync(gradle, g1); console.log(`versionCode -> ${build}`); }
} else {
  console.warn('build number unreadable; versionCode left alone');
}

console.log(`packed build ${build}: ${files} files, ${(bytes / 1048576).toFixed(1)} MB`);
