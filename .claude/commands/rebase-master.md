---
description: Fetch, rebase the current branch onto the latest origin/master, resolve conflicts, and report each decision. Never commits or pushes.
allowed-tools: Bash(git fetch:*), Bash(git status:*), Bash(git rebase:*), Bash(git diff:*), Bash(git log:*), Bash(git show:*), Bash(git checkout --ours:*), Bash(git checkout --theirs:*), Bash(git add:*), Bash(git rm:*), Bash(git rev-parse:*), Bash(git merge-base:*), Bash(git ls-files:*), Bash(git branch:*), Read, Edit, Grep, Glob
---

Rebase the current branch onto the latest `origin/master`.

## Hard rules

- **No commit and no push.** Never run `git commit`, `git push`, `git merge`, `git reset --hard`, `git stash drop`, or anything that rewrites another branch. The only history change allowed is the rebase itself. Move it forward with `GIT_EDITOR=true git rebase --continue`, which keeps each replayed commit's own message, so you never write a commit yourself.
- Never use `--force`, `--no-verify` or `-X ours/theirs` on the whole rebase. Decide conflicts file by file.

## Steps

1. **Preflight.**
   - Run `git rev-parse --abbrev-ref HEAD`. If the result is `master` or `HEAD` (detached), stop and say so.
   - If a rebase, merge or cherry-pick is already in progress (`git status`), stop and report it. Don't touch it.
   - If the working tree has staged or unstaged changes to tracked files, stop. List the files and ask the user to commit or stash them. Untracked files are fine.
2. **Fetch:** `git fetch --prune origin`.
3. **Report the state before the rebase:**
   - current branch;
   - `origin/master` SHA;
   - merge-base;
   - commits ahead: `git log --oneline origin/master..HEAD`;
   - commits behind: `git log --oneline HEAD..origin/master | wc -l`.

   If the branch is already on top of `origin/master` (merge-base == `origin/master`), say so and stop.
4. **Rebase:** `git rebase origin/master`.
5. **On each conflict stop**, repeat until the rebase finishes:
   - List the conflicted files: `git diff --name-only --diff-filter=U`.
   - Find the commit being replayed: `git log -1 --format='%h %s' REBASE_HEAD`.
   - Remember the rebase sides:
     - `--ours` / `HEAD` = upstream (`origin/master` plus the commits already replayed);
     - `--theirs` / `REBASE_HEAD` = the branch commit being replayed.
   - For each file, read both sides: the conflict hunks, plus `git show REBASE_HEAD -- <file>` to see what the branch commit intended. Decide:
     - **`graphify-out/**`:** always take upstream (`git checkout --ours -- <file>`). Graph files can't be merged by hand, and CI regenerates them on master.
     - **Generated or lock files** (`pnpm-lock.yaml`, build output): take upstream, then note that the user should re-run the generator (`pnpm install`) afterwards. Don't run it yourself, because that would leave changes the rebase didn't make.
     - **Code and docs:** merge by hand so both sides' intent survives (master's change plus the branch's change). If one side clearly supersedes the other (for example, master deleted or renamed what the branch edited), follow the surviving structure and carry the branch's intent into it.
     - **Delete/modify conflicts:** keep the side whose intent still applies, with `git rm` or `git add`, and explain why.
   - Make sure no conflict markers remain: `git diff --check` and grep for `^<<<<<<<|^>>>>>>>`. Then `git add <file>`.
   - Continue with `GIT_EDITOR=true git rebase --continue`. If a commit becomes empty, use `git rebase --skip` and record that.
   - If a conflict can't be resolved with confidence (both sides change the same logic in incompatible ways), **don't guess**. Run `git rebase --abort`, so the branch is exactly as it was, and report the conflict and the options instead.
6. **Verify:**
   - `git status` shows a clean tree and no rebase in progress.
   - `git log --oneline origin/master..HEAD` shows the same number of branch commits, minus any skipped as empty.
   - Don't run builds or tests unless the user asks. Suggest them when code conflicts were resolved by hand.

## Report

End with:

- **Branch** `<name>`: rebased from `<old merge-base short SHA>` onto `origin/master` `<short SHA>`. Commits replayed: N. Commits skipped as empty: list them.
- **Conflicts:** a table with one row per conflicted file per commit. Write "none" if there were no conflicts.

  | Commit | File | What clashed | Decision | Why |
  |---|---|---|---|---|

- **Follow-ups:** for example, re-run `pnpm install`, re-run tests in the areas merged by hand, or `git push --force-with-lease` **when you are ready**. The command never pushes.
- If you aborted: why, the conflicting hunks in short, and the options.
