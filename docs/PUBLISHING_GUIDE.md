# Publishing Maven Artifacts: A Complete Guide

This guide covers how to publish your Maven project (`uni-secret-manager-spring-boot-starter`) to package registries using GitHub.

## Table of Contents

1. [Publishing Options Overview](#publishing-options-overview)
2. [Pre-Publication Checklist](#pre-publication-checklist)
3. [Option A: JitPack](#option-a-jitpack-recommended-for-team-use)
4. [Option B: GitHub Packages](#option-b-github-packages)
5. [Option C: Maven Central](#option-c-maven-central-recommended-for-public-libraries)
6. [Maven Profiles](#maven-profiles)
7. [GPG Signing Setup](#gpg-signing-setup)
8. [GitHub Actions CI/CD](#github-actions-cicd)
9. [Version Management](#version-management)
10. [Troubleshooting](#troubleshooting)

---

## Publishing Options Overview

| Feature | JitPack | GitHub Packages | Maven Central |
|---------|---------|-----------------|---------------|
| **Ease of Setup** | Minimal | Easy | Moderate to Complex |
| **Consumer Authentication** | **Not required** | **Required** (GitHub token) | **Not required** |
| **Publisher Setup** | None (builds from repo) | GitHub Actions | Sonatype + GPG signing |
| **Cost** | Free for public repos | Free for public repos | Free |
| **Discovery** | jitpack.io | Limited | High (search.maven.org) |
| **Best For** | Team/internal use | Organization-scoped | Public open-source |
| **GroupId** | `com.github.<user>` | Your own (e.g., `io.github.*`) | Your own (verified) |

> **Consumer Experience Comparison**
>
> - **JitPack**: Consumer adds a repository + dependency to `pom.xml`. No accounts or tokens needed. Builds directly from your GitHub repo on first request.
> - **GitHub Packages**: Consumer must have a GitHub account, create a PAT with `read:packages` scope, and configure `~/.m2/settings.xml`.
> - **Maven Central**: Consumer just adds the dependency — no extra repository or auth. The standard for public libraries.

**Recommendation**: Use **JitPack** for team/internal distribution (zero friction). Use **Maven Central** when publishing for broad public adoption. Keep **GitHub Packages** as an additional channel if your organization already uses it.

### How Publishing Targets Are Managed

This project uses **Maven profiles** to support multiple publishing targets from a single `pom.xml`.
See the [Maven Profiles](#maven-profiles) section for details.

---

## Pre-Publication Checklist

Before publishing, ensure your project meets these requirements:

### 1. License Selection

You **must** include a license. Common choices for open-source:

| License | Description |
|---------|-------------|
| Apache 2.0 | Permissive, allows commercial use, requires attribution |
| MIT | Very permissive, minimal restrictions |
| GPL 3.0 | Copyleft, derivatives must also be GPL |

**Action**: Create a `LICENSE` file in your project root.

```bash
# Example: Add Apache 2.0 license
curl -o LICENSE https://www.apache.org/licenses/LICENSE-2.0.txt
```

### 2. Required POM Metadata

Maven Central (and good practice) requires these elements in your `pom.xml`:

```xml
<project>
    <!-- GAV Coordinates (you already have these) -->
    <groupId>io.github.timhwang777</groupId>
    <artifactId>uni-secret-manager-spring-boot-starter</artifactId>
    <version>1.0.0</version>

    <!-- Required metadata -->
    <name>Universal Secret Manager Spring Boot Starter</name>
    <description>Spring Boot starter for universal secret management across AWS and GCP</description>
    <url>https://github.com/timhwang777/uni-secret-manager-spring</url>

    <!-- License (REQUIRED) -->
    <licenses>
        <license>
            <name>Apache License, Version 2.0</name>
            <url>https://www.apache.org/licenses/LICENSE-2.0</url>
            <distribution>repo</distribution>
        </license>
    </licenses>

    <!-- Developer Information (REQUIRED) -->
    <developers>
        <developer>
            <id>timhwang777</id>
            <name>Your Name</name>
            <email>your.email@example.com</email>
            <url>https://github.com/timhwang777</url>
        </developer>
    </developers>

    <!-- Source Control (REQUIRED) -->
    <scm>
        <connection>scm:git:git://github.com/timhwang777/uni-secret-manager-spring.git</connection>
        <developerConnection>scm:git:ssh://github.com:timhwang777/uni-secret-manager-spring.git</developerConnection>
        <url>https://github.com/timhwang777/uni-secret-manager-spring</url>
    </scm>

    <!-- Issue Tracking (optional but recommended) -->
    <issueManagement>
        <system>GitHub Issues</system>
        <url>https://github.com/timhwang777/uni-secret-manager-spring/issues</url>
    </issueManagement>
</project>
```

### 3. Source and Javadoc JARs

Maven Central requires `-sources.jar` and `-javadoc.jar` files. Add these plugins:

```xml
<build>
    <plugins>
        <!-- Source JAR -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-source-plugin</artifactId>
            <version>3.3.0</version>
            <executions>
                <execution>
                    <id>attach-sources</id>
                    <goals>
                        <goal>jar-no-fork</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>

        <!-- Javadoc JAR -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-javadoc-plugin</artifactId>
            <version>3.6.3</version>
            <executions>
                <execution>
                    <id>attach-javadocs</id>
                    <goals>
                        <goal>jar</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 4. Code Quality

Before publishing:

- [ ] All tests pass: `mvn test`
- [ ] No security vulnerabilities in dependencies
- [ ] Code is well-documented
- [ ] README.md with usage instructions
- [ ] CHANGELOG.md tracking versions

---

## Option A: JitPack (Recommended for Team Use)

JitPack builds and serves Maven artifacts directly from your public GitHub repository.
There is no publishing step — JitPack builds on first consumer request and caches the result.

### How JitPack Works

1. You push a git tag (e.g., `v1.0.0`) to GitHub
2. A consumer adds the JitPack repository and dependency to their `pom.xml`
3. On first dependency resolution, JitPack clones your repo and runs `mvn install`
4. The built artifact is cached and served for all subsequent requests
5. Build logs are available at `https://jitpack.io/#timhwang777/uni-secret-manager-spring-boot-starter`

### Project Configuration

JitPack reads `jitpack.yml` in the project root to customize the build environment:

```yaml
# jitpack.yml
jdk:
  - openjdk21

install:
  - mvn install -DskipTests -Djacoco.skip=true --batch-mode
```

- **JDK 21**: JitPack defaults to JDK 8; this override is required for our project.
- **Skip tests**: JitPack does not provide Docker, so Testcontainers-based integration tests cannot run.
- **Skip JaCoCo**: Coverage checks fail with 0% when tests are skipped.

No changes to `pom.xml` are needed for JitPack — it uses `mvn install`, which does not
require `distributionManagement`.

### Consumer Setup

Consumers only need to add the following to their `pom.xml` — no tokens, no `settings.xml`:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.timhwang777</groupId>
  <artifactId>uni-secret-manager-spring-boot-starter</artifactId>
  <version>v1.0.0</version>
</dependency>
```

> **Note**: The groupId for JitPack is `com.github.timhwang777`, not `io.github.timhwang777`.
> This is a JitPack convention — the groupId is derived from the GitHub repository owner.

### Available Versions on JitPack

JitPack supports several version formats:

| Version Format | Example | Description |
|---------------|---------|-------------|
| Release tag | `v1.0.0` | Recommended for production |
| Commit hash | `a1b2c3d` | Specific commit (for testing) |
| Branch-SNAPSHOT | `main-SNAPSHOT` | Latest commit on a branch |

### Triggering a JitPack Build

```bash
# 1. Ensure everything passes locally
mvn clean verify

# 2. Tag a release
git tag v1.0.0
git push origin v1.0.0

# 3. (Optional) Verify the build at:
#    https://jitpack.io/#timhwang777/uni-secret-manager-spring-boot-starter/v1.0.0
```

The first consumer request for this version triggers the build. You can also
pre-trigger it by visiting the JitPack URL above.

### JitPack Troubleshooting

- **Build fails on JitPack**: Check build logs at `https://jitpack.io/#timhwang777/uni-secret-manager-spring-boot-starter`
- **First resolution is slow**: JitPack builds on demand; the first request takes 1-3 minutes. Subsequent requests are cached.
- **Wrong JDK version**: Verify `jitpack.yml` exists in the tagged commit and specifies `openjdk21`.

---

## Option B: GitHub Packages

GitHub Packages is integrated with GitHub Actions and supports organization-scoped access.

> **Limitation**: GitHub Packages requires authentication for consumers, even for public repositories. See [Consumer Setup Requirements](#consumer-setup-requirements-for-github-packages) below.
> Consider using [JitPack](#option-a-jitpack-recommended-for-team-use) if token-free access is preferred.

### Step 1: Maven Profile Configuration

GitHub Packages distribution is configured via the `github` Maven profile in `pom.xml`:

```xml
<profiles>
    <profile>
        <id>github</id>
        <distributionManagement>
            <repository>
                <id>github</id>
                <name>GitHub Packages</name>
                <url>https://maven.pkg.github.com/timhwang777/uni-secret-manager-spring-boot-starter</url>
            </repository>
        </distributionManagement>
    </profile>
</profiles>
```

Deploy with: `mvn deploy -Pgithub`

### Step 2: Configure Maven Settings

Create or edit `~/.m2/settings.xml`:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_GITHUB_TOKEN</password>
        </server>
    </servers>
</settings>
```

### Step 3: Create GitHub Personal Access Token

1. Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Click "Generate new token (classic)"
3. Select scopes:
   - `write:packages` - Upload packages
   - `read:packages` - Download packages
   - `delete:packages` - (optional) Delete packages
4. Copy the token and use it in `settings.xml`

### Step 4: Deploy

```bash
# Remove -SNAPSHOT from version for release
mvn versions:set -DnewVersion=1.0.0

# Deploy to GitHub Packages
mvn deploy
```

### Step 5: Using Your Package

Others can use your package, but they must complete additional setup first.

#### Consumer Setup Requirements for GitHub Packages

> **This is a significant limitation of GitHub Packages.**
>
> Unlike Maven Central, GitHub Packages requires authentication even for downloading packages from public repositories. Each consumer of your library must complete these steps before they can use it.

**Step A: Consumer creates a GitHub Personal Access Token**

1. Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Click "Generate new token (classic)"
3. Select scope: `read:packages`
4. Copy the generated token

**Step B: Consumer configures their `~/.m2/settings.xml`**

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>github</id>
            <username>CONSUMER_GITHUB_USERNAME</username>
            <password>CONSUMER_GITHUB_TOKEN</password>
        </server>
    </servers>
</settings>
```

**Step C: Consumer adds repository and dependency to their `pom.xml`**

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/timhwang777/uni-secret-manager-spring</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.github.timhwang777</groupId>
        <artifactId>uni-secret-manager-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

> **Why this matters**: This authentication requirement creates friction for potential users of your library. They need a GitHub account and must configure credentials just to try your library. For widely-used public libraries, this can significantly reduce adoption. Consider using Maven Central if you want frictionless public access.

---

## Option C: Maven Central (Recommended for Public Libraries)

Maven Central is the primary repository for Java artifacts. Publishing here makes your library available to everyone without authentication.

> **Key Advantage**: Unlike GitHub Packages, consumers can use your library by simply adding a dependency to their `pom.xml`. No GitHub account, no tokens, no `settings.xml` configuration required. This is the standard way Java libraries are distributed.

> **Important**: As of June 30, 2025, OSSRH (the old method) has been sunset. Use the new [Central Portal](https://central.sonatype.com/).

### Step 1: Create Central Portal Account

1. Go to [https://central.sonatype.com/](https://central.sonatype.com/)
2. **Sign in with GitHub** (recommended) - this automatically grants you the `io.github.timhwang777` namespace
3. If using email, you'll need to verify namespace ownership separately

### Step 2: Verify Namespace Ownership

If you signed in with GitHub, your `io.github.<username>` namespace is automatically verified.

For custom domains (e.g., `com.example`):
1. Go to Central Portal → Namespaces
2. Add your namespace
3. Verify via DNS TXT record or other methods

### Step 3: Generate User Token

1. In Central Portal, go to your account settings
2. Generate a user token (username and password pair)
3. Save these credentials securely

### Step 4: Configure Maven Settings

Update `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>central</id>
            <username>YOUR_CENTRAL_TOKEN_USERNAME</username>
            <password>YOUR_CENTRAL_TOKEN_PASSWORD</password>
        </server>
    </servers>
</settings>
```

### Step 5: Add Central Publishing Plugin

Add to your `pom.xml`:

```xml
<build>
    <plugins>
        <!-- Central Publishing Plugin -->
        <plugin>
            <groupId>org.sonatype.central</groupId>
            <artifactId>central-publishing-maven-plugin</artifactId>
            <version>0.6.0</version>
            <extensions>true</extensions>
            <configuration>
                <publishingServerId>central</publishingServerId>
                <autoPublish>true</autoPublish>
            </configuration>
        </plugin>

        <!-- GPG Signing (REQUIRED for Central) -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-gpg-plugin</artifactId>
            <version>3.2.4</version>
            <executions>
                <execution>
                    <id>sign-artifacts</id>
                    <phase>verify</phase>
                    <goals>
                        <goal>sign</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Step 6: Deploy to Central

```bash
# Ensure version is NOT -SNAPSHOT
mvn versions:set -DnewVersion=1.0.0

# Deploy (includes signing)
mvn deploy
```

### Step 7: Verify Publication

1. Go to [Central Portal](https://central.sonatype.com/)
2. Check "Deployments" for your upload
3. If `autoPublish` is false, manually publish the deployment
4. Wait for sync to [search.maven.org](https://search.maven.org/) (can take up to 2 hours)

---

## Maven Profiles

This project uses Maven profiles to manage multiple publishing targets from a single
`pom.xml`. Profiles keep `distributionManagement` and target-specific plugins separated,
so the default build remains clean for local development and JitPack.

### Profile Summary

| Profile | Purpose | Activation |
|---------|---------|------------|
| *(none)* | Local development + JitPack | Default (`mvn verify`, `mvn install`) |
| `github` | Publish to GitHub Packages | `mvn deploy -Pgithub` (used by `publish.yml`) |
| `maven-central` | Publish to Maven Central | `mvn deploy -Pmaven-central` (future) |

### Why Profiles?

Without profiles, `distributionManagement` is always active. This means:
- `mvn deploy` without a profile would fail or target the wrong registry
- Adding Maven Central later would conflict with the GitHub Packages config
- JitPack uses `mvn install` (not `deploy`), so profiles don't affect it at all

With profiles, each `mvn deploy -P<target>` activates only the relevant configuration.

### Profile Definitions in `pom.xml`

```xml
<profiles>
    <!-- GitHub Packages: activated by publish.yml on tag push -->
    <profile>
        <id>github</id>
        <distributionManagement>
            <repository>
                <id>github</id>
                <name>GitHub Packages</name>
                <url>https://maven.pkg.github.com/timhwang777/uni-secret-manager-spring-boot-starter</url>
            </repository>
        </distributionManagement>
    </profile>

    <!-- Maven Central: placeholder for future publishing setup -->
    <profile>
        <id>maven-central</id>
        <!-- TODO: Add Central Publishing Plugin and GPG signing -->
    </profile>
</profiles>
```

---

## GPG Signing Setup

GPG signing is **required** for Maven Central and recommended for GitHub Packages.

### Step 1: Install GPG

```bash
# macOS
brew install gnupg

# Ubuntu/Debian
sudo apt-get install gnupg

# Verify installation
gpg --version
```

### Step 2: Generate Key Pair

```bash
gpg --full-generate-key
```

Select:
- Key type: RSA and RSA (default)
- Key size: 4096
- Expiration: 0 (never) or your preference
- Real name: Your Name
- Email: your.email@example.com
- Passphrase: Choose a strong passphrase

### Step 3: List Your Keys

```bash
gpg --list-secret-keys --keyid-format=long
```

Output example:
```
sec   rsa4096/ABCD1234EFGH5678 2024-01-15 [SC]
      1234567890ABCDEF1234567890ABCDEF12345678
uid                 [ultimate] Your Name <your.email@example.com>
ssb   rsa4096/WXYZ9876HIJK5432 2024-01-15 [E]
```

The key ID is `ABCD1234EFGH5678`.

### Step 4: Upload Public Key to Key Servers

```bash
# Upload to Ubuntu key server (used by Maven Central)
gpg --keyserver keyserver.ubuntu.com --send-keys ABCD1234EFGH5678

# Also upload to other servers for redundancy
gpg --keyserver keys.openpgp.org --send-keys ABCD1234EFGH5678
```

### Step 5: Configure Maven to Use GPG

Option A: Use gpg-agent (interactive):
```xml
<!-- No additional config needed, gpg-agent will prompt -->
```

Option B: Configure passphrase in settings.xml (for CI):
```xml
<settings>
    <profiles>
        <profile>
            <id>gpg</id>
            <properties>
                <gpg.keyname>ABCD1234EFGH5678</gpg.keyname>
            </properties>
        </profile>
    </profiles>
    <activeProfiles>
        <activeProfile>gpg</activeProfile>
    </activeProfiles>
</settings>
```

### Step 6: Export Keys for CI/CD

```bash
# Export private key (keep this secret!)
gpg --armor --export-secret-keys ABCD1234EFGH5678 > private-key.asc

# Store the content as a GitHub secret
```

---

## GitHub Actions CI/CD

Automate publishing with GitHub Actions.

### Create Workflow File

Create `.github/workflows/publish.yml`:

```yaml
name: Publish Package

on:
  release:
    types: [created]

jobs:
  publish-github:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          server-id: github

      - name: Publish to GitHub Packages
        run: mvn --batch-mode deploy -Pgithub
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  publish-central:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          server-id: central
          server-username: MAVEN_USERNAME
          server-password: MAVEN_PASSWORD
          gpg-private-key: ${{ secrets.GPG_PRIVATE_KEY }}
          gpg-passphrase: MAVEN_GPG_PASSPHRASE

      - name: Publish to Maven Central
        run: mvn --batch-mode deploy -Pgpg
        env:
          MAVEN_USERNAME: ${{ secrets.CENTRAL_USERNAME }}
          MAVEN_PASSWORD: ${{ secrets.CENTRAL_PASSWORD }}
          MAVEN_GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
```

### Required GitHub Secrets

Go to Repository → Settings → Secrets and variables → Actions:

| Secret | Description |
|--------|-------------|
| `CENTRAL_USERNAME` | Central Portal token username |
| `CENTRAL_PASSWORD` | Central Portal token password |
| `GPG_PRIVATE_KEY` | Output of `gpg --armor --export-secret-keys` |
| `GPG_PASSPHRASE` | Your GPG key passphrase |

**Note**: `GITHUB_TOKEN` is automatically provided.

---

## Version Management

### Semantic Versioning

Follow [SemVer](https://semver.org/):
- **MAJOR** (1.0.0 → 2.0.0): Breaking changes
- **MINOR** (1.0.0 → 1.1.0): New features, backward compatible
- **PATCH** (1.0.0 → 1.0.1): Bug fixes, backward compatible

### Release Process

1. **Update version** (remove -SNAPSHOT):
   ```bash
   mvn versions:set -DnewVersion=1.0.0
   mvn versions:commit
   ```

2. **Update CHANGELOG.md**

3. **Commit and tag**:
   ```bash
   git add .
   git commit -m "Release version 1.0.0"
   git tag -a v1.0.0 -m "Version 1.0.0"
   git push origin main --tags
   ```

4. **Create GitHub Release**:
   - Go to Releases → Draft a new release
   - Choose your tag
   - Add release notes
   - Publish (triggers CI/CD)

5. **Prepare next development version**:
   ```bash
   mvn versions:set -DnewVersion=1.1.0-SNAPSHOT
   mvn versions:commit
   git add .
   git commit -m "Prepare for next development iteration"
   git push
   ```

---

## Troubleshooting

### Common Issues

**1. "401 Unauthorized" when deploying to GitHub Packages**
- Verify your token has `write:packages` scope
- Check token hasn't expired
- Ensure `<id>` in settings.xml matches distributionManagement

**2. "403 Forbidden" for Maven Central**
- Verify namespace ownership in Central Portal
- Check token credentials are correct
- Ensure version doesn't end in -SNAPSHOT

**3. GPG signing fails**
- Ensure GPG is installed: `gpg --version`
- Check key is not expired: `gpg --list-keys`
- For CI, ensure private key is properly base64 encoded

**4. "Invalid POM" for Maven Central**
- All required metadata must be present
- version cannot be -SNAPSHOT
- Check for missing `<name>`, `<description>`, `<url>`, `<licenses>`, `<developers>`, `<scm>`

**5. Javadoc generation fails**
- Ensure code has proper Javadoc comments
- Check for HTML errors in Javadoc
- Use `<doclint>none</doclint>` for quick fix (not recommended long-term)

---

## Quick Reference Checklist

### Before First Release

- [ ] Choose and add LICENSE file
- [ ] Complete POM metadata (name, description, url, licenses, developers, scm)
- [ ] Add maven-source-plugin
- [ ] Add maven-javadoc-plugin
- [ ] Set up GPG signing
- [ ] Configure publishing destination (GitHub Packages or Central)
- [ ] Set up GitHub Actions workflow
- [ ] Create README.md with usage instructions
- [ ] All tests pass

### For Each Release

- [ ] Update version (remove -SNAPSHOT)
- [ ] Update CHANGELOG.md
- [ ] Run full test suite
- [ ] Commit, tag, and push
- [ ] Create GitHub Release
- [ ] Verify package appears in registry
- [ ] Bump to next SNAPSHOT version

---

## Additional Resources

- [GitHub Packages Documentation](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
- [Maven Central Requirements](https://central.sonatype.org/publish/requirements/)
- [Central Portal](https://central.sonatype.com/)
- [Apache Maven GPG Plugin](https://maven.apache.org/plugins/maven-gpg-plugin/)
- [Semantic Versioning](https://semver.org/)
