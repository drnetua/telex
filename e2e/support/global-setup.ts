import { randomInt, randomUUID } from "node:crypto";
import { mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

/**
 * Once per run, before any worker starts: a directory where `testNumber` claims Telegram test numbers, shared by every
 * worker, project and retry of this run, and a random start so reruns against a long-lived local stack begin elsewhere.
 * Workers inherit these variables.
 */
export default function globalSetup(): void {
  const dir = join(tmpdir(), `telex-e2e-numbers-${randomUUID()}`);
  mkdirSync(dir, { recursive: true });
  process.env.TELEX_E2E_NUMBER_CLAIMS = dir;
  process.env.TELEX_E2E_NUMBER_OFFSET = String(randomInt(1_000_000));
}
