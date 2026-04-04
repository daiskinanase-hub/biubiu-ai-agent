import axios from 'axios'

// AI 服务 API 基础 URL（AI恋爱大师、AI超级智能体）-> 8123 端口
const AI_API_BASE_URL = '/api'

// 知识库服务 API 基础 URL -> 10201 端口（通过 /kb-api 代理）
const KB_API_BASE_URL = '/kb-api'

// 创建 AI 服务的 axios 实例
const aiRequest = axios.create({
  baseURL: AI_API_BASE_URL,
  timeout: 60000
})

// 创建知识库服务的 axios 实例
const kbRequest = axios.create({
  baseURL: KB_API_BASE_URL,
  timeout: 1800000   // 30分钟，防止慢查询时被浏览器提前断开
})

// 封装 SSE 连接（GET 方式）- 用于 AI 服务
export const connectSSE = (url, params, onMessage, onError) => {
  // 构建带参数的 URL
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  
  const fullUrl = `${AI_API_BASE_URL}${url}?${queryString}`
  
  // 创建 EventSource
  const eventSource = new EventSource(fullUrl)
  
  eventSource.onmessage = event => {
    let data = event.data
    
    // 检查是否是特殊标记
    if (data === '[DONE]') {
      if (onMessage) onMessage('[DONE]')
    } else {
      // 处理普通消息
      if (onMessage) onMessage(data)
    }
  }
  
  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }
  
  // 返回 eventSource 实例，以便后续可以关闭连接
  return eventSource
}

// AI 恋爱大师聊天 - 使用 AI 服务 (8123 端口)
export const chatWithLoveApp = (message, chatId) => {
  return connectSSE('/ai/love_app/chat/sse', { message, chatId })
}

// AI 超级智能体聊天 - 使用 AI 服务 (8123 端口)
export const chatWithManus = (message) => {
  return connectSSE('/ai/manus/chat', { message })
}

// 知识库 ETL 文件上传（支持进度回调）- 使用知识库服务 (10201 端口)
export const uploadEtlFileWithProgress = (file, documentId, onProgress) => {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    
    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable && onProgress) {
        const percentComplete = Math.round((e.loaded / e.total) * 100)
        onProgress(percentComplete)
      }
    })
    
    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const response = JSON.parse(xhr.responseText)
          console.log('[Upload] 后端响应:', response)
          resolve({ data: response })
        } catch (e) {
          console.error('[Upload] 解析 JSON 失败:', xhr.responseText)
          reject(new Error('Invalid JSON response'))
        }
      } else {
        console.error('[Upload] HTTP 错误:', xhr.status, xhr.responseText)
        reject(new Error(`Upload failed with status ${xhr.status}`))
      }
    })
    
    xhr.addEventListener('error', () => {
      reject(new Error('Network error'))
    })
    
    xhr.addEventListener('abort', () => {
      reject(new Error('Upload aborted'))
    })
    
    const formData = new FormData()
    formData.append('file', file)
    formData.append('documentId', documentId)
    
    xhr.open('POST', `${KB_API_BASE_URL}/v1/etl/process`)
    xhr.timeout = 300000
    xhr.send(formData)
  })
}

// 知识库 ETL 文件上传（保持向后兼容）- 使用知识库服务 (10201 端口)
export const uploadEtlFile = (file, documentId) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('documentId', documentId)
  return kbRequest.post('/v1/etl/process', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 知识库 RAG 查询（非流式）- 使用知识库服务 (10201 端口)
// 返回 Promise<RagQueryResponse>，其中 RagQueryResponse = { answer: string, docCount: number, error: string|null }
// 使用较长的超时时间（30分钟），因为 RAG 处理涉及向量检索、重排、LLM生成，可能耗时较长
export const queryRag = (documentId, prompt) => {
  return kbRequest.post('/v1/rag/query', { documentId, prompt }, { timeout: 1800000 })
}

export default {
  chatWithLoveApp,
  chatWithManus,
  uploadEtlFile,
  uploadEtlFileWithProgress,
  queryRag
}
