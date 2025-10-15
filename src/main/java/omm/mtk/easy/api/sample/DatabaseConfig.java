package omm.mtk.easy.api.sample;

import omm.mtk.easy.api.annotation.ConfigurationProperties;

import java.util.List;

/**
 * Sample to show how to use this annotation
 * @author mahatoky rasolonirina
 */
class DatabaseConfig {
    private String url;
    private String username;
    private String password;
    private int poolSize;
    private long connectionTimeout;
    private Amount amount;
    private RetryConfig retry;
    private List<String> allowedHosts;
    
    // Classe imbriquée pour les montants
    public static class Amount {
        private int min;
        private int max;
        private int defaultValue;
        
        // Getters et setters
        public int getMin() { return min; }
        public void setMin(int min) { this.min = min; }
        
        public int getMax() { return max; }
        public void setMax(int max) { this.max = max; }
        
        public int getDefaultValue() { return defaultValue; }
        public void setDefaultValue(int defaultValue) { this.defaultValue = defaultValue; }
        
        @Override
        public String toString() {
            return String.format("Amount{min=%d, max=%d, defaultValue=%d}", min, max, defaultValue);
        }
    }
    
    // Classe imbriquée pour les retry
    public static class RetryConfig {
        private int maxAttempts;
        private long delay;
        private boolean enabled;
        
        // Getters et setters
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        
        public long getDelay() { return delay; }
        public void setDelay(long delay) { this.delay = delay; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        @Override
        public String toString() {
            return String.format("RetryConfig{maxAttempts=%d, delay=%d, enabled=%s}",
                    maxAttempts, delay, enabled);
        }
    }
    
    // Getters et setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
    
    public long getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(long connectionTimeout) { this.connectionTimeout = connectionTimeout; }
    
    public Amount getAmount() { return amount; }
    public void setAmount(Amount amount) { this.amount = amount; }
    
    public RetryConfig getRetry() { return retry; }
    public void setRetry(RetryConfig retry) { this.retry = retry; }
    
    public List<String> getAllowedHosts() { return allowedHosts; }
    public void setAllowedHosts(List<String> allowedHosts) { this.allowedHosts = allowedHosts; }
    
    @Override
    public String toString() {
        return String.format(
                "DatabaseConfig{url='%s', username='%s', poolSize=%d, connectionTimeout=%d, amount=%s, retry=%s, allowedHosts=%s}",
                url, username, poolSize, connectionTimeout, amount, retry, allowedHosts
        );
    }
}
