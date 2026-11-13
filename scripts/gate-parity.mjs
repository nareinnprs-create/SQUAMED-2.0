#!/usr/bin/env node
// Gate parity check — BUILD-PROTOCOL §7.2. Fails the build when the gate registry
// (.gate/GATES.md) and the CI workflow (.github/workflows/ci.yml) disagree.
// Dependency-free; safe to run locally: `node scripts/gate-parity.mjs`.
import { readFileSync } from "node:fs";

const root = new URL("..", import.meta.url);
const read = (p) => readFileSync(new URL(p, root), "utf8");

const ci = read(".github/workflows/ci.yml");
const registry = read(".gate/GATES.md");

// Top-level CI job ids: keys indented exactly two spaces inside the `jobs:` block.
const jobs = new Set();
let inJobs = false;
for (const line of ci.split(/\r?\n/)) {
  if (!inJobs) {
    if (/^jobs:\s*(#.*)?$/.test(line)) inJobs = true;
    continue;
  }
  if (/^\S/.test(line)) break; // left the jobs block
  const m = line.match(/^  ([A-Za-z0-9_-]+):/);
  if (m) jobs.add(m[1]);
}

// Gate rows look like: `| 1 | Name | pass condition | job-a, job-b |`
const gateJobs = new Map(); // gate number -> [job id, ...]
const gateNames = new Map();
const problems = [];
for (const line of registry.split(/\r?\n/)) {
  if (!/^\|\s*\d+\s*\|/.test(line)) continue;
  let cells = line.split("|").map((c) => c.trim());
  if (cells[0] === "") cells = cells.slice(1);
  if (cells[cells.length - 1] === "") cells = cells.slice(0, -1);
  if (cells.length !== 4) {
    problems.push(`malformed gate row (expected 4 columns): ${line}`);
    continue;
  }
  const [num, name, , jobCell] = cells;
  gateNames.set(num, name);
  const ids = jobCell.split(",").map((s) => s.trim()).filter(Boolean);
  if (ids.length === 0) problems.push(`gate ${num} (${name}): CI job column is empty`);
  for (const id of ids) {
    if (!/^[a-z0-9_-]+$/.test(id)) {
      problems.push(`gate ${num} (${name}): CI job column must list job ids only, got "${id}"`);
    } else if (!jobs.has(id)) {
      problems.push(`gate ${num} (${name}): CI job "${id}" not found in ci.yml`);
    }
  }
  gateJobs.set(num, ids);
}

for (let i = 1; i <= 12; i++) {
  if (!gateJobs.has(String(i))) problems.push(`gate ${i} is missing from the GATES.md table`);
}

const mapped = new Set([...gateJobs.values()].flat());
// Enabler jobs that run no gate themselves (detect feeds other jobs' `if:`).
const NON_GATE_JOBS = new Set(["detect"]);
for (const id of jobs) {
  if (!mapped.has(id) && !NON_GATE_JOBS.has(id)) problems.push(`CI job "${id}" is not mapped to any gate row in GATES.md`);
}

if (problems.length > 0) {
  console.error("GATE PARITY FAILURE — GATES.md and ci.yml disagree:");
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(1);
}
console.log(`gate parity OK: ${gateJobs.size} gates <-> ${jobs.size} CI jobs, all mapped`);
