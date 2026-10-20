import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
const files = execFileSync('find', ['.','-path','*/build/test-results/testDebugUnitTest/TEST-*.xml'],{encoding:'utf8'})
  .trim().split('\n').filter(Boolean);
const suites = files.map(file => {
  const xml = fs.readFileSync(file,'utf8');
  const header = xml.match(/<testsuite\b[^>]+>/)?.[0];
  if (!header) throw new Error(`Not a test report: ${file}`);
  const attrs = Object.fromEntries([...header.matchAll(/(\w+)="([^"]*)"/g)].map(m=>[m[1],m[2]]));
  return {file,name:attrs.name,tests:Number(attrs.tests),failures:Number(attrs.failures),errors:Number(attrs.errors),
    skipped:Number(attrs.skipped),reportModifiedAt:fs.statSync(file).mtime.toISOString()};
});
const totals = suites.reduce((s,t)=>({suites:s.suites+1,tests:s.tests+t.tests,failures:s.failures+t.failures,
  errors:s.errors+t.errors,skipped:s.skipped+t.skipped}),{suites:0,tests:0,failures:0,errors:0,skipped:0});
console.log(JSON.stringify({totals,newBrowserAndImportSuites:suites.filter(s=>s.name.includes('.browser.')),
  note:'All-module Gradle test task succeeded; unchanged module reports may be reused as UP-TO-DATE. This is JVM validation, not device or Release-variant testing.',suites},null,2));
if (totals.failures || totals.errors) process.exitCode = 1;
