# Maven 发布

## 本地开发（无需 GitHub）

```bash
mvn clean install
```

安装到 `~/.m2/repository`，demo 等项目可直接依赖。

## 发布到 GitHub Packages

### 1. `~/.m2/settings.xml`

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>emojackob</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

`GITHUB_TOKEN` 需 `read:packages` + `write:packages`（私有仓库还需 `repo`）。

### 2. 发布

```bash
export GITHUB_TOKEN=ghp_xxx
mvn clean deploy
```

`pom.xml` 中 `distributionManagement.repository.id` 必须为 `github`，与 settings 一致。

### 3. 消费方 `pom.xml`

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/emojackob/deposit-sdk</url>
  </repository>
</repositories>
```

读私有包时，消费方 `settings.xml` 也要配置同名 `github` server。
