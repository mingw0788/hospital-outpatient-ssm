# IDEA 本机运行指南

本指南使用 Windows、IntelliJ IDEA 和本机 MySQL。所有相对路径均以 `hospital-outpatient` 为项目根目录。后端是 Maven 工程，前端是独立的 Vue 3 工程，需要分别启动。

## 1. 准备环境

| 软件 | 使用版本 | 检查方式 |
| --- | --- | --- |
| JDK | 17 或 21 | `java -version` |
| Maven | 3.9.x，或 IDEA 内置 Maven | `mvn -version`；脚本运行需要 PATH 中有 Maven |
| Node.js | 22.12 或更高兼容版本 | `node -v`、`npm -v` |
| MySQL | 8.0.16 或更高的 8.x 版本 | Navicat 执行 `SELECT VERSION();` |
| Navicat | 可连接本机 MySQL 即可 | 打开连接并测试连接 |
| IntelliJ IDEA | 社区版或 Ultimate | 社区版使用普通 Java Application 运行 |

Navicat 是数据库管理工具，MySQL 服务才是数据库服务器。先确认 Navicat 能连接 `127.0.0.1:3306`。无需为基础业务安装 Redis。

## 2. 导入项目并配置 JDK

1. IDEA 选择 **File → Open**，打开包含根 `pom.xml` 的 `hospital-outpatient` 文件夹。
2. 如果提示加载 Maven 项目，选择加载；在右侧 Maven 面板点击 **Reload All Maven Projects**。
3. 在 **File → Project Structure → Project** 中将 Project SDK 设为 JDK 17 或 21。代码编译目标为 Java 17。
4. 在 **Settings → Build, Execution, Deployment → Build Tools → Maven → Runner** 中选择同一 JDK；如有 Importing JDK 选项也保持一致。
5. 等待 Maven 依赖下载结束，确认 `backend/src/main/java` 被识别为源码目录。

不要只打开 `frontend` 文件夹来运行 Java 后端；根 Maven 工程包含 `backend` 模块，前端仍由 npm 管理。

## 3. 配置数据库与首次初始化

打开 `backend/src/main/resources/application-local.yml`。该本机私有文件已按当前电脑配置，并已被 Git 忽略；移动项目或换电脑时请重新检查。若文件不存在，复制同目录 `application-local.yml.example`，将副本改名为 `application-local.yml`，填写自己的数据库连接信息。

重点检查：

- 数据库主机与端口：`127.0.0.1:3306`。
- 数据库名称：`hospital`。
- `spring.datasource.username`：当前本机 MySQL 用户名。
- `spring.datasource.password`：该 MySQL 用户的密码；在 YAML 中建议用引号包围。

本地密码直接填写在 `application-local.yml` 的 `spring.datasource.password`。不要在同一字段写 `${DB_PASSWORD:...}`：Spring Boot 会把 `DB_PASSWORD` 解析成该数据源字段的环境变量别名，可能造成自引用。

项目默认启用 `local` profile。本机配置的 JDBC URL 包含 `createDatabaseIfNotExist=true`，首次启动时可自动创建 `hospital`；账号需要具备建库、建表以及业务读写权限。随后 Spring SQL 初始化依次执行建表脚本，空库演示初始化创建账号、科室、医生、药品和未来七天内的排班。

重复启动使用 `CREATE TABLE IF NOT EXISTS` 保留表。空库会建立基础演示数据；如果现有库包含本项目的 `admin`、`patient` 与 `doctor` 演示账号，启动时会补齐缺少的演示科室、医生、患者、药品和未来排班。演示模式还会在启动时及每天 00:05（上海时区）滚动补齐未来七天排班，只创建缺少且不与现有时段重叠的排班，不覆盖人工维护、已停诊或已有业务的记录。普通已有用户的数据库不会触发演示数据补充。建表脚本用于初始化，不能代替未来表结构变更的迁移脚本。

### 使用 Navicat 手动初始化

也可以在启动后端前手动执行：

1. 用具有建库权限的账号运行 `sql/000-create-database.sql`。
2. 刷新连接，选择 `hospital` 数据库。
3. 按顺序运行 `sql/001-base.sql`、`sql/002-business.sql`、`sql/003-advanced.sql`。

这些脚本创建表，不清空数据。演示账号由后端首次空库启动时生成。日常在 Navicat 查看 `hospital` 下的表即可，不需要反复导入 SQL。

SQL 源文件统一保存在 `sql/`；Maven 构建时复制到后端 classpath 的 `db/`。修改 SQL 后应重新加载或编译 Maven 工程，不要修改 `backend/target/classes/db/` 内的构建产物。

## 4. 在 IDEA 启动后端

推荐直接打开：

```text
backend/src/main/java/com/example/hospital/HospitalApplication.java
```

点击 `main` 方法旁的绿色三角，选择 **Run 'HospitalApplication.main()'**。默认 profile 是 `local`，不需要额外设置环境变量或 Active profiles。

项目也提供 `.run/Hospital Backend (local).run.xml`：若 IDEA 已识别，可在右上角选择 **Hospital Backend (local)** 并运行。它使用普通 Java Application 配置，社区版也可用。

控制台出现 `Started HospitalApplication` 且没有随后报错，表示后端已经启动，默认端口为 `8080`。直接访问后端根地址可能显示未找到页面；用户页面由前端提供。可打开 `http://127.0.0.1:8080/api/auth/csrf` 检查接口是否返回 JSON。

若 IDEA 旧运行配置设置过 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`SEED_DEMO` 或 `SPRING_PROFILES_ACTIVE`，它们可能覆盖文件配置。出现配置与文件不一致时，检查 **Run → Edit Configurations** 中的环境变量、程序参数和 VM 参数。

## 5. 在 IDEA 启动前端

后端保持运行，打开 IDEA 底部 Terminal。在项目根目录执行：

```powershell
cd frontend
npm install
npm run dev
```

首次需要安装依赖，后续通常直接执行 `npm run dev`。浏览器打开：

```text
http://127.0.0.1:5173/
```

前端通过 `/api` 将请求代理到 `http://127.0.0.1:8080`。正常操作只需访问前端地址。前端终端和后端运行窗口都应保持开启；停止时分别在前端终端按 `Ctrl+C`、在 IDEA 后端窗口点击停止按钮。

如果同时测试不同角色，先退出再切换账号，或使用不同浏览器/无痕窗口。普通同一浏览器的标签页通常共享登录会话。

## 6. 演示账号与验收流程

首次空库初始化的默认密码均为 `Demo12345!`：

| 账号 | 角色 |
| --- | --- |
| `patient`、`patient2`、`patient3`、`patient4`、`patient5` | 患者 |
| `doctor`、`doctor2`、`doctor3`、`doctor4` | 医生 |
| `cashier` | 收费员 |
| `pharmacist` | 药师 |
| `admin` | 管理员 |

演示数据库包含 3 笔未来挂号（2 笔已缴费、1 笔待缴费）；`patient2` 另有一条虚构的历史就诊、电子病历、处方及缴费记录，可用于查看完整页面。病历与处方只用于教学演示，不构成医疗建议。演示资料在应用启动时幂等补齐，不会重复添加。

建议按以下顺序验收：

1. **挂号与缴费**：患者进入“排班与挂号”，选择尚未开始且有余号的排班，提交挂号；在“门诊缴费”模拟付款，检查挂号变为已预约。
2. **取消与退号**：另建一笔未付款挂号后取消，观察号源释放；已付款且符合退号条件的挂号使用退号退款流程。
3. **报到与接诊**：患者在“我的挂号”报到；使用该排班对应医生账号进入“候诊与接诊”，依次叫号、开始接诊、填写病历并提交处方。
4. **药费与发药**：患者支付处方药费；药师进入“处方与发药”确认发药，并查看库存记录。
5. **数据核对**：患者查看电子病历、挂号和缴费记录；管理员查看业务数据和维护页面，Navicat 中检查相应数据已保存。

报到仅允许在**就诊当天开诊前 30 分钟至排班结束前**进行；挂号须在排班开始前完成，未付款号源最多保留 15 分钟且不会晚于开诊时间。未来几天的预约可以测试挂号缴费，但需等到报到窗口才能继续接诊。

需要立即演示完整流程时，用管理员创建“今天、约 15 分钟后开始”的开放排班，结束时间晚于开始时间，并避开该医生其他排班的时间段。患者选中该排班即可先挂号、付款和报到。`doctor` 与 `doctor2` 分别只能处理自己的排班和接诊记录。

患者排班页仅显示当前至未来七天内尚未开诊的开放排班；页面跨过上海时间午夜后会自动刷新日期和列表。历史排班及就诊记录保留原日期，便于查询历史，不会被滚动生成任务改写。非演示环境可由管理员维护排班，或配置下面的 Quartz 排班模板功能。

## 7. PowerShell 启动与构建

在项目根目录，两个终端分别运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-frontend.ps1
```

这些脚本按自身位置定位项目，避免依赖某台电脑的绝对路径。后端脚本需要 `java` 和 `mvn` 可用，前端脚本需要 `node` 和 `npm` 可用。

也可进入 `backend` 手动运行 Maven：

```powershell
mvn -s .mvn/settings.xml -gs .mvn/settings.xml spring-boot:run
```

需要编译检查或打包时，在 `backend` 执行：

```powershell
mvn -s .mvn/settings.xml -gs .mvn/settings.xml clean verify
```

打包产物位于 `backend/target/hospital-outpatient-1.0.0.jar`。停止 IDEA 中占用同一端口的后端后，在 `backend` 执行：

```powershell
java -jar target/hospital-outpatient-1.0.0.jar
```

在 `frontend` 执行 `npm run build` 会生成 `frontend/dist/`。开发演示仍使用 `npm run dev`，它提供本项目配置的接口代理。

## 8. 可选的 Redis 与 Quartz

默认 `QUEUE_ENABLED=false`、`QUARTZ_ENABLED=false`，基础业务不连接 Redis 队列，也不启动 Quartz 排班任务。

| 功能 | 开启前准备 | IDEA 运行配置中的环境变量 |
| --- | --- | --- |
| Redis 挂号队列 | 单独运行可访问的 Redis 7.x，检查地址、端口及密码 | `QUEUE_ENABLED=true`；按需设置 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` |
| Quartz 自动排班 | 管理员先维护有效的排班模板 | `QUARTZ_ENABLED=true` |

修改环境变量后重启后端。Redis 只负责接收与消费排队请求，入队成功需要继续查询处理结果，最终挂号仍受数据库事务、号源和重复预约约束控制。Quartz 根据模板生成排班，开启开关本身不会替你填写医生排班规则。

## 9. 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| `Access denied for user` | 在 Navicat 使用相同账号测试连接，修正 `application-local.yml` 用户名/密码；检查旧环境变量是否覆盖文件。 |
| `Communications link failure` / 连接被拒绝 | 确认 MySQL 服务已启动，主机和端口正确。 |
| 无权创建数据库或表 | 由数据库管理员用 Navicat 执行 `sql/` 初始化脚本，并为应用账号配置对应数据库权限。 |
| 找不到表 | 检查启动使用 `local` profile；重新构建 Maven 工程，确认 SQL 资源已复制；阅读控制台最早出现的 SQL 错误。 |
| `release version 17 not supported` | Maven 实际运行的 JDK 低于 17；检查 `mvn -version` 与 IDEA Maven Runner JDK。 |
| Maven 依赖下载失败、镜像地址失效 | IDEA Maven 设置中的 User settings file 选择根 `.mvn/settings.xml` 后重新加载；命令行进入 `backend` 使用 `-s .mvn/settings.xml -gs .mvn/settings.xml` 覆盖用户和全局镜像设置，并检查网络。 |
| 找不到 `mvn` | 在 IDEA 使用内置 Maven，或将自己的 Maven `bin` 加入 PATH，重新打开终端；脚本方式要求 PATH 配好。 |
| npm 提示版本不支持、找不到 node/npm | 安装 Node.js 22.12+，检查 PATH，重新打开 IDEA 终端后执行 `npm install`。PowerShell 对 npm 脚本有限制时可用 `npm.cmd install`、`npm.cmd run dev`。 |
| `Port 8080 was already in use` | 停止自己此前启动的同一项目实例；若端口由其他程序使用，给后端设置 `SERVER_PORT=8081`，并同步调整前端代理后重启前端。 |
| 前端 5173 被占用 | 关闭自己先前的前端终端，重新启动；以 Vite 控制台实际地址为准。 |
| 页面能打开，登录或接口请求失败 | 确认后端已完成启动；检查浏览器请求和后端日志，前端代理目标必须与实际后端端口一致。 |
| 登录演示账号失败 | 确认当前连接的是正确 `hospital` 数据库；演示数据只在空库初始化，有用户时不会重置密码。 |
| 无排班或提示“不在可预约时间内” | 选择未来七天范围内、尚未开诊的开放排班；演示模式会在应用启动时和每天 00:05 补齐演示医生的滚动排班。 |
| 已付款却无法报到 | 检查就诊日期、开诊前 30 分钟的报到窗口、排班是否停诊，以及电脑系统时间。 |

调整后端端口时，可在前端终端设置代理后重新启动：

```powershell
$env:API_PROXY='http://127.0.0.1:8081'
npm run dev
```

需要检查端口占用时可在 PowerShell 使用 `Get-NetTCPConnection -State Listen -LocalPort 8080` 查看所属进程，再确认是否为自己之前启动的项目。不要批量结束不明进程。
