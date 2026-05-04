# GitHub Workflows

This directory contains GitHub Actions workflows for CI/CD automation.

---

## Available Workflows

### 🔨 Maven CI (`workflows/maven.yml`)

Continuous integration testing for all pull requests.

**Trigger:** Automatically on all pull requests

**Purpose:** Ensure code quality and compatibility across different environments

**What it does:**
- ✅ Builds the project with Maven (`mvn package`)
- ✅ Runs all unit and integration tests
- ✅ Tests on multiple platforms (Ubuntu)
- ✅ Tests with different Maven and Java versions
- ✅ Uploads test reports as artifacts (retained for 5 days)

**Test Matrix:**

| Component | Values |
|-----------|--------|
| **OS** | Ubuntu Latest |
| **Java** | 17 (Eclipse Temurin) |
| **Maven** | Multiple versions (compatibility testing) |
| **Build Tool** | Maven |

**Artifacts:**

Test reports are uploaded and available for 5 days:
- Surefire reports (unit tests)
- Failsafe reports (integration tests)

Access them from the workflow run's "Artifacts" section.

---

### 🔄 Automatic Rebase (`workflows/rebase.yml`)

Automatically rebase PR branches onto the target base branch via comment command.

**Trigger:** `/rebase` command in PR comments

**Purpose:** Keep PR branches up-to-date with the base branch without manual intervention

#### How to Use

Comment on any pull request:

```
/rebase
```

The bot will automatically rebase your PR branch onto the target branch (usually `main`).

#### Workflow Steps

1. **👀 Eyes reaction** — Bot acknowledges the `/rebase` command
2. **🔍 Fetch branches** — Downloads the latest base branch and PR branch
3. **📝 Get PR details** — Retrieves branch names and commit SHAs
4. **⚙️ Configure git** — Sets up git identity as `github-actions[bot]`
5. **🔀 Rebase** — Executes `git rebase origin/<base_ref>`
6. **📤 Force push** — Pushes rebased branch with `--force-with-lease`
7. **✅ Success** — 🚀 reaction + success comment, **OR**
8. **❌ Failure** — 😕 reaction + manual rebase instructions

#### Success Example

```
✅ Successfully rebased onto `main`
```

Your PR is now up-to-date! 🎉

#### Conflict Handling

If conflicts are detected:

**What the workflow does:**
- Aborts the rebase (`git rebase --abort`)
- Adds a 😕 reaction to your comment
- Posts detailed manual rebase instructions

**Sample conflict message:**

```
❌ Rebase failed due to merge conflicts. Please rebase manually:

git fetch origin
git rebase origin/main
# Resolve conflicts
git rebase --continue
git push --force-with-lease
```

**How to resolve conflicts:**

1. Follow the instructions in the comment
2. Resolve conflicts in your local repository
3. Force-push the rebased branch
4. The PR will update automatically

#### Permissions

**Required permissions (automatically granted):**
- `contents: write` — Push rebased branch
- `pull-requests: write` — Add reactions and comments

**Fork PRs:**
- Must enable **"Allow edits from maintainers"** when creating the PR
- Without this, the workflow cannot push to your fork

**Branch PRs:**
- Work automatically with no additional setup

#### Security

✅ **Safe by design:**
- Uses GitHub's `GITHUB_TOKEN` (scoped to the repository)
- Only triggers on PRs (not on direct pushes)
- Respects all branch protection rules
- Uses `--force-with-lease` instead of `--force` (prevents overwriting concurrent changes)
- Cannot be triggered on protected branches without permission

❌ **What it cannot do:**
- Bypass required reviews
- Skip status checks
- Modify protected branches without approval
- Access secrets from forks (security boundary maintained)

#### Limitations

- ⚠️ Works only on **pull requests** (not issues or commits)
- ⚠️ Requires `/rebase` on its own line (case-sensitive)
- ⚠️ Fork PRs need "Allow edits from maintainers" enabled
- ⚠️ Cannot resolve conflicts automatically (manual intervention required)

---

### 🔁 Rerun Tests (`workflows/retest.yml`)

Automatically restart CI tests for a PR via comment command.

**Trigger:** `/retest` command in PR comments

**Purpose:** Restart flaky or failed tests without pushing new commits

#### How to Use

Comment on any pull request:

```
/retest
```

The bot will find the latest Maven CI workflow run for your PR and restart it.

#### Workflow Steps

1. **👀 Eyes reaction** — Bot acknowledges the `/retest` command
2. **🔍 Find workflow run** — Locates the latest Maven CI run for this PR's HEAD SHA
3. **🔄 Restart workflow** — Triggers `gh run rerun` to restart the tests
4. **✅ Success** — 🚀 reaction + success comment with link to workflow run, **OR**
5. **❌ Failure** — 😕 reaction + explanation (no run found or restart failed)

#### Success Example

```
✅ Tests restarted — check the Actions tab
```

Click the link to see the restarted workflow run.

#### When to Use

- **Flaky test failure** — Test failed due to timing or resource issues
- **Infrastructure failure** — CI encountered temporary network or service issues
- **Post-discussion verification** — Verify tests still pass after review comments

**Do NOT use if:**
- You made code changes — push a commit instead to trigger fresh tests
- Tests are consistently failing — fix the issue first

#### Failure Cases

**No workflow run found:**

```
❌ No workflow run found for this PR. Tests may not have run yet — push a commit to trigger them.
```

This happens when:
- The PR has no CI runs yet (first push hasn't triggered tests)
- All previous workflow runs were deleted

**Solution:** Push a commit to trigger tests.

**Restart failed:**

```
❌ Failed to restart tests. The workflow run may have already been rerun or deleted.
```

This happens when:
- The workflow run was already manually rerun
- The workflow run was deleted
- GitHub API rate limit exceeded (rare)

**Solution:** Check the Actions tab manually or wait a few minutes and try again.

#### Permissions

**Required permissions (automatically granted):**
- `actions: write` — Restart workflow runs
- `pull-requests: write` — Add reactions and comments

#### Security

✅ **Safe by design:**
- Uses GitHub's `GITHUB_TOKEN` (scoped to the repository)
- Only triggers on PRs (not issues or commits)
- Cannot modify code or bypass checks
- Only restarts existing workflow runs (doesn't create new ones with different code)

❌ **What it cannot do:**
- Run tests for code that hasn't been pushed
- Bypass required status checks
- Modify workflow files or test configuration
- Access secrets from forks (security boundary maintained)

#### Limitations

- ⚠️ Works only on **pull requests** (not issues or commits)
- ⚠️ Requires `/retest` on its own line (case-sensitive)
- ⚠️ Only restarts the **latest** workflow run (not older runs)
- ⚠️ Requires at least one previous CI run to exist

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for:
- Detailed contribution guidelines
- Development workflow
- Code quality standards
- Testing requirements
- Commit message conventions

---

## Troubleshooting

### ❓ Rebase fails with "Permission denied"

**Symptom:**
```
remote: Permission to user/repo.git denied to github-actions[bot].
```

**Root Cause:**
Fork PRs require write access to the contributor's fork, which is only granted when "Allow edits from maintainers" is enabled.

**Solution:**

**Option 1: Enable maintainer edits (recommended)**
1. Go to your PR on GitHub
2. Click "Edit" near the PR title
3. Check ✅ **"Allow edits from maintainers"**
4. Save changes
5. Comment `/rebase` again

**Option 2: Manual rebase**
```bash
git fetch upstream
git rebase upstream/main
git push --force-with-lease
```

---

### ❓ Workflow doesn't trigger

**Symptom:**
You commented `/rebase` but nothing happened.

**Possible Causes:**

1. **Not a pull request** — The workflow only runs on PRs, not issues
2. **Typo in command** — Must be exactly `/rebase` (case-sensitive)
3. **Workflow disabled** — Check the "Actions" tab to see if workflows are enabled
4. **No permission to comment** — You must have read access to the repository

**Solution:**

✅ **Check your comment:**
```
/rebase
```

✅ **Verify it's a PR:**
- Look for "Pull request" label at the top
- URL should be `/pull/123`, not `/issues/123`

✅ **Check workflow runs:**
1. Go to the "Actions" tab
2. Filter by "Automatic Rebase"
3. Check if a run was created

---

### ❓ Tests fail in CI but pass locally

**Symptom:**
`mvn test` passes on your machine but fails in GitHub Actions.

**Common Causes:**

| Issue | Explanation | Solution |
|-------|-------------|----------|
| **Environment differences** | CI uses Ubuntu/Java 17, you might use macOS/Java 21 | Test locally with same Java version |
| **Test isolation** | Tests pass individually but fail when run together | Run full suite: `mvn clean test` |
| **Race conditions** | Timing-dependent tests fail intermittently | Review test logs, add proper synchronization |
| **Resource limits** | CI has memory/CPU constraints | Check for resource-heavy operations |
| **Hardcoded paths** | Absolute paths that work locally but not in CI | Use relative paths or `System.getProperty("java.io.tmpdir")` |

**Debugging Steps:**

1. **Download test reports:**
   - Go to the failed workflow run
   - Scroll to "Artifacts"
   - Download `surefire-reports-ubuntu-latest`
   - Extract and review failure details

2. **Check logs:**
   ```
   Actions → Failed Run → Build and test → View logs
   ```

3. **Reproduce locally:**
   ```bash
   # Use same Java version as CI
   java -version  # Should be 17

   # Clean build
   ./mvnw clean test

   # Test specific profile
   ./mvnw test -Ppersistence-memory
   ```

4. **Fix and repush:**
   ```bash
   # Fix the issue
   git add .
   git commit -m "fix: resolve CI test failure"
   git push
   ```

---

### ❓ How to skip CI for documentation changes

**Symptom:**
You only changed documentation but CI runs anyway.

**Solution:**

Add `[skip ci]` or `[ci skip]` to your commit message:

```bash
git commit -m "docs: update contributing guide [skip ci]"
```

**Note:** Use sparingly. Even documentation PRs should generally pass CI to ensure examples compile.

---

### ❓ Rebase creates duplicate commits

**Symptom:**
After `/rebase`, the PR shows duplicate commits or merge commits.

**Root Cause:**
You merged `main` into your branch instead of rebasing, then rebased afterward.

**Solution:**

**Prevention:**
- Always rebase, never merge: `git rebase origin/main`
- Use `/rebase` command instead of manual merging

**Recovery:**
```bash
# Reset to remote state
git fetch origin
git reset --hard origin/your-branch-name

# Rebase cleanly
git rebase origin/main

# Force push
git push --force-with-lease
```

---

## Workflow File Reference

### Directory Structure

```
.github/
├── CONTRIBUTING.md          # Contribution guidelines
├── README.md                # This file
└── workflows/
    ├── maven.yml           # Maven CI workflow
    └── rebase.yml          # Automatic rebase workflow
```

### Workflow Status Badges

Add to your PR description to show CI status:

```markdown
![CI](https://github.com/user/repo/actions/workflows/maven.yml/badge.svg)
```

### Local Workflow Testing

**Test workflow syntax:**
```bash
# Install act (GitHub Actions local runner)
brew install act  # macOS
# or download from https://github.com/nektos/act

# Run workflows locally
act pull_request  # Runs maven.yml
```

**Note:** `rebase.yml` requires GitHub context and cannot be fully tested locally.

---

## Questions?

- 📖 **Documentation:** See [../README.md](../README.md)
- 🐛 **Bug Reports:** Open an issue
- 💬 **Discussions:** Use GitHub Discussions
- 🤝 **Contributing:** See [CONTRIBUTING.md](CONTRIBUTING.md)
