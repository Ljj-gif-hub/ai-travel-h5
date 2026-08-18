package org.example.traveljava.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class ChatRequest {

    private String model;
    private List<ChatMessage> messages;
    private Boolean stream = false;
    private Double temperature = 0.7;
    @JsonProperty("max_tokens")
    private Integer maxTokens = 3000;
    /**
     * DeepSeek V4 系列关闭思考模式的扩展参数：{"type":"disabled"}。
     * 非 DeepSeek 供应商为 null，序列化时省略，避免影响其它兼容接口。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> thinking;

    public ChatRequest() {
    }

    public ChatRequest(String model, List<ChatMessage> messages, Boolean stream, Double temperature, Integer maxTokens) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Map<String, Object> getThinking() {
        return thinking;
    }

    public void setThinking(Map<String, Object> thinking) {
        this.thinking = thinking;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private List<ChatMessage> messages;
        private Boolean stream = false;
        private Double temperature = 0.7;
        private Integer maxTokens = 3000;
        private Map<String, Object> thinking;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder thinking(Map<String, Object> thinking) {
            this.thinking = thinking;
            return this;
        }

        public ChatRequest build() {
            ChatRequest req = new ChatRequest(model, messages, stream, temperature, maxTokens);
            req.setThinking(thinking);
            return req;
        }
    }
}
