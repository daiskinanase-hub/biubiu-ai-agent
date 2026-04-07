package com.example.rag.controller;

import com.example.rag.exception.BusinessException;
import com.example.rag.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GlobalExceptionHandler} 单元测试：验证增强版全局异常处理器的错误响应逻辑。
 * <p>
 * 注意：此测试针对新的异常处理机制
 * </p>
 */
class GlobalExceptionHandlerTest {

    @Nested
    @DisplayName("业务异常处理测试")
    class BusinessExceptionTests {

        @Test
        @DisplayName("业务异常应返回错误码")
        void businessException_returnsErrorCode() {
            BusinessException ex = new BusinessException(ErrorCode.DOCUMENT_PARSE_ERROR, "测试消息");

            Map<String, Object> response = Map.of(
                    "code", ex.getErrorCode().getCodeString(),
                    "message", ex.getMessage()
            );

            assertEquals("ERR_2001", response.get("code"));
            assertEquals("测试消息", response.get("message"));
        }

        @Test
        @DisplayName("错误码应包含正确的消息")
        void errorCode_containsCorrectMessage() {
            BusinessException ex = new BusinessException(ErrorCode.VECTOR_SEARCH_ERROR);

            assertTrue(ex.getErrorCode().getCodeString().startsWith("ERR_"));
            assertNotNull(ex.getErrorCode().getDefaultMessage());
        }
    }

    @Nested
    @DisplayName("错误码枚举测试")
    class ErrorCodeTests {

        @Test
        @DisplayName("所有错误码应有唯一编号")
        void allErrorCodes_haveUniqueNumbers() {
            ErrorCode[] codes = ErrorCode.values();
            long distinctCount = java.util.Arrays.stream(codes)
                    .map(ErrorCode::getCode)
                    .distinct()
                    .count();
            assertEquals(codes.length, distinctCount);
        }

        @Test
        @DisplayName("错误码编号应为正数")
        void errorCodeNumbers_arePositive() {
            for (ErrorCode code : ErrorCode.values()) {
                assertTrue(code.getCode() > 0);
            }
        }

        @Test
        @DisplayName("错误码格式应为ERR_XXXX")
        void errorCodeFormat_correct() {
            for (ErrorCode code : ErrorCode.values()) {
                assertTrue(code.getCodeString().matches("ERR_\\d{4}"));
            }
        }

        @Test
        @DisplayName("错误码分类正确")
        void errorCodeCategories_correct() {
            // 系统错误 1xxx
            assertTrue(ErrorCode.SYSTEM_ERROR.getCode() >= 1000 && ErrorCode.SYSTEM_ERROR.getCode() < 2000);

            // 文档错误 2xxx
            assertTrue(ErrorCode.DOCUMENT_PARSE_ERROR.getCode() >= 2000 && ErrorCode.DOCUMENT_PARSE_ERROR.getCode() < 3000);

            // RAG错误 3xxx
            assertTrue(ErrorCode.VECTOR_SEARCH_ERROR.getCode() >= 3000 && ErrorCode.VECTOR_SEARCH_ERROR.getCode() < 4000);

            // 外部服务错误 4xxx
            assertTrue(ErrorCode.DASHSCOPE_ERROR.getCode() >= 4000 && ErrorCode.DASHSCOPE_ERROR.getCode() < 5000);

            // 验证错误 5xxx
            assertTrue(ErrorCode.MISSING_PARAMETER.getCode() >= 5000 && ErrorCode.MISSING_PARAMETER.getCode() < 6000);
        }
    }

    @Nested
    @DisplayName("异常消息测试")
    class ExceptionMessageTests {

        @Test
        @DisplayName("自定义消息应覆盖默认消息")
        void customMessage_overridesDefault() {
            BusinessException ex = new BusinessException(ErrorCode.DOCUMENT_PARSE_ERROR, "自定义错误");
            assertEquals("自定义错误", ex.getMessage());
        }

        @Test
        @DisplayName("无自定义消息时应返回默认消息")
        void defaultMessage_whenNoCustom() {
            BusinessException ex = new BusinessException(ErrorCode.DOCUMENT_PARSE_ERROR);
            assertEquals(ErrorCode.DOCUMENT_PARSE_ERROR.getDefaultMessage(), ex.getMessage());
        }
    }
}
