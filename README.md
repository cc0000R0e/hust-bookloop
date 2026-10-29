# HUST BookLoop

华中科技大学计算机科学与技术学院二手教材与学习资料流转平台。

## 当前进度

已完成需求、页面原型、数据库设计、Spring Boot基础工程，以及基于数据库的注册、登录和退出。资料CRUD、图片上传和交易状态服务尚未实现。原型中的sessionStorage数据不会自动导入数据库。

## 开发环境

- Java 21（JDK，包含javac）。
- Maven 3.6.3及以上。
- Spring Boot 4.1.2，版本固定在pom.xml。
- 数据库设计目标为MySQL 8.4，当前本地安装为MySQL 8.0.46，仍需完成集成验证；本地预览使用H2内存数据库，可测试账户流程，不用于替代MySQL建表验证。

项目位于WSL Ubuntu，以下命令在Ubuntu终端、仓库根目录运行。无需另外安装Tomcat。

## 构建和启动

```bash
mvn -Dmaven.repo.local=.cache/maven clean verify
mvn -Dmaven.repo.local=.cache/maven spring-boot:run
```

首次构建会下载依赖，需要联网。依赖缓存放在被Git忽略的`.cache/maven`。

打开 <http://localhost:8080>。进入首页后可以打开市场、个人中心和管理后台原型。默认仅监听127.0.0.1，尚不用于对外部署。按Ctrl+C停止。

真实账户入口是`/register`（注册）、`/login`（登录）和`/account`（账户及退出）。账号支持4–20位英文字母、数字或下划线，统一按小写保存；密码为6–64个字符且UTF-8编码不超过72字节，使用BCrypt哈希保存。未登录访问账户页会跳转登录，退出使用带CSRF令牌的POST表单。

默认`preview`模式会创建临时账户表，停止应用后账户数据丢失；需要持久保存时使用下方MySQL模式。真实登录与`/prototype/`内的演示登录相互独立，原型中的发布、交易和管理操作仍为模拟。

也可在构建后运行应用包：

```bash
java -jar target/bookloop-0.0.1-SNAPSHOT.jar
```

端口冲突时，例如使用8081：

```bash
SERVER_PORT=8081 mvn -Dmaven.repo.local=.cache/maven spring-boot:run
```

## MySQL模式（数据库准备好后）

先阅读[数据库设计](./数据库设计.md)，在开发库执行[建表脚本](./database/schema.sql)及[基础数据](./database/seed.sql)。应用不会自动执行这两个文件，也不会删除或自动创建业务表。

在当前终端设置`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，例如DB_URL使用`jdbc:mysql://localhost:3306/hust_bookloop?connectionTimeZone=UTC`。密码通过本地环境设置，不提交到仓库。

若数据库是旧版本创建的，`app_user`仍有必填的`nickname`列，需要先用有表结构修改权限的数据库账号执行`database/migrations/001_remove_user_nickname.sql`，再启动新版应用，否则注册可能失败。该脚本只执行一次，会删除旧昵称，保留账号、密码及用户ID。使用当前`schema.sql`新建的库无需执行。系统统一使用账号展示用户身份。

```bash
mvn -Dmaven.repo.local=.cache/maven spring-boot:run -Dspring-boot.run.profiles=mysql
```

也可在设置好上述环境变量的终端运行打包后的应用：

```bash
java -jar target/bookloop-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql --server.port=8081
```

该模式从环境读取凭据，连接缺失会启动失败。当前仅映射了`app_user`实体，`ddl-auto=validate`会检查该实体对应的表，但不能证明其余9张业务表符合设计。预览模式下通过的测试也不代表MySQL集成验证通过。可在MySQL模式注册账户、退出并重新登录，再重启应用验证账户仍然存在。

## 目录

```text
prototype/                    独立HTML原型、样式及演示脚本
database/                     MySQL建表及初始专业、标签
src/main/java/cn/edu/hust/bookloop/
  BookLoopApplication.java    启动类
  config/                     安全等配置
  account/                    账户实体、仓库和注册服务
  web/                        MVC控制器
src/main/resources/
  templates/                  Thymeleaf模板
  application*.yml            公共、预览、MySQL配置
src/test/java/                启动、HTTP烟雾及账户流程测试
```

构建时将原型复制进应用资源的`static/prototype`目录，源码仍以prototype为准，不要编辑target中的生成副本。修改原型后重新启动开发命令或重新打包即可更新。

后续真实页面逐步迁入templates，CSS与需要保留的交互脚本迁入静态资源；演示登录及demo-state.js不会作为真实权限或业务实现。

## 相关文档

- [需求分析](./需求分析文档.md)
- [项目工作流程](./项目工作流程.md)
- [数据库设计](./数据库设计.md)
- [原型使用说明](./prototype/README.md)
- [Spring Boot官方环境要求](https://docs.spring.io/spring-boot/system-requirements.html)
