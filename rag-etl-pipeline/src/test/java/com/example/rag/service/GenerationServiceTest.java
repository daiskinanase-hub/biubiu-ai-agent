package com.example.rag.service;

import com.example.rag.dto.SseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SseEvent} 单元测试：验证SSE事件数据类的构造和特性。
 */
class SseEventTest {

    @Nested
    @DisplayName("SseEvent构造测试")
    class ConstructorTests {

        @Test
        @DisplayName("正常构造应包含所有字段")
        void constructor_containsAllFields() {
            SseEvent event = new SseEvent("chunk", "内容片段");

            assertEquals("chunk", event.type());
            assertEquals("内容片段", event.content());
        }

        @Test
        @DisplayName("done类型事件content可为null")
        void doneType_contentCanBeNull() {
            SseEvent event = new SseEvent("done", null);

            assertEquals("done", event.type());
            assertNull(event.content());
        }

        @Test
        @DisplayName("error类型事件应包含错误信息")
        void errorType_containsErrorMessage() {
            SseEvent event = new SseEvent("error", "网络连接失败");

            assertEquals("error", event.type());
            assertEquals("网络连接失败", event.content());
        }
    }

    @Nested
    @DisplayName("equals和hashCode测试")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("相同字段的对象应相等")
        void sameFields_equals() {
            SseEvent e1 = new SseEvent("chunk", "内容");
            SseEvent e2 = new SseEvent("chunk", "内容");

            assertEquals(e1, e2);
            assertEquals(e1.hashCode(), e2.hashCode());
        }

        @Test
        @DisplayName("不同类型的对象应不相等")
        void differentTypes_notEquals() {
            SseEvent e1 = new SseEvent("chunk", "内容");
            SseEvent e2 = new SseEvent("done", "内容");

            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("不同内容的对象应不相等")
        void differentContent_notEquals() {
            SseEvent e1 = new SseEvent("chunk", "内容1");
            SseEvent e2 = new SseEvent("chunk", "内容2");

            assertNotEquals(e1, e2);
        }
    }

    @Nested
    @DisplayName("toString测试")
    class ToStringTests {

        @Test
        @DisplayName("toString应包含字段信息")
        void toString_containsFields() {
            SseEvent event = new SseEvent("chunk", "测试内容");
            String str = event.toString();

            assertTrue(str.contains("chunk") || str.contains("type"));
            assertTrue(str.contains("测试内容") || str.contains("content"));
        }
    }
}
