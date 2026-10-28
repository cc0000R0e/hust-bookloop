# HUST BookLoop

华中科技大学计算机科学与技术学院二手教材与学习资料流转平台。

## 当前进度

已完成需求、页面原型、数据库设计及Spring Boot基础工程。真实注册登录、资料CRUD、图片上传和交易状态服务尚未实现。原型中的sessionStorage数据不会自动导入数据库。

## 开发环境

- Java 21（JDK，包含javac）。
- Maven 3.6.3及以上。
- Spring Boot 4.1.2，版本固定在pom.xml。
- 正式数据目标为MySQL 8.4；本地预览暂用H2内存数据库，仅用于让基础工程启动，不用于替代MySQL建表验证。

项目位于WSL Ubuntu，以下命令在Ubuntu终端、仓库根目录运行。无需另外安装Tomcat。

## 构建和启动

```bash
mvn -Dmaven.repo.local=.cache/maven clean verify
mvn -Dmaven.repo.local=.cache/maven spring-boot:run
```

首次构建会下载依赖，需要联网。依赖缓存放在被Git忽略的`.cache/maven`。

打开 <http://localhost:8080>。进入首页后可以打开市场、个人中心和管理后台原型。默认仅监听127.0.0.1，尚不用于对外部署。按Ctrl+C停止。

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

```bash
mvn -Dmaven.repo.local=.cache/maven spring-boot:run -Dspring-boot.run.profiles=mysql
```

该模式从环境读取凭据，连接缺失会启动失败。当前还没有业务实体，因此`ddl-auto=validate`不能证明10张业务表已经符合设计；应单独执行建表验证，随后逐步增加实体映射和数据库测试。预览模式下通过的测试也不代表MySQL集成验证通过。

## 目录

```text
prototype/                    独立HTML原型、样式及演示脚本
database/                     MySQL建表及初始专业、标签
src/main/java/cn/edu/hust/bookloop/
  BookLoopApplication.java    启动类
  config/                     安全等配置
  web/                        MVC控制器
src/main/resources/
  templates/                  Thymeleaf模板
  application*.yml            公共、预览、MySQL配置
src/test/java/                启动与HTTP烟雾测试
```

构建时将原型复制进应用资源的`static/prototype`目录，源码仍以prototype为准，不要编辑target中的生成副本。修改原型后重新启动开发命令或重新打包即可更新。

后续真实页面逐步迁入templates，CSS与需要保留的交互脚本迁入静态资源；演示登录及demo-state.js不会作为真实权限或业务实现。

## 相关文档

- [需求分析](./需求分析文档.md)
- [项目工作流程](./项目工作流程.md)
- [数据库设计](./数据库设计.md)
- [原型使用说明](./prototype/README.md)
- [Spring Boot官方环境要求](https://docs.spring.io/spring-boot/system-requirements.html)
