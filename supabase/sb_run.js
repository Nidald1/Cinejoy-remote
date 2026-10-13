'use strict';
// Runs SQL against a Supabase project via the Management API.
// Reuses the existing POS tooling's token WITHOUT printing it.
// Usage: node sb_run.js <project-ref> "<sql>" | --file <path>
const fs = require('fs');
const path = require('path');
const { runSql: _unused } = require('D:/From Desktop/New folder (2) - Copy/tools/sb_sql.js');

const TOOL_SRC = fs.readFileSync(
  'D:/From Desktop/New folder (2) - Copy/tools/sb_sql.js', 'utf8');
const TOKEN = (TOOL_SRC.match(/sbp_[A-Za-z0-9]+/) || [])[0];
if (!TOKEN) { console.error('token not found'); process.exit(1); }

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
