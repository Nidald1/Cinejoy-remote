'use strict';
// Creates the standalone CineJoy Supabase project via the Management API.
// Token comes from the environment — never hardcoded, never printed.
//   set SUPABASE_ACCESS_TOKEN=<personal access token>
//   (create one at https://supabase.com/dashboard/account/tokens)
// Writes non-secret metadata to project.json, secrets to .local-secrets
// (both stay on this machine; only ref+url are needed by the apps).
const fs = require('fs');
const crypto = require('crypto');

const TOKEN = process.env.SUPABASE_ACCESS_TOKEN;
if (!TOKEN) {
  console.error('SUPABASE_ACCESS_TOKEN is not set — export it or set it in .env before running.');
  process.exit(1);
}
const H = { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' };
const API = 'https://api.supabase.com/v1';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  // Mirror the POS project's org + region.
  let r = await fetch(`${API}/projects/txoxhnzbawjkyzbhowjm`, { headers: H });
  if (!r.ok) throw new Error('lookup POS project: HTTP ' + r.status);
  const pos = await r.json();

  const dbPass = crypto.randomBytes(18).toString('base64url');
  r = await fetch(`${API}/projects`, {
    method: 'POST',
    headers: H,
    body: JSON.stringify({
      name: 'cinejoy-remote',
      organization_id: pos.organization_id,
      plan: 'free',
      region: pos.region,
      db_pass: dbPass,
    }),
  });
  const created = await r.json();
  if (!r.ok) throw new Error('create: HTTP ' + r.status + ' ' + JSON.stringify(created).slice(0, 300));
  const ref = created.ref || created.id;
  console.log('CREATED ref=' + ref);

  // Poll until healthy (creation takes ~2-5 min).
  for (let i = 0; i < 60; i++) {
    await sleep(15000);
    r = await fetch(`${API}/projects/${ref}`, { headers: H });
    const p = await r.json();
    console.log(`status ${i + 1}: ${p.status}`);
    if (p.status === 'ACTIVE_HEALTHY') break;
    if (p.status && p.status.includes('ERROR')) throw new Error('provisioning failed: ' + p.status);
  }

  r = await fetch(`${API}/projects/${ref}/api-keys`, { headers: H });
  const keys = await r.json();
  const pick = (n) => (Array.isArray(keys) ? keys.find((k) => k.name === n)?.api_key : keys[n]);
  const anon = pick('anon') || pick('ANON_KEY');
  const url = `https://${ref}.supabase.co`;
  fs.writeFileSync('supabase/project.json',
    JSON.stringify({ ref, url, region: pos.region }, null, 2) + '\n');
  fs.writeFileSync('supabase/.local-secrets',
    `SUPABASE_URL=${url}\nSUPABASE_ANON_KEY=${anon}\nDB_PASS=${dbPass}\n`);
  console.log('wrote supabase/project.json + supabase/.local-secrets');
})().catch((e) => { console.error('ERROR: ' + e.message); process.exit(1); });
