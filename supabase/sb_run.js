'use strict';
// Runs SQL against a Supabase project via the Management API.
// Token comes from the environment — never hardcoded, never printed.
//   set SUPABASE_ACCESS_TOKEN=<personal access token>
//   (create one at https://supabase.com/dashboard/account/tokens)
// Usage: node sb_run.js <project-ref> "<sql>" | --file <path>
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
  const a = process.argv[3];
  const sql = a === '--file'
    ? fs.readFileSync(process.argv[4], 'utf8')
    : a;
  try {
    const out = await runSqlOn(project, sql);
    console.log(out === null ? 'OK' : JSON.stringify(out).slice(0, 2000));
  } catch (e) { console.error('ERROR: ' + e.message); process.exit(1); }
})();
