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

# Wave 8 (CJ commerce completeness + Trovemo branding) active worktrees.
# Parked this wave — re-add to launch them: "platform" (Wave-7 deployed),
# "promotion" (no assignment). Each name must have a matching
# '### Worktree: `<short>`' block in CLAUDE.md.
MODULES=(
  "order"             # CJ fulfilment + payment path walked end to end, gaps fixed
  "goods-management"  # CJ product reviews: fill the empty PDP reviews section
  "gateway-api"       # Trovemo wordmark + favicon on the storefront
  "gateway-admin"     # Trovemo wordmark + favicon on the admin console
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
  inner_cmd="claude $(printf %q "$prompt"); exec bash"
  gnome-terminal --window \
    --working-directory="$wt_path" \
    --title="claude/$short" \
    -- bash -c "$inner_cmd"
done
