# Maven 发布

当前开发版本为 **`0.1.0-SNAPSHOT`**（`pom.xml` 的 `<version>`）。可多次 `mvn deploy` 覆盖同一 SNAPSHOT，消费方配置 `updatePolicy always` 即可拉最新构建。

稳定发布时再改为 `0.1.0`、`0.1.1` 等 release 版本。

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

### 2. 发布 SNAPSHOT

```bash
export GITHUB_TOKEN=ghp_xxx
mvn clean deploy
```

### 3. 消费方 `pom.xml`

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/emojackob/deposit-sdk</url>
    <snapshots>
      <enabled>true</enabled>
      <updatePolicy>always</updatePolicy>
    </snapshots>
  </repository>
</repositories>

<dependency>
  <groupId>io.github.emojackob.deposit</groupId>
  <artifactId>deposit-sdk</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

拉取时建议：`mvn -U clean compile`（强制检查远程 SNAPSHOT 更新）。

读私有包时，消费方 `settings.xml` 也要配置同名 `github` server。
