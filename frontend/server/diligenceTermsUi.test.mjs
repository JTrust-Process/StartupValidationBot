import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import ts from 'typescript';

test('diligence handoff maps exact security subtypes without guessing ambiguous instruments', async () => {
  const source = await readFile(new URL('../src/utils/diligenceHandoff.ts', import.meta.url), 'utf8');
  const javascript = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ES2022 } }).outputText;
  const { diligenceSecurityType } = await import(`data:text/javascript;base64,${Buffer.from(javascript).toString('base64')}`);
  assert.equal(diligenceSecurityType('SAFE'), 'SAFE');
  assert.equal(diligenceSecurityType('Convertible Note'), 'NOTE');
  assert.equal(diligenceSecurityType('Revenue Share'), 'REVENUE_SHARE');
  assert.equal(diligenceSecurityType('Preferred Stock'), 'EQUITY');
  assert.equal(diligenceSecurityType('SAFE and Convertible Note'), 'UNKNOWN');
  assert.equal(diligenceSecurityType(null), 'UNKNOWN');
});

test('packet shows term meaning, original excerpts and campaign verification independently', async () => {
  const page = await readFile(new URL('../src/pages/diligenceDetailPage.ts', import.meta.url), 'utf8');
  assert.match(page, /source\.semanticType/);
  assert.match(page, /source\.accession/);
  assert.match(page, /escapeHtml\(item\.rawExcerpt\)/);
  assert.match(page, /intermediary identity does not prove campaign availability/);
  assert.match(page, /Multiple filed terms/);
  assert.doesNotMatch(page, /createDeal\(/);
});
