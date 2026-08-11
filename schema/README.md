# Schema（PostgreSQL + PGVector）

对齐 `docs/database-design.md`。文件编码 **UTF-8**。  
本目录与 Flyway `db/migration` **纳入版本管理**。

## 文件说明

| 文件 | 内容 |
|---|---|
| `00_extensions.sql` | `CREATE EXTENSION vector` |
| `01_sys_user.sql` … `20_prompt_template_version.sql`、`27_rag_prompts.sql` | 业务表 / 向量表 / Agent Checkpoint / Prompt 种子与版本表 / admin / 知识库提示词 |
| `all.sql` | 用 `\ir` 按序串联以上脚本 |

等价迁移：`V1__init.sql` … `V21__rag_prompts.sql`（`prompt_template` 每 code 一行 + `prompt_template_version`；V21 种子知识库提示词）。

## 一键落库

确认本机已安装 **pgvector**，库 `vector_test` 已存在后：

```bash
psql "postgresql://postgres:1234567890@127.0.0.1:5432/vector_test" -f schema/all.sql
```

Windows 示例（按本机 psql 路径调整）：

```powershell
$env:PGPASSWORD = "1234567890"
& "D:\java-sofeware\PostgreSQL\18\bin\psql.exe" -h 127.0.0.1 -U postgres -d vector_test -f schema/all.sql
```

请在**仓库根目录**执行，以便 `all.sql` 中的 `\ir` 能正确找到同目录分文件。

## 核对

```sql
\dx
\dt
\d+ sys_user
```

应能看到 `vector` 扩展、全部业务表，以及中文列注释。
