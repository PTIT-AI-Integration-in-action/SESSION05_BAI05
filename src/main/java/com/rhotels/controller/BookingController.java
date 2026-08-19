package com.rhotels.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/booking")
public class BookingController {

    private final ChatClient chatClient;

    public BookingController(ChatClient.Builder builder,
                             JdbcChatMemory jdbcChatMemory,
                             ToolCallbackProvider bookingTools,
                             ToolCallbackProvider crmTools) {
        this.chatClient = builder
                .defaultAdvisors(new MessageChatMemoryAdvisor(jdbcChatMemory))
                .defaultTools(bookingTools, crmTools)
                .build();
    }

    @GetMapping("/check")
    public String checkRoom(@RequestParam(required = false) String message,
                            @RequestParam(required = false) String conversationId) {

        // Phòng thủ: cuộc trò chuyện mới -> tự sinh UUID làm Session ID
        String sessionId = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString()
                : conversationId;

        String response = this.chatClient.prompt()
                .advisors(a -> a.param("chat_memory_conversation_id", sessionId))
                .user(message)
                .call()
                .content();

        return "conversationId: " + sessionId + "\n" + response;
    }
}