# HUST BookLoop

华中科技大学计算机科学与技术学院二手教材与学习资料流转平台。

## 当前进度

已完成需求、页面原型、数据库设计、Spring Boot基础工程，以及基于数据库的注册、登录、退出、默认联系方式修改和密码修改。教材市场已支持数据库查询、筛选、排序和分页；真实详情页与按权限展示联系方式已实现；登录后的发布表单、图片选择预览及multipart提交校验已接入；真正发布入库、图片持久保存、编辑和交易状态服务尚未实现。原型中的sessionStorage数据不会自动导入数据库。

## 开发环境

- Java 21（JDK，包含javac）。
- Maven 3.6.3及以上。
- Spring Boot 4.1.2，版本固定在pom.xml。
- 数据库设计目标为MySQL 8.4，当前本地安装为MySQL 8.0.46，仍需完成集成验证；本地预览使用H2内存数据库，可测试账户和市场查询流程，不用于替代MySQL建表验证。

项目位于WSL Ubuntu，以下命令在Ubuntu终端、仓库根目录运行。无需另外安装Tomcat。

## 构建和启动

```bash
mvn -Dmaven.repo.local=.cache/maven clean verify
mvn -Dmaven.repo.local=.cache/maven spring-boot:run
```

首次构建会下载依赖，需要联网。依赖缓存放在被Git忽略的`.cache/maven`。

打开 <http://localhost:8080>。进入首页后可以打开真实教材市场、账户页和独立页面原型。默认仅监听127.0.0.1，尚不用于对外部署。按Ctrl+C停止。

真实账户入口是`/register`（注册）、`/login`（登录）和`/account`（账户及退出）。账号支持4–20位英文字母、数字或下划线，统一按小写保存；密码为6–64个字符且UTF-8编码不超过72字节，使用BCrypt哈希保存。未登录访问账户页会跳转登录，退出使用带CSRF令牌的POST表单。

默认`preview`模式会创建实体对应的临时表，停止应用后数据丢失；需要持久保存时使用下方MySQL模式。账户页可修改或清空默认联系方式；修改密码需验证原密码，成功后退出当前会话并重新登录。账号不可修改。真实登录与`/prototype/`内的演示登录相互独立，原型中的发布、交易和管理操作仍为模拟。

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

该模式从环境读取凭据，连接缺失会启动失败。当前映射了`app_user`及市场所需的`listing`、`listing_scope`、`major`、`tag`、`listing_tag`及详情所需的`listing_contact`。市场实体是只读查询映射，仅包含查询需要的字段；`ddl-auto=validate`会检查已映射的结构，不能证明全部业务表、未映射字段或数据库约束符合设计。预览模式下通过的测试也不代表MySQL集成验证通过。可在MySQL模式注册账户、退出并重新登录，再重启应用验证账户仍然存在。

## 教材市场

访问`/market`（例如 <http://localhost:8081/market>）。页面沿用原型样式，数据来自当前连接的数据库，不加载`books.js`或`demo-state.js`。

- 关键词匹配书名、作者、课程、标签名称；`%`和`_`按普通字符搜索。
- 专业、学期、类别、多选标签、流转方式、成色、价格区间可组合筛选，点击“搜索”或“应用筛选”提交。
- 具体专业包含“不限专业”资料，但专业与学期必须由同一组适用范围满足；“未定义”是精确学期选项。
- 多个标签匹配任一所选标签；停用标签仍可用于历史资料筛选。
- 默认最新发布，支持价格升序、降序；选择排序后点击“确定”。赠送价格按0参与筛选与排序。
- 每页20条，分页保留条件，修改条件重新从第一页查询。“重置筛选”清空全部条件。
- 只显示未被管理员限制的`ACTIVE`条目，列表不返回联系方式。无数据时展示空状态，不生成示例库存。

市场卡片可点击进入真实详情页，列表与详情均使用明确标注的封面占位；图片展示和发布功能尚待接入。此阶段无需修改已有MySQL建表结构。预览模式不自动导入专业、标签或书籍；MySQL模式的基础字典由`database/seed.sql`提供。

验证命令为上方的`mvn ... verify`。`MarketFlowTest`在H2中插入独立测试数据，覆盖公开状态、专业学期组合、多标签去重、关键词、价格排序、分页、空状态、HTML转义和非法参数；测试数据会清理，不写入真实MySQL。新增`BookFlowTest`验证详情权限矩阵、游客不查询联系方式、登录返回、筛选保留、文本转义、无效编号和UTC时间展示。MySQL实际联调和浏览器视觉检查仍待完成。

## 书籍详情

从`/market`点击书籍卡片进入`/books/{id}`。详情展示数据库中的资料、适用范围、卖家账号和北京时间发布时间。说明采用转义文本并保留换行；可选字段为空时显示“未填写”。

- 未受限的在售、待赠送和已预订资料可公开查看正文。游客不查询联系方式；登录后只读取该条目在`listing_contact`中保存的联系方式，不使用卖家默认联系方式补齐。
- 点击“登录后查看联系方式”访问受保护的`/books/{id}/contact`。登录成功后回到原详情；登录期间资料变为不可获取时重新检查权限。直接登录仍默认进入账户页。
- 已完成、下架或受管理员限制的资料，对游客及其他普通用户返回HTTP 410，仅显示不可获取提示；所有者及管理员仍可只读查看。不存在或无效编号返回HTTP 404。
- “返回市场”保留关键词、筛选、排序和页码。`returnTo`仅接受本站`/market`及其查询参数；外部地址和其他路径回退到`/market`。
- 图片继续使用占位，不提供尚未实现的举报、编辑或状态修改操作。无需修改MySQL表结构，也不会自动生成业务数据。

## 发布表单（填写与校验阶段）

访问`/books/new`，未登录会先进入登录页，成功后返回表单。首页、市场、详情和账户页均有入口。

- 表单包括基本信息、成色、笔记程度、说明、适用专业学期组合、可选课程、多选标签、出售／赠送和联系方式。
- 默认适用范围为“不限专业＋未定义”；可以新增或删除范围，至少保留一组，最多50组，拒绝重复组合。仅提供启用的专业和标签。
- 联系方式预填当前用户的默认值，表单修改不会更新个人资料。赠送价格在前后端均归零，出售要求0.01—9999.99元，最多两位小数。
- 照片支持分批添加、本地预览、删除和设为封面，最终文件顺序与预览一致；限制1—6张，每张不超过5MB，JPG／PNG／WebP。
- 点击“校验并预览”通过multipart表单向`POST /books/preview`提交文字和图片。该接口受登录和CSRF保护，只校验并返回预览，不写入业务表或持久保存文件。当前图片校验覆盖数量、大小和声明的MIME类型；实际图片解码与安全存储将在持久化阶段接入。
- 返回页面保留文字和选项，明确提示尚未发布；浏览器文件输入不会回填，再次提交需重新选择图片。文件超过单张或总请求32MB限制时返回友好的HTTP 413页面。

`PublishFormTest`验证登录回跳、默认联系方式、停用选项隔离、多组范围、赠送归零、multipart、CSRF、文件限制和不产生发布记录。全部21项自动化测试通过，JS语法检查通过；尚未完成浏览器交互视觉检查和MySQL联调。

## 目录

```text
prototype/                    独立HTML原型、样式及演示脚本
database/                     MySQL建表及初始专业、标签
src/main/java/cn/edu/hust/bookloop/
  BookLoopApplication.java    启动类
  config/                     安全等配置
  account/                    账户实体、仓库和注册服务
  market/                     市场只读映射、查询条件和分页服务
  book/                       详情DTO、访问权限和条目联系方式查询
  publish/                    发布表单与可用选项查询
  web/                        MVC控制器
src/main/resources/
  templates/                  Thymeleaf模板
  static/css/                 真实市场页面补充样式
  static/js/                  真实发布表单交互
  application*.yml            公共、预览、MySQL配置
src/test/java/                启动、HTTP烟雾、账户、市场、详情及发布表单测试
```

构建时将原型复制进应用资源的`static/prototype`目录，源码仍以prototype为准，不要编辑target中的生成副本。修改原型后重新启动开发命令或重新打包即可更新。

后续真实页面逐步迁入templates，CSS与需要保留的交互脚本迁入静态资源；演示登录及demo-state.js不会作为真实权限或业务实现。

## 相关文档

- [需求分析](./需求分析文档.md)
- [项目工作流程](./项目工作流程.md)
- [数据库设计](./数据库设计.md)
- [原型使用说明](./prototype/README.md)
- [Spring Boot官方环境要求](https://docs.spring.io/spring-boot/system-requirements.html)
