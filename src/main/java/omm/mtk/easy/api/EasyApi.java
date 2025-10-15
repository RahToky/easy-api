package omm.mtk.easy.api;

import io.vertx.core.Vertx;
import omm.mtk.easy.api.core.EasyApplicationContext;

public class EasyApi {
    
    private static EasyApplicationContext context;
    
    public static void run(Class<?> primarySource, String... args) {
        String basePackage = primarySource.getPackage().getName();
        System.out.println("🎯 Scanning package: " + basePackage);
        
        Vertx vertx = Vertx.vertx();
        
        try {
            context = new EasyApplicationContext(vertx, basePackage);
        } catch (Exception e) {
            System.err.println("❌ Context creation failed: " + e.getMessage());
            e.printStackTrace();
            return;
        }
        
        int port = Integer.parseInt(System.getProperty("server.port", "8080"));
        context.startServer(port);
    }
    
    public static void run(Class<?> primarySource) {
        run(primarySource, new String[]{});
    }
    
    public static EasyApplicationContext getContext() {
        return context;
    }
}