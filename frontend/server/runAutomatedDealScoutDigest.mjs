#!/usr/bin/env node
import './env/loadServerEnv.mjs';
import { runAutomatedDealScoutDigest } from './dealScoutAutomation.mjs';

const send = process.argv.includes('--send');
const result = await runAutomatedDealScoutDigest({ send });

console.log(JSON.stringify(result, null, 2));
process.exitCode = result.ok ? 0 : 1;
