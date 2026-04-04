<template>
  <div class="knowledge-base-container">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">知识库检索</h1>
      <div class="placeholder"></div>
    </div>
    
    <div class="content-wrapper">
      <div class="chat-area">
        <ChatRoom 
          :messages="messages" 
          :connection-status="connectionStatus"
          ai-type="knowledge"
          :show-upload="true"
          :upload-disabled="fileUploaded"
          :uploading="uploading"
          :upload-progress="uploadProgress"
          :uploaded-document-name="uploadedDocumentName"
          @send-message="sendMessage"
          @upload-file="handleUploadFile"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import { uploadEtlFileWithProgress, queryRag } from '../api'

// 设置页面标题和元数据
useHead({
  title: '知识库检索 - BIUBIU~AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: '知识库检索是BIUBIU~AI超级智能体应用平台的智能文档问答助手，支持上传PDF并进行智能检索问答'
    },
    {
      name: 'keywords',
      content: '知识库检索,PDF问答,智能检索,AI问答,文档问答,鱼皮,AI智能体'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const documentId = ref('')
const uploadedDocumentName = ref('')
const connectionStatus = ref('disconnected')
const uploading = ref(false)
const uploadProgress = ref(0)
const fileUploaded = ref(false)

// 生成随机文档ID
const generateDocumentId = () => {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return 'doc_' + Math.random().toString(36).substring(2, 10) + '_' + Date.now()
}

// 添加消息到列表
const addMessage = (content, isUser) => {
  messages.value.push({
    content,
    isUser,
    time: new Date().getTime()
  })
}

// 处理文件上传
const handleUploadFile = async (file) => {
  uploading.value = true
  uploadProgress.value = 0
  try {
    const res = await uploadEtlFileWithProgress(file, documentId.value, (progress) => {
      uploadProgress.value = progress
    })
    console.log('[KnowledgeBase] 上传成功，响应数据:', res.data)
    // 兼容 snake_case (chunks_processed) 和 camelCase (chunksProcessed)
    const chunksProcessed = res.data?.chunksProcessed ?? res.data?.chunks_processped ?? 0
    const documentName = res.data?.documentName ?? res.data?.document_name ?? ''
    if (res.data?.status === 'success' || res.data?.status === 'SUCCESS') {
      uploadedDocumentName.value = documentName || file.name
      fileUploaded.value = true
      addMessage(`✅ 文件「${uploadedDocumentName.value}」上传成功！共处理 ${chunksProcessed} 个切片，现在可以开始提问了。`, false)
    } else {
      addMessage('文件上传失败，请重试。', false)
    }
  } catch (error) {
    console.error('Upload error:', error)
    addMessage('文件上传失败：' + (error.message || '网络错误'), false)
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

// 发送消息
const sendMessage = async (message) => {
  addMessage(message, true)

  connectionStatus.value = 'connecting'

  // 创建一个空的AI回复消息
  const aiMessageIndex = messages.value.length
  addMessage('', false)

  try {
    const res = await queryRag(documentId.value, message)
    console.log('[KnowledgeBase] 查询成功，响应数据:', res.data)
    if (res.data) {
      if (res.data.error) {
        // 有错误
        if (aiMessageIndex < messages.value.length) {
          messages.value[aiMessageIndex].content = '查询失败：' + res.data.error
        }
      } else {
        // 正常回答
        if (aiMessageIndex < messages.value.length) {
          messages.value[aiMessageIndex].content = res.data.answer || '抱歉，未能生成回答'
        }
      }
    } else {
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content = '查询失败，请重试'
      }
    }
  } catch (error) {
    console.error('[KnowledgeBase] 查询失败:', error)
    if (aiMessageIndex < messages.value.length) {
      messages.value[aiMessageIndex].content = '查询失败：' + (error.message || '网络错误')
    }
  } finally {
    connectionStatus.value = 'disconnected'
  }
}

// 返回主页
const goBack = () => {
  router.push('/')
}

// 页面加载时添加欢迎消息
onMounted(() => {
  // 生成文档ID
  documentId.value = generateDocumentId()

  // 添加欢迎消息
  addMessage('欢迎来到知识库检索，请先上传一个 PDF 文件（≤5MB），然后向我提问。', false)
})

// 组件销毁前关闭资源
onBeforeUnmount(() => {
  // 无需清理（SSE 连接已移除）
})
</script>

<style scoped>
.knowledge-base-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #fffbf5;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background-color: #ff9800;
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-button {
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: opacity 0.2s;
}

.back-button:hover {
  opacity: 0.8;
}

.back-button:before {
  content: '←';
  margin-right: 8px;
}

.title {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
}

.placeholder {
  width: 1px;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  max-height: calc(100vh - 56px);
}

.chat-area {
  flex: 1;
  padding: 16px;
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 500px;
  max-height: calc(100vh - 56px - 32px);
}

/* 响应式样式 */
@media (max-width: 768px) {
  .header {
    padding: 12px 16px;
  }
  
  .title {
    font-size: 18px;
  }
  
  .chat-area {
    padding: 12px;
  }
}

@media (max-width: 480px) {
  .header {
    padding: 10px 12px;
  }
  
  .back-button {
    font-size: 14px;
  }
  
  .title {
    font-size: 16px;
  }
  
  .chat-area {
    padding: 8px;
  }
}
</style>
