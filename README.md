# BIUBIU~AI超级智能体

> 🔥 一个功能强大的 AI 应用平台，包含 AI 恋爱大师、AI 超级智能体、知识库检索三大模块

[![Stars](https://img.shields.io/github/stars/daiskinanase-hub/biubiu-ai-agent?style=social)](https://github.com/daiskinanase-hub/biubiu-ai-agent)
[![License](https://img.shields.io/github/license/daiskinanase-hub/biubiu-ai-agent)](https://github.com/daiskinanase-hub/biubiu-ai-agent)

---

## 功能演示

### 平台首页

<img width="1278" height="1129" alt="image" src="https://github.com/user-attachments/assets/797c9240-5b94-4904-8e00-626ef4efc549" />


三大核心功能模块：
- **AI恋爱大师** - 智能情感顾问，解答恋爱烦恼
- **AI超级智能体** - 全能型AI助手，解决各类专业问题
- **知识库检索** - 上传PDF文档，智能检索问答

---

### AI恋爱大师

<img width="1279" height="1129" alt="image" src="https://github.com/user-attachments/assets/6812b5c1-9600-4f34-9048-eb67083baaf2" />


功能特点：
- 多轮对话，智能情感分析
- 个性化恋爱建议
- 7x24 小时在线服务

---

### AI超级智能体

<img width="1279" height="1128" alt="image" src="https://github.com/user-attachments/assets/e9628017-716b-49fa-9ea3-96e9e72ae3d4" />


功能特点：
- 自主规划与推理
- 工具调用能力
- 专业问题解答

---

### 知识库检索

<img width="1282" height="1138" alt="image" src="https://github.com/user-attachments/assets/a8f2aaa6-0212-418e-be43-c953153f8051" />


功能特点：
- PDF 文档智能解析
- 基于文档的精准问答
- 向量检索 + RAG 技术

---

## 技术栈

### 后端
- Java 21 + Spring Boot 3
- Spring AI
- PostgreSQL + pgVector 向量数据库
- SSE 流式响应

### 前端
- Vue 3 + Vite
- Axios + SSE
- 响应式设计

---

## 快速开始

### 环境要求

- JDK 21+
- Node.js 18+
- PostgreSQL 15+ (需开启 pgvector 扩展)

### 后端部署

```bash
# 进入后端目录
cd yu-ai-agent

# 修改配置文件，填入你的 API Key
vim src/main/resources/application.yml

# 打包
./mvnw clean package -DskipTests

# 运行
java -jar target/yu-ai-agent-0.0.1-SNAPSHOT.jar
```

### 前端部署

```bash
# 进入前端目录
cd yu-ai-agent-frontend

# 安装依赖
npm install

# 开发模式
npm run dev

# 生产构建
npm run build
```

### Docker 部署

```bash
# 构建前端镜像
cd yu-ai-agent-frontend
docker build -t biubiu-frontend .

# 构建后端镜像
cd ../yu-ai-agent
docker build -t biubiu-backend .

# 启动服务（需配合 docker-compose）
docker-compose up -d
```

---

## 配置说明

### 必需配置

| 配置项 | 说明 | 获取方式 |
|--------|------|----------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API Key | [阿里云百炼控制台](https://dashscope.console.aliyun.com/) |
| `DB_PASSWORD` | PostgreSQL 数据库密码 | 本地设置 |

### 可选配置

| 配置项 | 说明 |
|--------|------|
| `BOCHA_API_KEY` | 博查AI API Key（用于增强搜索） |
| `DASHSCOPE_WORKSPACE_ID` | 百炼工作空间ID |

---

## 项目结构

```
biubiu-ai-agent/
├── yu-ai-agent/              # 后端主服务
│   ├── src/main/java/
│   └── src/main/resources/
├── yu-ai-agent-frontend/      # 前端应用
│   ├── src/
│   │   ├── views/            # 页面组件
│   │   │   ├── Home.vue       # 首页
│   │   │   ├── LoveMaster.vue # AI恋爱大师
│   │   │   ├── SuperAgent.vue # AI超级智能体
│   │   │   └── KnowledgeBase.vue # 知识库检索
│   │   └── components/       # 公共组件
│   └── public/
├── rag-etl-pipeline/         # RAG 数据处理服务
│   └── src/main/java/
└── docker-compose.yml         # Docker 编排文件
```

---

## API 接口

### AI 恋爱大师

```
POST /api/ai/love_app/chat/sse
```

### AI 超级智能体

```
POST /api/ai/manus/chat/sse
```

### 知识库

```
POST /api/v1/etl/process     # 文件上传
POST /api/v1/rag/query       # 知识问答
```

---


## License

MIT License - 欢迎 Star 和 Fork！

---

## 联系方式

- GitHub: [daiskinanase-hub](https://github.com/daiskinanase-hub)
- 邮箱: daiskinanase@gmail.com
