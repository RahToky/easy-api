package omm.mtk.easy.api.core;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import omm.mtk.easy.api.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author mahatoky rasolonirina
 */
public class EasyApplicationContext {
    private final Map<String, Object> beans = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> beansByType = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> beansByInterface = new ConcurrentHashMap<>();
    private final Vertx vertx;
    private final JsonObject properties;
    private final Set<Class<?>> scannedClasses;
    private final EasyWebRouter webRouter;
    private final ConfigurationPropertiesBinder propertiesBinder;
    
    public EasyApplicationContext(Vertx vertx, String... basePackages) {
        this.vertx = vertx;
        this.properties = loadProperties();
        this.scannedClasses = scanPackages(basePackages);
        this.propertiesBinder = new ConfigurationPropertiesBinder(properties);
        
        System.out.println("🔧 Initializing EasyApi context...");
        System.out.println("📦 Scanned classes: " + scannedClasses.size());
        scannedClasses.forEach(clazz -> System.out.println("   📍 " + clazz.getName()));
        
        initializeBeans();
        buildInterfaceMapping();
        injectDependencies();
        bindConfigurationProperties();
        this.webRouter = new EasyWebRouter(vertx, this);
        
        System.out.println("✅ EasyApi context initialized with " + beans.size() + " beans");
    }
    
    private JsonObject loadProperties() {
        JsonObject props = new JsonObject();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                Properties javaProps = new Properties();
                javaProps.load(is);
                javaProps.forEach((k, v) -> props.put((String) k, v));
                System.out.println("✅ Loaded application.properties");
            } else {
                System.out.println("ℹ️ No application.properties found, using defaults");
            }
        } catch (IOException e) {
            System.out.println("ℹ️ No application.properties found, using defaults");
        }
        return props;
    }
    
    private Set<Class<?>> scanPackages(String... basePackages) {
        Set<Class<?>> allClasses = new HashSet<>();
        for (String basePackage : basePackages) {
            try {
                Set<Class<?>> packageClasses = ClassScanner.findClasses(basePackage);
                allClasses.addAll(packageClasses);
            } catch (IOException e) {
                System.err.println("❌ Failed to scan package: " + basePackage + " - " + e.getMessage());
                // Continuer avec les autres packages
            }
        }
        return allClasses;
    }
    
    private void initializeBeans() {
        System.out.println("🔧 Initializing beans...");
        
        scannedClasses.stream()
                .filter(clazz -> clazz.isAnnotationPresent(Configuration.class))
                .forEach(this::createConfigurationBean);
        
        // Ensuite les autres composants - EXCLURE LES ANNOTATIONS
        scannedClasses.stream()
                .filter(clazz -> isComponent(clazz) &&
                        !clazz.isAnnotationPresent(Configuration.class) &&
                        !clazz.isAnnotation()) // ✅ EXCLURE LES ANNOTATIONS
                .forEach(this::createBean);
    }
    
    private void buildInterfaceMapping() {
        System.out.println("🔗 Building interface mapping...");
        
        Map<Class<?>, List<Object>> interfaceImplementations = new HashMap<>();
        
        // Collecter toutes les implémentations d'interfaces
        for (Object beanInstance : beansByType.values()) {
            Class<?> beanClass = beanInstance.getClass();
            
            for (Class<?> interfaceClass : beanClass.getInterfaces()) {
                interfaceImplementations
                        .computeIfAbsent(interfaceClass, k -> new ArrayList<>(2)) // Optimisé pour 1-2 implémentations
                        .add(beanInstance);
                
                System.out.println("  📍 " + interfaceClass.getSimpleName() + " <- " + beanClass.getSimpleName());
            }
        }
        
        // Résoudre le mapping
        for (Map.Entry<Class<?>, List<Object>> entry : interfaceImplementations.entrySet()) {
            Class<?> interfaceClass = entry.getKey();
            List<Object> implementations = entry.getValue();
            
            if (implementations.size() == 1) {
                beansByInterface.put(interfaceClass, implementations.get(0));
                System.out.println("  ✅ Single implementation: " + interfaceClass.getSimpleName());
            } else {
                Object primaryImpl = findPrimaryImplementation(implementations);
                if (primaryImpl != null) {
                    beansByInterface.put(interfaceClass, primaryImpl);
                    System.out.println("  🏆 Using @Primary: " + interfaceClass.getSimpleName());
                } else {
                    String implNames = implementations.stream()
                            .map(impl -> impl.getClass().getSimpleName())
                            .collect(Collectors.joining(", "));
                    throw new RuntimeException(
                            "Multiple implementations for " + interfaceClass.getName() +
                                    ": " + implNames + ". Use @Primary or @Qualifier."
                    );
                }
            }
        }
        
        System.out.println("✅ Interface mapping: " + beansByInterface.size() + " interfaces");
    }
    
    private Object findPrimaryImplementation(List<Object> implementations) {
        return implementations.stream()
                .filter(impl -> isPrimary(impl.getClass()))
                .findFirst()
                .orElse(null);
    }
    
    private boolean isPrimary(Class<?> clazz) {
        return clazz.isAnnotationPresent(Primary.class);
    }
    
    private boolean isComponent(Class<?> clazz) {
        return clazz.isAnnotationPresent(Component.class) ||
                clazz.isAnnotationPresent(Service.class) ||
                clazz.isAnnotationPresent(Repository.class) ||
                clazz.isAnnotationPresent(RestController.class) ||
                clazz.isAnnotationPresent(ControllerAdvice.class) ||
                clazz.isAnnotationPresent(ConfigurationProperties.class);
    }
    
    private void createConfigurationBean(Class<?> clazz) {
        try {
            Object instance = createBeanInstance(clazz);
            registerBean(clazz, instance);
            
            // Méthodes @Bean
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Component.class)) {
                    Object bean = method.invoke(instance);
                    if (bean != null) {
                        String beanName = getMethodBeanName(method);
                        registerBean(beanName, bean.getClass(), bean);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create configuration: " + clazz.getName(), e);
        }
    }
    
    private void createBean(Class<?> clazz) {
        try {
            Object instance = createBeanInstance(clazz);
            registerBean(clazz, instance);
        } catch (Exception e) {
            throw  new RuntimeException("Failed to create bean: " + clazz.getName(), e);
        }
    }
    
    private void registerBean(Class<?> clazz, Object instance) {
        String beanName = getBeanName(clazz);
        registerBean(beanName, clazz, instance);
    }
    
    private void registerBean(String beanName, Class<?> clazz, Object instance) {
        beans.put(beanName, instance);
        beansByType.put(clazz, instance);
        System.out.println("  ✅ Registered: " + clazz.getSimpleName() + " as '" + beanName + "'");
    }
    
    private Object createBeanInstance(Class<?> clazz) throws Exception {
        Constructor<?>[] constructors = clazz.getConstructors();
        
        Constructor<?> autowiredConstructor = Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(Autowired.class))
                .findFirst()
                .orElse(null);
        
        if (autowiredConstructor != null) {
            return autowiredConstructor.newInstance(resolveConstructorParameters(autowiredConstructor));
        }
        
        // Constructeur avec paramètres
        Constructor<?> paramConstructor = Arrays.stream(constructors)
                .filter(c -> c.getParameterCount() > 0)
                .findFirst()
                .orElse(null);
        
        if (paramConstructor != null) {
            try {
                return paramConstructor.newInstance(resolveConstructorParameters(paramConstructor));
            } catch (Exception e) {
            }
        }
        
        return clazz.getDeclaredConstructor().newInstance();
    }
    
    private Object[] resolveConstructorParameters(Constructor<?> constructor) {
        return Arrays.stream(constructor.getParameters())
                .map(param -> {
                    if (param.isAnnotationPresent(Value.class)) {
                        return resolveValue(param.getAnnotation(Value.class).value(), param.getType());
                    }
                    
                    String qualifier = param.isAnnotationPresent(Qualifier.class) ?
                            param.getAnnotation(Qualifier.class).value() : null;
                    
                    Object dependency = findBeanForInjection(param.getType(), qualifier);
                    if (dependency == null) {
                        throw new RuntimeException("No bean found for: " + param.getType().getName());
                    }
                    return dependency;
                })
                .toArray();
    }
    
    private Object findBeanForInjection(Class<?> type, String qualifierName) {
        // 1. Qualifier
        if (qualifierName != null && !qualifierName.isEmpty()) {
            Object bean = beans.get(qualifierName);
            if (bean != null && type.isAssignableFrom(bean.getClass())) {
                return bean;
            }
        }
        
        // 2. Type exact
        Object bean = beansByType.get(type);
        if (bean != null) return bean;
        
        // 3. Interface
        bean = beansByInterface.get(type);
        if (bean != null) return bean;
        
        // 4. Assignabilité
        return beansByType.entrySet().stream()
                .filter(entry -> type.isAssignableFrom(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
    
    private void injectDependencies() {
        System.out.println("💉 Injecting dependencies...");
        beans.values().forEach(bean -> {
            injectFieldDependencies(bean);
            injectMethodDependencies(bean);
        });
    }
    
    private void injectFieldDependencies(Object bean) {
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Autowired.class)) {
                injectField(bean, field);
            } else if (field.isAnnotationPresent(Value.class)) {
                injectValue(bean, field);
            }
        }
    }
    
    private void injectField(Object bean, Field field) {
        field.setAccessible(true);
        try {
            String qualifier = field.isAnnotationPresent(Qualifier.class) ?
                    field.getAnnotation(Qualifier.class).value() : null;
            
            Object dependency = findBeanForInjection(field.getType(), qualifier);
            if (dependency == null) {
                throw new RuntimeException("No bean found for field: " + field.getType().getName());
            }
            
            field.set(bean, dependency);
            System.out.println("  💉 Injected " + field.getType().getSimpleName() +
                    " into " + bean.getClass().getSimpleName() + "." + field.getName());
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to inject field: " + field.getName(), e);
        }
    }
    
    private void injectValue(Object bean, Field field) {
        field.setAccessible(true);
        try {
            Object value = resolveValue(field.getAnnotation(Value.class).value(), field.getType());
            field.set(bean, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to inject value: " + field.getName(), e);
        }
    }
    
    private void injectMethodDependencies(Object bean) {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Autowired.class)) {
                method.setAccessible(true);
                try {
                    Object[] args = Arrays.stream(method.getParameters())
                            .map(param -> {
                                if (param.isAnnotationPresent(Value.class)) {
                                    return resolveValue(param.getAnnotation(Value.class).value(), param.getType());
                                }
                                
                                String qualifier = param.isAnnotationPresent(Qualifier.class) ?
                                        param.getAnnotation(Qualifier.class).value() : null;
                                
                                Object dependency = findBeanForInjection(param.getType(), qualifier);
                                if (dependency == null) {
                                    throw new RuntimeException("No bean found for method param: " + param.getType().getName());
                                }
                                return dependency;
                            })
                            .toArray();
                    
                    method.invoke(bean, args);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to inject method: " + method.getName(), e);
                }
            }
        }
    }
    
    private void bindConfigurationProperties() {
        beans.values().stream()
                .filter(bean -> bean.getClass().isAnnotationPresent(ConfigurationProperties.class))
                .forEach(bean -> {
                    ConfigurationProperties annotation = bean.getClass().getAnnotation(ConfigurationProperties.class);
                    String prefix = annotation.prefix().isEmpty() ? annotation.value() : annotation.prefix();
                    propertiesBinder.bind(bean, prefix);
                    System.out.println("  ⚙️  Bound configuration: " + bean.getClass().getSimpleName() + " with prefix: " + prefix);
                });
    }
    
    private Object resolveValue(String expression, Class<?> targetType) {
        String key = expression.replace("${", "").replace("}", "");
        String defaultValue = "";
        
        if (key.contains(":")) {
            String[] parts = key.split(":");
            key = parts[0];
            defaultValue = parts[1];
        }
        
        String value = properties.getString(key, System.getProperty(key, defaultValue));
        
        // Conversion des types
        if (targetType == String.class) return value;
        if (targetType == int.class || targetType == Integer.class)
            return value.isEmpty() ? 0 : Integer.parseInt(value);
        if (targetType == boolean.class || targetType == Boolean.class)
            return value.isEmpty() ? false : Boolean.parseBoolean(value);
        if (targetType == long.class || targetType == Long.class)
            return value.isEmpty() ? 0L : Long.parseLong(value);
        if (targetType == double.class || targetType == Double.class)
            return value.isEmpty() ? 0.0 : Double.parseDouble(value);
        if (targetType == float.class || targetType == Float.class)
            return value.isEmpty() ? 0.0f : Float.parseFloat(value);
        if (targetType.isEnum())
            return Enum.valueOf((Class<Enum>) targetType, value.toUpperCase());
        
        throw new RuntimeException("Unsupported value type: " + targetType.getName());
    }
    
    private String getBeanName(Class<?> clazz) {
        String name = "";
        
        if (clazz.isAnnotationPresent(Component.class)) {
            name = clazz.getAnnotation(Component.class).value();
        } else if (clazz.isAnnotationPresent(Service.class)) {
            name = clazz.getAnnotation(Service.class).value();
        } else if (clazz.isAnnotationPresent(Repository.class)) {
            name = clazz.getAnnotation(Repository.class).value();
        } else if (clazz.isAnnotationPresent(RestController.class)) {
            name = clazz.getAnnotation(RestController.class).value();
        } else if (clazz.isAnnotationPresent(Configuration.class)) {
            name = clazz.getAnnotation(Configuration.class).value();
        } else if (clazz.isAnnotationPresent(ControllerAdvice.class)) {
            name = clazz.getAnnotation(ControllerAdvice.class).value();
        } else if (clazz.isAnnotationPresent(ConfigurationProperties.class)) {
            name = clazz.getAnnotation(ConfigurationProperties.class).value();
        }
        
        return name.isEmpty() ?
                clazz.getSimpleName().substring(0, 1).toLowerCase() + clazz.getSimpleName().substring(1)
                : name;
    }
    
    private String getMethodBeanName(Method method) {
        String name = method.getAnnotation(Component.class).value();
        return name.isEmpty() ? method.getName() : name;
    }
    
    public void startServer(int port) {
        vertx.createHttpServer()
                .requestHandler(webRouter.getRouter())
                .listen(port, result -> {
                    if (result.succeeded()) {
                        System.out.println("🚀 EasyApi server started on port " + port);
                    } else {
                        System.err.println("❌ Failed to start server: " + result.cause().getMessage());
                    }
                });
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        return (T) beansByType.get(clazz);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        return (T) beans.get(name);
    }
    
    public Vertx getVertx() {
        return vertx;
    }
    
    public Set<Class<?>> getScannedClasses() {
        return Collections.unmodifiableSet(scannedClasses);
    }
    
    public EasyWebRouter getWebRouter() {
        return webRouter;
    }
}