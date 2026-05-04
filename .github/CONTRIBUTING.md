# Contributing to Case Hub

Thank you for your interest in contributing to Case Hub! This guide will help you get started.

## Table of Contents

- [Pull Request Commands](#pull-request-commands)
- [Development Workflow](#development-workflow)
- [Code Quality Standards](#code-quality-standards)
- [Testing](#testing)

---

## Pull Request Commands

### Automatic Rebase

Trigger an automatic rebase of your PR branch onto the base branch by commenting:

```
/rebase
```

**How it works:**

1. Comment `/rebase` on any pull request
2. The GitHub Action will automatically:
   - Fetch the latest base branch
   - Rebase your PR branch onto it
   - Force-push the rebased branch
3. You'll receive a reaction and comment with the result:
   - ✅ 🚀 **Success** — PR rebased successfully
   - ❌ 😕 **Conflict** — Manual rebase required

**Example:**

```
/rebase
```

The bot will react with 👀 to acknowledge your command, then perform the rebase.

---

### Rerun Tests

Trigger a rerun of CI tests for your PR by commenting:

```
/retest
```

**How it works:**

1. Comment `/retest` on any pull request
2. The GitHub Action will automatically:
   - Find the latest Maven CI workflow run for your PR
   - Restart the workflow
3. You'll receive a reaction and comment with the result:
   - ✅ 🚀 **Success** — Tests restarted
   - ❌ 😕 **Failure** — No workflow run found or restart failed

**When to use:**

- After fixing a flaky test failure
- When CI encountered a temporary infrastructure issue
- To verify tests still pass after discussion or review

**Example:**

```
/retest
```

The bot will react with 👀 to acknowledge your command, then restart the tests.

---

### Manual Rebase (when conflicts occur)

If the automatic rebase fails due to conflicts, you'll need to rebase manually:

```bash
# Fetch latest changes
git fetch origin

# Checkout your branch
git checkout your-branch-name

# Rebase onto main (or target branch)
git rebase origin/main

# Resolve conflicts
# Edit conflicting files, then:
git add .
git rebase --continue

# Repeat until rebase completes

# Force push with lease (safer than --force)
git push --force-with-lease
```

**Aborting a rebase:**

```bash
git rebase --abort
```

---

## Development Workflow

### Setting Up

1. **Fork the repository** on GitHub
2. **Clone your fork:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/casehub-engine.git
   cd casehub-engine
   ```
3. **Add upstream remote:**
   ```bash
   git remote add upstream https://github.com/ORIGINAL_OWNER/casehub-engine.git
   ```
4. **Install dependencies:**
   ```bash
   ./mvnw clean install
   ```

### Making Changes

1. **Create a feature branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```
2. **Make your changes**
3. **Run tests:**
   ```bash
   ./mvnw test
   ```
4. **Format code:**
   ```bash
   ./mvnw spotless:apply
   ```
5. **Commit your changes:**
   ```bash
   git add .
   git commit -m "feat: add your feature description"
   ```

### Submitting a Pull Request

1. **Push to your fork:**
   ```bash
   git push origin feature/your-feature-name
   ```
2. **Create a Pull Request** on GitHub
3. **Enable "Allow edits from maintainers"** (required for `/rebase` to work on forks)
4. **Wait for CI checks** to complete
5. **Address review feedback** if any

---

## Code Quality Standards

All code contributions must meet the following standards:

### Code Formatting

- **Spotless** enforces Google Java Format
- Run before committing:
  ```bash
  ./mvnw spotless:apply
  ```
- Check formatting:
  ```bash
  ./mvnw spotless:check
  ```

### Checkstyle

- **No wildcard imports** (`import java.util.*` ❌)
- **No `@author` tags** in JavaDoc
- **No redundant imports**

Violations will fail the build. Check locally:
```bash
./mvnw checkstyle:check
```

### Code Style

- Use meaningful variable names
- Keep methods focused and small
- Add JavaDoc for public APIs
- Write descriptive commit messages

---

## Testing

### Running Tests

**All tests:**
```bash
./mvnw test
```

**Specific module:**
```bash
./mvnw test -pl engine
```

**Specific test:**
```bash
./mvnw test -Dtest=WorkerRetryExtendedTest
```

**With persistence profile:**
```bash
# In-memory persistence (faster, no Docker)
./mvnw test -pl engine -Ppersistence-memory

# Hibernate persistence (requires PostgreSQL)
./mvnw test -pl engine -Ppersistence-hibernate
```

### Writing Tests

- Use descriptive test method names
- Follow AAA pattern (Arrange, Act, Assert)
- Use `@QuarkusTest` for integration tests
- Mock external dependencies
- Test both happy path and edge cases

---

## CI/CD Workflows

### Maven CI (`maven.yml`)

**Triggers:** All pull requests

**What it does:**
- Builds the project with Maven
- Runs all unit and integration tests
- Uploads test reports as artifacts (retained for 5 days)
- Tests on multiple OS/Java/Maven versions

**Test matrix:**
- OS: Ubuntu
- Java: 17 (Temurin)
- Maven: Multiple versions

### Automatic Rebase (`rebase.yml`)

**Triggers:** `/rebase` command in PR comments

**What it does:**
- Rebases PR branch onto base branch
- Handles conflicts gracefully
- Provides feedback via reactions and comments

**Permissions required:**
- Fork PRs: "Allow edits from maintainers" must be enabled
- Branch PRs: Automatic

### Rerun Tests (`retest.yml`)

**Triggers:** `/retest` command in PR comments

**What it does:**
- Finds the latest Maven CI workflow run for the PR
- Restarts the workflow to rerun all tests
- Provides feedback via reactions and comments

**When to use:**
- Flaky test failures
- Temporary CI infrastructure issues
- Post-review verification without code changes

---

## Commit Message Guidelines

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Code style changes (formatting, whitespace)
- `refactor`: Code refactoring (no functional changes)
- `test`: Adding or updating tests
- `chore`: Build process, dependencies, tooling

**Examples:**

```
feat(engine): add worker retry mechanism with exponential backoff

fix(persistence): resolve race condition in InMemoryEventLogRepository

docs(readme): update quick start guide with YAML examples

test(engine): add flakiness test for WorkerRetryExtendedTest
```

---

## Getting Help

- **Issues:** Use GitHub Issues for bug reports and feature requests
- **Discussions:** Use GitHub Discussions for questions
- **Documentation:** See [README.md](../README.md) and [.github/README.md](README.md)

---

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
