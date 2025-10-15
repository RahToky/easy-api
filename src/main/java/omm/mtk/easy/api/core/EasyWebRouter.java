package omm.mtk.easy.api.core;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import omm.mtk.easy.api.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public class EasyWebRouter {
    private final Router router;
    private final EasyApplicationContext context;
    private final Map<Class<?>, Object> controllerAdvices = new HashMap<>();
    
    public EasyWebRouter(io.vertx.core.Vertx vertx, EasyApplicationContext context) {
        this.router = Router.router(vertx);
        this.context = context;
        initializeControllerAdvices();
        setupRoutes();
    }
    
    private void initializeControllerAdvices() {
        for (Class<?> clazz : context.getScannedClasses()) {
            if (clazz.isAnnotationPresent(ControllerAdvice.class)) {
                Object advice = context.getBean(clazz);
                controllerAdvices.put(clazz, advice);
            }
        }
    }
    
    private void setupRoutes() {
        router.route().handler(BodyHandler.create());
        router.route().handler(this::setupCors);
        
        for (Class<?> clazz : context.getScannedClasses()) {
            if (clazz.isAnnotationPresent(RestController.class)) {
                registerController(clazz);
            }
        }
        
        router.route().last().handler(this::handleNotFound);
    }
    
    private void registerController(Class<?> controllerClass) {
        Object controller = context.getBean(controllerClass);
        String basePath = getBasePath(controllerClass);
        
        for (Method method : controllerClass.getDeclaredMethods()) {
            registerMethodRoute(controller, method, basePath);
        }
    }
    
    private void registerMethodRoute(Object controller, Method method, String basePath) {
        String httpMethod = null;
        String path = "";
        
        if (method.isAnnotationPresent(GetMapping.class)) {
            httpMethod = "GET";
            path = method.getAnnotation(GetMapping.class).value();
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            httpMethod = "POST";
            path = method.getAnnotation(PostMapping.class).value();
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            httpMethod = "PUT";
            path = method.getAnnotation(PutMapping.class).value();
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            httpMethod = "DELETE";
            path = method.getAnnotation(DeleteMapping.class).value();
        } else if (method.isAnnotationPresent(PatchMapping.class)) {
            httpMethod = "PATCH";
            path = method.getAnnotation(PatchMapping.class).value();
        }
        
        if (httpMethod != null) {
            String fullPath = normalizePath(basePath + normalizePath(path));
            String vertxPath = convertSpringPathToVertx(fullPath);
            
            io.vertx.core.http.HttpMethod vertxHttpMethod = convertToVertxHttpMethod(httpMethod);
            
            router.route(vertxHttpMethod, vertxPath)
                    .handler(createHandler(controller, method));
        }
    }
    
    private Handler<RoutingContext> createHandler(Object controller, Method method) {
        return ctx -> {
            try {
                Object[] args = resolveMethodParameters(method, ctx);
                Object result = method.invoke(controller, args);
                handleResponse(result, ctx, method);
            } catch (Exception e) {
                handleException(ctx, e, method);
            }
        };
    }
    
    private Object[] resolveMethodParameters(Method method, RoutingContext ctx) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            args[i] = resolveParameter(parameters[i], ctx);
        }
        
        return args;
    }
    
    private Object resolveParameter(Parameter param, RoutingContext ctx) {
        Class<?> paramType = param.getType();
        
        if (param.isAnnotationPresent(RequestBody.class)) {
            return resolveRequestBody(param, ctx);
        }
        
        if (param.isAnnotationPresent(RequestParam.class)) {
            return resolveRequestParam(param, ctx);
        }
        
        if (param.isAnnotationPresent(PathVariable.class)) {
            return resolvePathVariable(param, ctx);
        }
        
        if (param.isAnnotationPresent(RequestHeader.class)) {
            return resolveRequestHeader(param, ctx);
        }
        
        if (paramType == RoutingContext.class) {
            return ctx;
        }
        
        return getDefaultValue(paramType);
    }
    
    private Object resolveRequestBody(Parameter param, RoutingContext ctx) {
        String body = ctx.getBodyAsString();
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        
        Class<?> paramType = param.getType();
        try {
            if (paramType == String.class) {
                return body;
            }
            return Json.decodeValue(body, paramType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse request body", e);
        }
    }
    
    private Object resolveRequestParam(Parameter param, RoutingContext ctx) {
        RequestParam annotation = param.getAnnotation(RequestParam.class);
        String paramName = annotation.value().isEmpty() ? param.getName() : annotation.value();
        Class<?> paramType = param.getType();
        
        String paramValue = ctx.request().getParam(paramName);
        
        if (paramValue == null) {
            if (annotation.required() && annotation.defaultValue().isEmpty()) {
                throw new RuntimeException("Required parameter '" + paramName + "' is missing");
            }
            return convertStringToType(annotation.defaultValue(), paramType);
        }
        
        return convertStringToType(paramValue, paramType);
    }
    
    private Object resolvePathVariable(Parameter param, RoutingContext ctx) {
        PathVariable annotation = param.getAnnotation(PathVariable.class);
        String pathVarName = annotation.value().isEmpty() ? param.getName() : annotation.value();
        Class<?> paramType = param.getType();
        
        String pathVarValue = ctx.pathParam(pathVarName);
        
        if (pathVarValue == null) {
            throw new RuntimeException("Path variable '" + pathVarName + "' not found");
        }
        
        return convertStringToType(pathVarValue, paramType);
    }
    
    private Object resolveRequestHeader(Parameter param, RoutingContext ctx) {
        RequestHeader annotation = param.getAnnotation(RequestHeader.class);
        String headerName = annotation.value().isEmpty() ? param.getName() : annotation.value();
        String headerValue = ctx.request().getHeader(headerName);
        
        if (headerValue == null) {
            return getDefaultValue(param.getType());
        }
        
        return convertStringToType(headerValue, param.getType());
    }
    
    private Object convertStringToType(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(value);
        }
        throw new RuntimeException("Unsupported parameter type: " + targetType.getName());
    }
    
    private Object getDefaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        return null;
    }
    
    private void handleResponse(Object result, RoutingContext ctx, Method method) {
        if (result == null) {
            ctx.response().setStatusCode(204).end();
            return;
        }
        
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
            ctx.response().setStatusCode(responseEntity.getStatus());
            responseEntity.getHeaders().forEach(ctx.response()::putHeader);
            
            if (responseEntity.getBody() != null) {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(Json.encodePrettily(responseEntity.getBody()));
            } else {
                ctx.response().end();
            }
            return;
        }
        
        boolean isResponseBody = method.isAnnotationPresent(ResponseBody.class) ||
                method.getDeclaringClass().isAnnotationPresent(ResponseBody.class);
        
        if (isResponseBody || !isSimpleType(result)) {
            ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(Json.encodePrettily(result));
        } else {
            ctx.response()
                    .putHeader("content-type", "text/plain")
                    .end(result.toString());
        }
    }
    
    private void handleException(RoutingContext ctx, Exception e, Method method) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        
        boolean handled = handleExceptionWithControllerAdvice(ctx, cause, method);
        
        if (!handled) {
            handleDefaultException(ctx, cause);
        }
    }
    
    private boolean handleExceptionWithControllerAdvice(RoutingContext ctx, Throwable exception, Method originalMethod) {
        for (Object advice : controllerAdvices.values()) {
            Method exceptionHandler = findExceptionHandler(advice, exception);
            if (exceptionHandler != null) {
                try {
                    Object result = invokeExceptionHandler(advice, exceptionHandler, exception, ctx);
                    if (result != null) {
                        handleResponse(result, ctx, originalMethod);
                        return true;
                    }
                } catch (Exception e) {
                    // Ignore and try next handler
                }
            }
        }
        return false;
    }
    
    private Method findExceptionHandler(Object advice, Throwable exception) {
        for (Method method : advice.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(ExceptionHandler.class)) {
                ExceptionHandler annotation = method.getAnnotation(ExceptionHandler.class);
                Class<?>[] parameterTypes = method.getParameterTypes();
                
                if (annotation.value().length > 0) {
                    for (Class<? extends Throwable> handledException : annotation.value()) {
                        if (handledException.isInstance(exception)) {
                            return method;
                        }
                    }
                } else if (parameterTypes.length > 0 && parameterTypes[0].isInstance(exception)) {
                    return method;
                }
            }
        }
        return null;
    }
    
    private Object invokeExceptionHandler(Object advice, Method handler, Throwable exception, RoutingContext ctx) throws Exception {
        Parameter[] parameters = handler.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Class<?> paramType = parameters[i].getType();
            
            if (paramType.isInstance(exception)) {
                args[i] = exception;
            } else if (paramType == RoutingContext.class) {
                args[i] = ctx;
            } else {
                args[i] = null;
            }
        }
        
        return handler.invoke(advice, args);
    }
    
    private void handleDefaultException(RoutingContext ctx, Throwable exception) {
        int statusCode = 500;
        String errorMessage = exception.getMessage();
        
        if (exception instanceof IllegalArgumentException) {
            statusCode = 400;
        }
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", exception.getClass().getSimpleName());
        errorResponse.put("message", errorMessage);
        errorResponse.put("timestamp", System.currentTimeMillis());
        errorResponse.put("path", ctx.request().path());
        errorResponse.put("status", statusCode);
        
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader("content-type", "application/json")
                .end(Json.encodePrettily(errorResponse));
    }
    
    private void setupCors(RoutingContext ctx) {
        ctx.response()
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS")
                .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        
        if (ctx.request().method() == HttpMethod.OPTIONS) {
            ctx.response().setStatusCode(200).end();
        } else {
            ctx.next();
        }
    }
    
    private void handleNotFound(RoutingContext ctx) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Not Found");
        errorResponse.put("message", "No route found for " + ctx.request().method() + " " + ctx.request().path());
        errorResponse.put("timestamp", System.currentTimeMillis());
        errorResponse.put("status", 404);
        
        ctx.response()
                .setStatusCode(404)
                .putHeader("content-type", "application/json")
                .end(Json.encodePrettily(errorResponse));
    }
    
    private String getBasePath(Class<?> controllerClass) {
        RestController annotation = controllerClass.getAnnotation(RestController.class);
        return normalizePath(annotation.value());
    }
    
    private String normalizePath(String path) {
        if (path.isEmpty()) return "";
        if (!path.startsWith("/")) return "/" + path;
        return path;
    }
    
    private String convertSpringPathToVertx(String path) {
        return path.replaceAll("\\{([^}]+)\\}", ":$1");
    }
    
    private io.vertx.core.http.HttpMethod convertToVertxHttpMethod(String httpMethod) {
        switch (httpMethod.toUpperCase()) {
            case "GET": return HttpMethod.GET;
            case "POST": return HttpMethod.POST;
            case "PUT": return HttpMethod.PUT;
            case "DELETE": return HttpMethod.DELETE;
            case "PATCH": return HttpMethod.PATCH;
            default: return HttpMethod.GET;
        }
    }
    
    private boolean isSimpleType(Object result) {
        return result instanceof String ||
                result instanceof Number ||
                result instanceof Boolean;
    }
    
    public Router getRouter() {
        return router;
    }
}