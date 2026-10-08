# 医院门诊挂号与缴费系统

软件体系结构综合实训项目，使用 **Maven + Spring + Spring MVC + MyBatis（SSM）+ Vue 3 + MySQL**。Spring Boot 负责整合 SSM 与启动服务；Navicat 用于管理 MySQL。缴费为系统内模拟支付。

**在 IntelliJ IDEA 中自己运行，请先阅读 [IDEA 本机运行指南](docs/IDEA本机运行指南.md)。** IDEA 社区版也可以运行后端，前端在 IDEA 终端启动。

## 快速启动

1. 准备 JDK 17 或 21、Maven 3.9、Node.js 22.12+、MySQL 8.0.16+，并启动 MySQL 服务。
2. IDEA 打开本项目根目录（包含本文件和根 `pom.xml` 的 `hospital-outpatient`），加载 Maven，设置项目 JDK。
3. 检查 `backend/src/main/resources/application-local.yml` 的数据库地址、用户名和密码。本机配置已放在该文件中；若复制项目后文件缺失，从同目录 `.example` 文件复制并填写。默认使用 `local` 配置，无需手动填写 Active profiles。
4. 运行 `backend/src/main/java/com/example/hospital/HospitalApplication.java` 的 `main` 方法，或选择预置的 `Hospital Backend (local)` 运行配置。首次空库启动会创建表并生成演示数据；本项目演示库升级时会安全补齐更多演示资料，重复启动保留已有用户与业务记录。
5. 在 IDEA 终端执行：

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

6. 浏览器打开 **http://127.0.0.1:5173/**，使用 `patient` / `Demo12345!` 登录。后端监听 `127.0.0.1:8080`；前端通过 `/api` 转发接口请求。

也可在项目根目录的两个 PowerShell 终端分别运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-frontend.ps1
```

## 目录

```text
hospital-outpatient/
├─ pom.xml                  IDEA 导入用 Maven 聚合工程
├─ .run/                    IDEA 后端运行配置
├─ backend/                 Spring Boot 整合 SSM 后端
├─ frontend/                Vue 3 + Vite 前端
├─ sql/                     Navicat 可手动执行的数据库脚本
├─ scripts/                 本机启动脚本
├─ docs/                    IDEA 运行及排错指南
└─ .env.example             环境变量参考，不会被 Spring Boot 自动读取
```

## 演示账号

演示账号密码均为 `Demo12345!`；新增的演示患者、医生和排班只会补齐缺项，不重置已有业务记录或已有用户密码。

演示模式按上海时区在启动时及每天 00:05 自动补齐未来七天的演示排班；页面跨午夜时会刷新当天日期。已有排班、挂号及就诊历史会保留，不会被自动改写。

演示库还预置了 3 笔未来挂号（2 笔已缴费、1 笔待缴费），以及 `patient2` 的 1 条虚构历史就诊、病历、处方和缴费记录，方便直接查看患者端、医生端和药房页面。病历与处方均为教学样例，不代表真实诊疗建议。

| 角色 | 账号 | 主要操作 |
| --- | --- | --- |
| 患者 | `patient`、`patient2`、`patient3`、`patient4`、`patient5` | 查询排班、挂号、缴费、报到、查看病历 |
| 医生 | `doctor`、`doctor2`、`doctor3`、`doctor4` | 叫号、接诊、填写病历、开处方 |
| 收费员 | `cashier` | 处理缴费与退款 |
| 药师 | `pharmacist` | 库存管理、处方发药 |
| 管理员 | `admin` | 维护账号、科室、医生、排班和药品 |

## 技术与课程要求对应

| 技术 | 项目中的用途 |
| --- | --- |
| Maven | 依赖管理、编译、测试与打包 |
| Spring | 依赖注入、业务事务、AOP 挂号与退号日志 |
| Spring MVC | REST 接口、请求参数校验、统一异常处理 |
| MyBatis | Mapper 数据访问、排班与就诊记录动态查询 |
| SSM | Spring + Spring MVC + MyBatis，使用 Spring Boot 整合 |
| Vue 3 | 前端页面，配合 Vite、Vue Router、Pinia、Element Plus |
| MySQL / Navicat | 业务存储 / 数据库可视化管理 |
| Spring Security | 登录会话、角色权限与 CSRF 防护 |
| Redis Streams（可选） | 异步挂号队列；最终号源一致性仍由 MySQL 事务保障 |
| Quartz（可选） | 按排班模板定时生成未来排班 |

基础流程只需要 MySQL，Redis 和 Quartz 默认关闭。并发挂号使用行锁、条件扣减、唯一约束和请求幂等控制；缴费、退号、库存预占与发药在服务层校验状态并使用事务。

完整的数据库初始化说明、操作验收流程、可选功能配置、构建命令与常见错误处理见 [IDEA 本机运行指南](docs/IDEA本机运行指南.md)。
