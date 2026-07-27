package org.example.traveljava.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ChatRequest {

    private String model;
    private List<ChatMessage> messages;
    private Boolean stream = false;
    private Double temperature = 0.7;
    @JsonProperty("max_tokens")
    private Integer maxTokens = 3000;

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private List<ChatMessage> messages;
        private Boolean stream = false;
        private Double temperature = 0.7;
        private Integer maxTokens = 3000;

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

        public ChatRequest build() {
            return new ChatRequest(model, messages, stream, temperature, maxTokens);
        }
    }
}
