'use strict';
// Deploys schema.sql statement-by-statement to a project via Management API.
// Token comes from the environment — never hardcoded, never printed.
//   set SUPABASE_ACCESS_TOKEN=<personal access token>
//   (create one at https://supabase.com/dashboard/account/tokens)
// Usage: node supabase/deploy_schema.js <project-ref>
const fs = require('fs');
const path = require('path');

const TOKEN = process.env.SUPABASE_ACCESS_TOKEN;
if (!TOKEN) {
  console.error('SUPABASE_ACCESS_TOKEN is not set — export it or set it in .env before running.');
  process.exit(1);
}

async function runSqlOn(project, sql) {
  const r = await fetch(
    `https://api.supabase.com/v1/projects/${project}/database/query`,
    {
      method: 'POST',
      headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: sql }),
    });
  const text = await r.text();
  if (!r.ok) throw new Error(`HTTP ${r.status}: ${text.slice(0, 300)}`);
  return text ? JSON.parse(text) : null;
}

(async () => {
  const project = process.argv[2];
  if (!project) { console.error('usage: node supabase/deploy_schema.js <ref>'); process.exit(1); }
  const raw = fs.readFileSync(path.join('supabase', 'schema.sql'), 'utf8');
  const clean = raw.split('\n').map((l) => l.replace(/(^|\s)--.*$/, '$1')).join('\n');
  const stmts = clean.split(';').map((s) => s.trim()).filter((s) => s.length > 1);
  let ok = 0;
  for (const [i, s] of stmts.entries()) {
    try { await runSqlOn(project, s + ';'); ok++; }
    catch (e) { console.error(`FAIL ${i + 1}/${stmts.length}: ${s.slice(0, 80)} :: ${e.message}`); process.exit(1); }
  }
  console.log(`ALL APPLIED ${ok}/${stmts.length}`);
})().catch((e) => { console.error('ERROR: ' + e.message); process.exit(1); });
