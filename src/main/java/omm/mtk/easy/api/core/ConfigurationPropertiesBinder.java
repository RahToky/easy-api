package omm.mtk.easy.api.core;

import io.vertx.core.json.JsonObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;


/**
 * @author mahatoky rasolonirina
 */
public class ConfigurationPropertiesBinder {
    
    private final JsonObject properties;
    
    public ConfigurationPropertiesBinder(JsonObject properties) {
        this.properties = properties;
    }
    
    public void bind(Object target, String prefix) {
        Class<?> targetClass = target.getClass();
        String effectivePrefix = prefix.isEmpty() ? "" : prefix + ".";
        
        bindFields(target, targetClass, effectivePrefix);
        bindSetters(target, targetClass, effectivePrefix);
    }
    
    private void bindFields(Object target, Class<?> targetClass, String prefix) {
        for (Field field : getAllFields(targetClass)) {
            field.setAccessible(true);
            try {
                // Ne pas binder les champs statiques ou finals
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                        java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                
                Class<?> fieldType = field.getType();
                String propertyName = getPropertyNameForField(field, prefix);
                
                // Si c'est un type complexe (objet), créer une instance et binder récursivement
                if (isComplexType(fieldType) && !fieldType.isEnum()) {
                    Object nestedObject = createInstance(fieldType);
                    if (nestedObject != null) {
                        bind(nestedObject, propertyName);
                        field.set(target, nestedObject);
                    }
                } else {
                    // Type simple, binder directement
                    Object value = getPropertyValue(propertyName, fieldType);
                    if (value != null) {
                        field.set(target, value);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to bind field " + field.getName() + ": " + e.getMessage());
            }
        }
    }
    
    private void bindSetters(Object target, Class<?> targetClass, String prefix) {
        for (Method method : targetClass.getDeclaredMethods()) {
            if (isSetterMethod(method)) {
                try {
                    Class<?> paramType = method.getParameterTypes()[0];
                    String fieldName = getFieldNameFromSetter(method);
                    String propertyName = prefix + camelCaseToKebabCase(fieldName);
                    
                    // Si c'est un type complexe, créer une instance et binder récursivement
                    if (isComplexType(paramType) && !paramType.isEnum()) {
                        Object nestedObject = createInstance(paramType);
                        if (nestedObject != null) {
                            bind(nestedObject, propertyName);
                            method.invoke(target, nestedObject);
                        }
                    } else {
                        // Type simple, binder directement
                        Object value = getPropertyValue(propertyName, paramType);
                        if (value != null) {
                            method.invoke(target, value);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to bind setter " + method.getName() + ": " + e.getMessage());
                }
            }
        }
    }
    
    private String getPropertyNameForField(Field field, String prefix) {
        return prefix + camelCaseToKebabCase(field.getName());
    }
    
    private String getFieldNameFromSetter(Method setter) {
        String methodName = setter.getName();
        if (methodName.startsWith("set") && methodName.length() > 3) {
            String fieldName = methodName.substring(3);
            return fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
        }
        return methodName;
    }
    
    private Object getPropertyValue(String propertyName, Class<?> targetType) {
        // Essayer différentes variantes du nom de propriété
        String[] variants = {
                propertyName, // database.pool-size
                propertyName.replace("-", ""), // database.poolsize
                kebabToCamelCase(propertyName), // database.poolSize
                propertyName.replace(".", "-") // database-pool-size (pour certaines conventions)
        };
        
        for (String variant : variants) {
            Object value = getNestedPropertyValue(variant);
            if (value != null) {
                return convertValue(value.toString(), targetType);
            }
        }
        
        return null;
    }
    
    private Object getNestedPropertyValue(String propertyName) {
        if (propertyName == null || propertyName.isEmpty()) {
            return null;
        }
        
        String[] parts = propertyName.split("\\.");
        JsonObject current = properties;
        
        for (int i = 0; i < parts.length - 1; i++) {
            if (current == null) break;
            current = current.getJsonObject(parts[i]);
        }
        
        if (current != null && parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            Object value = current.getValue(lastPart);
            
            // Si pas trouvé, essayer en camelCase
            if (value == null && lastPart.contains("-")) {
                String camelCasePart = kebabToCamelCase(lastPart);
                value = current.getValue(camelCasePart);
            }
            
            return value;
        }
        
        return null;
    }
    
    private Object convertValue(String value, Class<?> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return getDefaultValue(targetType);
        }
        
        try {
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
            } else if (targetType.isEnum()) {
                return Enum.valueOf((Class<Enum>) targetType, value.toUpperCase());
            } else if (targetType == List.class) {
                return Arrays.asList(value.split("\\s*,\\s*"));
            }
        } catch (Exception e) {
            System.err.println("Failed to convert value '" + value + "' to type " + targetType.getName() + ": " + e.getMessage());
        }
        
        return getDefaultValue(targetType);
    }
    
    private Object getDefaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == boolean.class) return false;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }
    
    private boolean isComplexType(Class<?> type) {
        return !type.isPrimitive() &&
                !type.isEnum() &&
                type != String.class &&
                type != Integer.class &&
                type != Long.class &&
                type != Boolean.class &&
                type != Double.class &&
                type != Float.class &&
                type != List.class;
    }
    
    private Object createInstance(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            System.err.println("Failed to create instance of " + type.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    private String camelCaseToKebabCase(String str) {
        if (str == null || str.isEmpty()) return str;
        
        StringBuilder result = new StringBuilder();
        result.append(Character.toLowerCase(str.charAt(0)));
        
        for (int i = 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('-').append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    private String kebabToCamelCase(String str) {
        if (str == null || str.isEmpty()) return str;
        
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '-') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(c);
                }
            }
        }
        
        return result.toString();
    }
    
    private String kebabCaseToCamelCase(String str) {
        return kebabToCamelCase(str);
    }
    
    private boolean isSetterMethod(Method method) {
        return method.getName().startsWith("set") &&
                method.getParameterCount() == 1 &&
                method.getReturnType() == void.class;
    }
    
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
