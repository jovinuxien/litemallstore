#!/usr/bin/env bash
set -euo pipefail

# Open one git worktree per module in its own gnome-terminal window.
# Each worktree is on a new branch off master (created if missing) and lives
# at ../litemall-wt/<short>, matching the repo's existing convention.
#
# Re-running is safe: existing worktrees and branches are reused.

REPO_ROOT="$(git -C "$(dirname "$(readlink -f "$0")")" rev-parse --show-toplevel)"
WT_BASE="$(readlink -f "$REPO_ROOT/..")/litemall-wt"
BASE_BRANCH="master"

# Wave 13 (SEO foundation) active worktrees. Launch with FRESH=1 —
# goods-management/gateway-admin Wave 12 is shipped, and a plain resume
# would reopen the finished previous-wave conversations instead of
# delivering Wave 13. "promotion" is still IN FLIGHT on Wave 12 in its
# own open terminal — do NOT relaunch it here (FRESH=1 would bury its
# live session). Parked: "order", "gateway-admin", "platform". Each name
# must have a matching '### Worktree: `<short>`' block in CLAUDE.md.
MODULES=(
  "goods-management"  # /srv/goods/meta/{id} + nightly-regenerated /srv/goods/sitemap.xml
  "gateway-api"       # head injection (title/OG/JSON-LD) at the SPA fallback, slugged URLs, robots.txt
)

mkdir -p "$WT_BASE"

for short in "${MODULES[@]}"; do
  branch="fix/$short"
  wt_path="$WT_BASE/$short"

  if [[ -d "$wt_path" ]]; then
    echo "[$short] worktree exists at $wt_path — reusing"
  elif git -C "$REPO_ROOT" show-ref --verify --quiet "refs/heads/$branch"; then
    echo "[$short] branch $branch exists — attaching worktree at $wt_path"
    git -C "$REPO_ROOT" worktree add "$wt_path" "$branch"
  else
    echo "[$short] creating branch $branch off $BASE_BRANCH at $wt_path"
    git -C "$REPO_ROOT" worktree add -b "$branch" "$wt_path" "$BASE_BRANCH"
  fi
done

for short in "${MODULES[@]}"; do
  wt_path="$WT_BASE/$short"
  prompt="You are the '$short' worktree (branch fix/$short, working dir $wt_path). Read CLAUDE.md and locate the block '### Worktree: \`$short\`' under '## Per-worktree tasks'. Treat the Task and Acceptance lines in that block as your sole assignment. Per the repo's locked process rule, produce a written plan and get my approval before editing code."
  # Resume an interrupted session when one exists for this worktree (its
  # project dir is the worktree path with [/_.] flattened to '-'), otherwise
  # start fresh with the assignment prompt. The trailing `exec bash` keeps the
  # window open either way so a startup error is readable, not a vanish.
  # FRESH=1 skips the resume — use it at the start of a NEW wave, otherwise
  # --continue reopens the finished previous-wave conversation instead of
  # delivering the new assignment.
  proj_dir="$HOME/.claude/projects/$(echo "$wt_path" | tr '/_.' '---')"
  if [[ "${FRESH:-0}" != "1" ]] && ls "$proj_dir"/*.jsonl >/dev/null 2>&1; then
    echo "[$short] previous session found — resuming it"
    inner_cmd="claude --continue || claude $(printf %q "$prompt"); exec bash"
  else
    inner_cmd="claude $(printf %q "$prompt"); exec bash"
  fi
  gnome-terminal --window \
    --working-directory="$wt_path" \
    --title="claude/$short" \
    -- bash -c "$inner_cmd"
done
