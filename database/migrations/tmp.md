目前最稳妥的展示方式是：**快速功能展示可用 H2，完整展示举报和管理员功能必须用 MySQL**。因为 H2 重启后账号和角色都会丢失，管理员授权无法在演示过程中持久保存。

## 一、启动完整演示环境

先在项目根目录初始化 MySQL：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS hust_bookloop CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -u root -p hust_bookloop < database/schema.sql
mysql -u root -p hust_bookloop < database/seed.sql
```

如果使用专用数据库账号，提前创建并授权，然后设置环境变量：

```bash
export DB_URL='jdbc:mysql://localhost:3306/hust_bookloop?connectionTimeZone=UTC'
export DB_USERNAME='bookloop'
read -s -p '数据库密码: ' DB_PASSWORD
export DB_PASSWORD
```

使用 `8091` 端口启动，避免 8080 冲突：

```bash
mvn -Dmaven.repo.local=.cache/maven spring-boot:run \
  -Dspring-boot.run.profiles=mysql \
  -Dspring-boot.run.arguments=--server.port=8091
```

打开：

```text
http://localhost:8091
```

## 二、准备账号

准备三个独立浏览器会话，不要使用同一个会话反复切换：

- 普通窗口：卖家 `seller_demo`
- 无痕窗口：买家 `buyer_demo`
- Edge 或另一个无痕窗口：管理员 `admin_demo`

先通过 `/register` 注册这三个普通账号。

然后管理员账号需要先退出，在数据库执行：

```sql
UPDATE app_user
SET role = 'ADMIN'
WHERE username = 'admin_demo';
```

之后让 `admin_demo` 重新登录。重新登录后，进入“我的账户”，会看到：

- 处理待审举报
- 管理资料限制

## 三、推荐展示流程

### 1. 展示市场

访问：

```text
http://localhost:8091/market
```

展示关键词、专业、学期、类别、标签、价格、成色、出售／赠送筛选。

### 2. 卖家发布资料

使用 `seller_demo` 进入 `/books/new`，发布一份资料：

- “数据结构（C语言版）”
- 教材
- 少量使用痕迹
- 无笔记
- 上传 1 张图片
- 出售价格 20 元
- 联系方式和适用范围正常填写

发布后：

1. 回到市场搜索该资料。
2. 进入详情页。
3. 进入 `/my/listings` 展示编辑入口。

### 3. 买家查看联系方式和举报

切换到 `buyer_demo`：

1. 从市场进入卖家资料详情。
2. 点击“登录后查看联系方式”。
3. 在详情页底部找到“举报资料”。
4. 选择“信息不准确”，填写“封面与实物可能不一致”。
5. 提交举报。

再次提交同一举报，展示“你已举报过该资料，请等待管理员处理”。

### 4. 管理员查看举报

切换到 `admin_demo`：

1. 进入 `/admin/reports`。
2. 展示举报原因、补充说明、举报人和关联资料。
3. 选择“处理举报”。
4. 填写处理说明，例如“核实后要求下架”。
5. 勾选“同时下架关联资料”。
6. 填写管理员原因，例如“描述与实物不一致”。
7. 提交。

### 5. 展示发布者不能恢复

切回 `seller_demo`：

1. 进入 `/my/listings`。
2. 展示资料状态为“已下架（管理员限制）”。
3. 尝试进入编辑页，展示无法编辑。
4. 展示没有“重新上架”按钮。

### 6. 管理员解除限制

切换到 `admin_demo`：

1. 进入 `/admin/listings`。
2. 找到被下架资料及管理员原因。
3. 点击“解除限制”。

切回 `seller_demo`，进入 `/my/listings`：

- 原来在售的资料现在显示“已下架”。
- 点击“重新上架”后恢复为在售。

### 7. 展示已完成资料保持完成状态

再发布或准备一份资料并由卖家标记完成。然后管理员从 `/admin/listings`：

1. 管理员下架该资料。
2. 再解除限制。

展示解除后仍然是“已售出／已送出”，不会恢复成在售。

## 四、最后展示测试

停止应用后在另一个终端运行：

```bash
mvn -Dmaven.repo.local=.cache/maven verify
```

展示最终结果：

```text
Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

同时可以展示：

```bash
git log --oneline --decorate -10
```

注意演示时使用真实页面 `/market`、`/my/listings`、`/admin/reports`、`/admin/listings`，不要把 `/prototype/` 下的模拟页面作为后端功能展示。
