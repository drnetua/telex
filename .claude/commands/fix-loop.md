---
description: Review → fix → re-review an SDD feature until a review comes back clean. Uses /sdd:review and /sdd:implement, with guardrails so no step can hang or silently break the build.
argument-hint: <feature-slug> [max-passes, default 5]
---

Run the review/fix loop for the SDD feature `$1` until a review is clean. Stop after `${2:-5}` review passes, even if findings remain.

## One pass

1. **Gate.** Run `./gradlew build integrationTest` in the background (see Guardrails). Start a review only on green. Run it again after every fix batch.
2. **Review** (`/sdd:review $1`). Dispatch two `sdd:reviewer` agents in parallel:
   - **(a) fix range:** checks the previous record's "Fix now" findings with mutation checks, then runs stage 2 on the fix range;
   - **(b) stage 1 at HEAD:** read-only from committed content (`git show HEAD:`), plus a stage-2 sweep. Its ids are prefixed `H-`.

   Paste the mutation rules below into (a)'s prompt. Tell (b) not to run Gradle or pnpm.
3. **Resolve.**
   - Merge duplicate findings.
   - Check any code-defect claim against the code yourself.
   - Ask the user (one `AskUserQuestion`, at most 4 questions) only when the verdict is a product or scope trade-off. Resolve test-only and docs-only "Fix now" findings without asking, and say so.
   - Write `docs/features/$1/_review/review-<date>[-rN].md`, then commit it.
4. **Plan.**
   - Add follow-up tasks (next T-ids) to `tasks.json`, `tasks/<id>-<slug>.md` and `tasks/tracker.md` in the existing format, plus one docs-sync task.
   - Commit them.
5. **Implement** (`/sdd:implement $1`).
   - **2 or more independent lanes:** run a Workflow with `isolation: 'worktree'`.
     - Its prompt must say: `git reset --hard <feature-branch>` if the worktree isn't on it.
     - Don't edit tracker, test-plan or sad.
     - Never run `pnpm install` except at the worktree's own root.
     - Then cherry-pick the lanes' commits onto the feature branch.
   - **1 small task:** do it inline, red first, using mutation checks.
6. **Docs sync:** test-plan, sad, screens, the tracker and task statuses. Verify with a script that `tasks.json`, the task files and the tracker agree. Commit.
7. Back to 1. **Stop** when both reviewers report no new findings (REVIEW_CLEAN). Then write a PASS record and hand off to `/sdd:ship $1`.

## Guardrails (these exist because earlier loops hung or broke)

- **No command may wait on stdin.**
  - Never put `cat`, `read`, an empty heredoc or an interactive tool in a script.
  - Give every Gradle/pnpm/git call `</dev/null`.
  - Use `GIT_EDITOR=true` for any git command that could open an editor.
- **Every long command gets a bound.**
  - Foreground: set `timeout` (max 600000).
  - Longer jobs (the full build is ~19 min): `run_in_background`, then wait for the completion notification. Never poll with sleep.
- **Never assume a background job finished.**
  - If no completion notification arrives within the job's expected time ×2, check its output file and `pgrep -fl gradle`.
  - If it is stuck, kill it, restore any file it mutated, and say so.
- **Mutation scripts:**
  - One mutation per command.
  - Copy the file to `<file>.orig` first, and restore with `cp <file>.orig <file>`. For tracked files, `git checkout -- <file>` also works.
  - After the batch, check `git status --short` shows only the intended changes.
  - Never chain a whole mutation sequence in one long pipeline. A failure midway leaves the tree mutated.
- **pnpm links:**
  - Reviewers mutate in the main tree, one file at a time. They never use a worktree or copy, never symlink `node_modules`, and never run `pnpm install`.
  - Before every full build, run `find frontend/node_modules e2e/node_modules -maxdepth 3 -type l \( -lname '*private/tmp*' -o -lname '*worktrees*' \)`.
  - If that prints anything: `rm -rf frontend/node_modules e2e/node_modules && pnpm install --frozen-lockfile </dev/null`.
- **Build collisions:** don't start the full build while a reviewer agent is still running. They share the Gradle build dir, Docker and pnpm links.
- **Failures:**
  - A red gate that isn't caused by the code (MODULE_NOT_FOUND, ZipException, Docker) is an environment problem. Fix it and rerun once. Don't loop on it.
  - A red that survives one fix attempt: stop the loop and report it.
- **Never commit with a red or skipped gate.** Never weaken a test to pass.
- **Never push, or ask the user to push, without a green local `./gradlew build integrationTest` on the exact commit.** Quote the BUILD SUCCESSFUL line, and say when Playwright e2e couldn't run locally.
- **Check in after each pass** with one short status line: findings, verdicts, commits.
