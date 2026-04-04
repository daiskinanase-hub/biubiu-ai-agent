-- ============================================================
-- RAG ETL Pipeline 数据库初始化脚本
-- 用途：在新服务器上初始化 PostgreSQL + pgvector 环境
-- 说明：项目中使用了 spring-ai-pgvector-store:1.1.2，
--       但 application.yml 中 initialize-schema: false，
--       因此必须手动执行以下 DDL。
-- ============================================================

-- 1. 安装必要的数据库扩展
-- vector: pgvector 核心扩展，支持向量类型和相似度运算符
-- hstore: Spring AI PgVectorStore 初始化时会检查该扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;

-- 2. 创建向量存储主表
-- 字段说明：
--   id        : 主键，UUID 自动生成
--   content   : 文本块内容
--   metadata  : JSONB 元数据（存储 document_id, chunk_index, label, keywords 等）
--   embedding : 1536 维向量（与 application.yml 中 dimensions: 1536 对应）
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    content text,
    metadata jsonb,
    embedding vector(1536)
);

-- 3. 创建索引

-- 3.1 HNSW 向量相似度索引（COSINE_DISTANCE）
-- 对应 application.yml 配置：index-type: HNSW, distance-type: COSINE_DISTANCE
-- 加速 embedding <=> ?::vector 的 ORDER BY / WHERE 查询
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_hnsw
    ON vector_store USING HNSW (embedding vector_cosine_ops);

-- 3.2 metadata GIN 索引
-- 加速 jsonb_exists_any(metadata->'keywords', ...) 等 jsonb 内部操作
CREATE INDEX IF NOT EXISTS idx_vector_metadata_gin
    ON vector_store USING gin (metadata);

-- 3.3 document_id 精确过滤 B-Tree 索引
-- 业务代码中高频使用 metadata->>'document_id' = ? 做精确匹配和 COUNT(*)
-- 普通 GIN 索引无法加速 ->> 操作符的等值比较，需要单独建表达式索引
CREATE INDEX IF NOT EXISTS idx_vector_store_document_id
    ON vector_store ((metadata->>'document_id'));

-- ============================================================
-- 业务代码中涉及的核心 SQL（供参考，无需手动执行）：
--
-- 1) 无关键词向量检索：
--    SELECT id, content, metadata, embedding,
--           embedding <=> ?::vector as distance
--    FROM vector_store
--    WHERE metadata->>'document_id' = ?
--    ORDER BY embedding <=> ?::vector
--    LIMIT ?;
--
-- 2) 带关键词交集过滤的向量检索：
--    SELECT id, content, metadata, embedding,
--           embedding <=> ?::vector as distance
--    FROM vector_store
--    WHERE metadata->>'document_id' = ?
--      AND jsonb_exists_any(metadata->'keywords', ?::text[])
--    ORDER BY embedding <=> ?::vector
--    LIMIT ?;
--
-- 3) 记录总数统计：
--    SELECT COUNT(*) FROM vector_store WHERE metadata->>'document_id' = ?;
-- ============================================================
