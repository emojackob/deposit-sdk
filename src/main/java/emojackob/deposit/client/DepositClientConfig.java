package emojackob.deposit.client;

import java.security.PrivateKey;

/** deposit-notify 对接配置。 */
public final class DepositClientConfig {
    private final String baseUrl;
    private final String apiKey;
    private final PrivateKey privateKey;
    private final String project;
    private final int recvWindow;
    private final int connectTimeoutSeconds;
    private final int requestTimeoutSeconds;

    private DepositClientConfig(Builder b) {
        this.baseUrl = b.baseUrl;
        this.apiKey = b.apiKey;
        this.privateKey = b.privateKey;
        this.project = b.project;
        this.recvWindow = b.recvWindow;
        this.connectTimeoutSeconds = b.connectTimeoutSeconds;
        this.requestTimeoutSeconds = b.requestTimeoutSeconds;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    public String project() {
        return project;
    }

    public int recvWindow() {
        return recvWindow;
    }

    public int connectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public int requestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String baseUrl;
        private String apiKey;
        private PrivateKey privateKey;
        private String project;
        private int recvWindow = 30_000;
        private int connectTimeoutSeconds = 10;
        private int requestTimeoutSeconds = 30;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder privateKey(PrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        public Builder project(String project) {
            this.project = project;
            return this;
        }

        public Builder recvWindow(int recvWindow) {
            this.recvWindow = recvWindow;
            return this;
        }

        public Builder connectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            return this;
        }

        public Builder requestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
            return this;
        }

        public DepositClientConfig build() {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl is required");
            }
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("apiKey is required");
            }
            if (privateKey == null) {
                throw new IllegalArgumentException("privateKey is required");
            }
            return new DepositClientConfig(this);
        }
    }
}
