// Package an already-built local Release. No network, Git writes or signing-key access.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
const root = process.cwd();
const version = '2.5.6-work.5', code = '198';
const output = path.resolve(process.argv[2] || `artifacts/${version}/F013-browser-cookies-desktop-release-20261020`);
assert.ok(output.startsWith(`${root}/artifacts/${version}/`));
fs.mkdirSync(output, {recursive:true});
const run = (cmd, args, options = {}) => execFileSync(cmd, args, {cwd:root, encoding:'utf8', maxBuffer:64*1024*1024, ...options});
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const save = (name, value) => fs.writeFileSync(path.join(output, name), typeof value === 'string' ? value : JSON.stringify(value, null, 2)+'\n');
const sdk = fs.readFileSync('local.properties', 'utf8').match(/^sdk.dir=(.+)$/m)?.[1].trim();
assert.ok(sdk, 'Android SDK is configured');
const buildTools = path.join(sdk, 'build-tools/37.0.0');
const apkResults = [];
let referenceCert;
for (const abi of ['arm64-v8a', 'x86_64', 'universal']) {
  const source = path.join(root, `app/build/outputs/apk/release/app-${abi}-release.apk`);
  const name = `RikkaHub.Work.${version}-${abi}-release.apk`;
  const destination = path.join(output, name);
  fs.copyFileSync(source, destination);
  const badging = run(path.join(buildTools, 'aapt2'), ['dump', 'badging', destination]);
  assert.match(badging, new RegExp(`package: name='me.rerere.rikkahub.plus' versionCode='${code}' versionName='${version.replaceAll('.', '\\.')}'`));
  assert.ok(!badging.includes('application-debuggable'));
  const labels = [...badging.matchAll(/^application-label(?:-[^:]+)?:'([^']*)'/gm)].map(m => m[1]);
  assert.ok(labels.length > 0 && labels.every(label => label === 'RikkaHub Work'));
  const locales = [...(badging.match(/^locales:(.*)$/m)?.[1] || '').matchAll(/'([^']*)'/g)].map(m=>m[1]).filter(Boolean);
  assert.ok(locales.every(locale=>['--_--','en','zh','zh-CN','zh-TW'].includes(locale)), `Unexpected locales: ${locales}`);
  const entries = run('unzip', ['-Z1', destination]).trim().split('\n');
  const abis = [...new Set(entries.filter(e=>e.startsWith('lib/')).map(e=>e.split('/')[1]))].sort();
  assert.deepEqual(abis, abi === 'universal' ? ['arm64-v8a','x86_64'] : [abi]);
  for (const asset of ['readability.js','readability-readerable.js','LICENSE-APACHE-2.0.txt','NOTICE.txt'])
    assert.ok(entries.includes(`assets/browser/${asset}`), `${asset} is packaged`);
  assert.ok(entries.includes('assets/deepseek_sha3.wasm'));
  run('unzip', ['-tqq', destination]);
  const signature = run(path.join(buildTools, 'apksigner'), ['verify','--verbose','--print-certs',destination]);
  const cert = signature.match(/certificate SHA-256 digest: ([^\s]+)/)?.[1];
  assert.ok(cert);
  if (referenceCert) assert.equal(cert, referenceCert); else referenceCert = cert;
  const previousApk = path.join(root, 'artifacts/2.5.6-work.3/F012-release-20261014/RikkaHub.Work.2.5.6-work.3-arm64-v8a-release.apk');
  const previousSignature = run(path.join(buildTools, 'apksigner'), ['verify','--print-certs',previousApk]);
  assert.equal(cert, previousSignature.match(/certificate SHA-256 digest: ([^\s]+)/)?.[1], 'Release certificate matches work.3');
  save(`APK-${abi}-BADGING.txt`, badging); save(`APK-${abi}-SIGNATURE.txt`, signature);
  apkResults.push({file:name, bytes:fs.statSync(destination).size, sha256:hash(destination), package:'me.rerere.rikkahub.plus',
    versionName:version, versionCode:code, debuggable:false, abis, locales, certSha256:cert});
}
save('APK-VERIFICATION.json', apkResults);
const requiredFiles = [
  'app/src/main/java/me/rerere/rikkahub/browser/BrowserController.kt',
  'app/src/main/java/me/rerere/rikkahub/browser/BrowserDataCleaner.kt',
  'app/src/main/java/me/rerere/rikkahub/browser/BrowserSites.kt',
  'app/src/main/java/me/rerere/rikkahub/browser/BrowserJsBridge.kt',
  'app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/BrowserTools.kt',
  'app/src/main/java/me/rerere/rikkahub/ui/pages/browser/BrowserPage.kt',
  'app/src/main/java/me/rerere/rikkahub/ui/pages/browser/BrowserMiniPanel.kt',
  'app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingBrowserCookiesPage.kt',
  'app/src/main/java/me/rerere/rikkahub/service/GenerationWatchdog.kt',
  'common/src/main/java/me/rerere/common/android/RequestTiming.kt',
  'search/src/main/java/me/rerere/search/DeepSeekSearchService.kt',
  'search/src/main/java/me/rerere/search/AnySearchService.kt',
  'search/src/main/java/me/rerere/search/SearchService.kt',
];
for (const file of requiredFiles) assert.ok(fs.existsSync(file), `${file} is present`);
assert.match(fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8'), /android\.permission\.WAKE_LOCK/);
assert.match(fs.readFileSync('search/src/main/java/me/rerere/search/SearchService.kt', 'utf8'), /@SerialName\("deepseek"\)/);
assert.match(fs.readFileSync('search/src/main/java/me/rerere/search/SearchService.kt', 'utf8'), /@SerialName\("anysearch"\)/);
// Also validate actual app resource trees, including any untracked directories.
const languages = ['values','values-zh','values-zh-rTW'];
assert.deepEqual(fs.readdirSync('app/src/main/res').filter(p=>p.startsWith('values')).sort(), languages);
assert.deepEqual(fs.readdirSync('search/src/main/res').filter(p=>p.startsWith('values')).sort(), languages);
const keysOf = file => {
  const xml = fs.readFileSync(file, 'utf8');
  return [...xml.matchAll(/<string name="([^"]+)"/g)].map(m=>m[1]);
};
const requiredAppKeys = [
  'search_deepseek_api_key', 'search_deepseek_max_results', 'search_deepseek_max_results_hint',
  'search_deepseek_base_url', 'search_deepseek_base_url_hint',
  'search_anysearch_api_key', 'search_anysearch_api_key_hint', 'search_anysearch_max_results',
  'search_anysearch_max_results_hint', 'error_generation_timeout',
  'error_generation_interrupted_by_sleep', 'log_request_duration_with_sleep',
];
const requiredSearchKeys = ['search_deepseek_description', 'search_anysearch_description', 'search_anysearch_website'];
const languageResults = languages.map(lang => {
  const file = `app/src/main/res/${lang}/strings.xml`;
  run('xmllint', ['--noout',file]);
  const keys = keysOf(file);
  assert.equal(keys.length, new Set(keys).size, `No duplicate keys in ${lang}`);
  for (const key of requiredAppKeys) assert.ok(keys.includes(key), `${key} is present in ${lang}`);
  assert.equal(keys.filter(k=>k.startsWith('memory_import_')).length,7);
  assert.equal(keys.filter(k=>k.startsWith('browser_ui_')).length,18);
  assert.equal(keys.filter(k=>k.startsWith('browser_cookies_')).length,21);
  assert.equal(keys.filter(k=>/^(assistant_page_local_tools_browser_|browser_setting_|browser_mode_|browser_status_)/.test(k)).length,12);
  const searchFile = `search/src/main/res/${lang}/strings.xml`;
  run('xmllint', ['--noout',searchFile]);
  const searchKeys = keysOf(searchFile);
  assert.equal(searchKeys.length, new Set(searchKeys).size, `No duplicate keys in ${searchFile}`);
  for (const key of requiredSearchKeys) assert.ok(searchKeys.includes(key), `${key} is present in ${lang}`);
  return {lang, memoryImportKeys:7, browserUiKeys:18, browserSettingKeys:12,
    appSearchKeys:requiredAppKeys.length, searchModuleKeys:requiredSearchKeys.length};
});
save('LANGUAGE-VERIFICATION.json',languageResults);
// Existing Debug APK remains exactly the prior artifact; test compilation is not APK packaging.
const debugFile = 'app/build/outputs/apk/debug/app-arm64-v8a-debug.apk';
assert.equal(hash(debugFile), '168440930bf0086199def31497ec9aa2bfb0d972e7b83f100fed83c23f6fd341');
save('DEBUG-UNCHANGED.json', {file:debugFile, sha256:hash(debugFile), mtime:fs.statSync(debugFile).mtime.toISOString()});
// Read current Git-tracked sources (including submodule files), plus non-ignored new files.
// Exclude local credentials, artifacts, builds, caches, and Git metadata by path as defense in depth.
const names = [...new Set((run('git',['ls-files','--recurse-submodules','-z'])+
  run('git',['ls-files','--others','--exclude-standard','-z'])).split('\0').filter(Boolean))].sort();
const excluded = /(^|\/)(\.git|\.gradle|\.gradle-user|\.idea|node_modules|build|artifacts|\.env[^/]*|local\.properties|keystore\.properties|signing\.properties)(\/|$)|\.(jks|keystore|apk|aab|pem|p12|pfx)$/i;
const files = names.filter(p=>!excluded.test(p) && fs.existsSync(p) && !fs.lstatSync(p).isDirectory());
assert.ok(files.length > 1800);
for (const file of requiredFiles) assert.ok(files.includes(file), `${file} is included in source archive`);
assert.ok(files.some(p=>p.startsWith('material3/material-color-utilities/')));
const archive = path.join(output, `RikkaHub.Work.${version}-source.zip`);
if (fs.existsSync(archive)) {
  assert.equal(process.env.REPLACE_SOURCE_ARCHIVE, '1', 'Set REPLACE_SOURCE_ARCHIVE=1 only to refresh this generated source archive');
  fs.rmSync(archive);
}
run('zip', ['-q','-9','-y',archive,'-@'], {input:files.join('\n')+'\n'});
run('unzip',['-tqq',archive]);
const zipped = run('unzip',['-Z1',archive]).trim().split('\n').sort();
assert.deepEqual(zipped, files);
const manifest = files.map(file => ({file, sha256:fs.lstatSync(file).isSymbolicLink() ? null : hash(file)}));
// Extract each zipped regular file to stdout and verify it equals the live workspace.
for (const item of manifest) {
  if (!item.sha256) continue;
  const data = execFileSync('unzip',['-p',archive,item.file],{maxBuffer:64*1024*1024});
  assert.equal(crypto.createHash('sha256').update(data).digest('hex'), item.sha256, item.file);
}
save('SOURCE-MANIFEST.json',manifest);
save('SOURCE-VERIFICATION.json',{file:path.basename(archive), files:files.length, sha256:hash(archive),
  allFilesMatchWorkspace:true, includesSubmodule:true, includesUntrackedSources:true, excludesLocalCredentials:true});
const changed = [...new Set((run('git',['diff','--name-only','-z'])+run('git',['ls-files','--others','--exclude-standard','-z']))
  .split('\0').filter(Boolean))].filter(file=>files.includes(file));
save('CHANGED-FILES.tsv','file\tlines\n'+changed.map(file=>`${file}\t${fs.readFileSync(file,'utf8').split('\n').length-1}`).join('\n')+'\n');
const delivered = [...apkResults.map(a=>a.file),path.basename(archive)];
save('SHA256SUMS',delivered.map(file=>`${hash(path.join(output,file))}  ${file}`).join('\n')+'\n');
console.log(JSON.stringify({version,versionCode:code,output,apks:apkResults.map(a=>({file:a.file,bytes:a.bytes})),sourceFiles:files.length},null,2));
