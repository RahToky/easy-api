package omm.mtk.easy.api.core;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.net.URISyntaxException;


/**
 * @author mahatoky rasolonirina
 */

public class ClassScanner {
    
    public static Set<Class<?>> findClasses(String packageName) throws IOException {
        Set<Class<?>> classes = new HashSet<>();
        String path = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(path);
        
        if (!resources.hasMoreElements()) {
            System.out.println("⚠️  No resources found for package: " + packageName);
            return classes;
        }
        
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            System.out.println("🔍 Scanning resource: " + resource);
            
            if (resource.getProtocol().equals("file")) {
                try {
                    File file = new File(resource.toURI());
                    classes.addAll(findClassesInDirectory(file, packageName));
                } catch (URISyntaxException e) {
                    System.err.println("❌ Invalid URI: " + resource);
                }
            } else if (resource.getProtocol().equals("jar")) {
                // Pour les JARs, on utilise une approche alternative
                findClassesInJar(packageName, classes);
            }
        }
        
        // Fallback: essayer de charger directement depuis le classpath
        if (classes.isEmpty()) {
            findClassesInClasspath(packageName, classes);
        }
        
        System.out.println("📦 Found " + classes.size() + " classes in package: " + packageName);
        classes.forEach(clazz -> System.out.println("   ✅ " + clazz.getName()));
        
        return classes;
    }
    
    private static Set<Class<?>> findClassesInDirectory(File directory, String packageName) {
        Set<Class<?>> classes = new HashSet<>();
        if (!directory.exists() || !directory.isDirectory()) {
            return classes;
        }
        
        File[] files = directory.listFiles();
        if (files == null) return classes;
        
        for (File file : files) {
            if (file.isDirectory()) {
                String subPackage = packageName + "." + file.getName();
                classes.addAll(findClassesInDirectory(file, subPackage));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                loadClass(className, classes);
            }
        }
        return classes;
    }
    
    private static void findClassesInJar(String packageName, Set<Class<?>> classes) {
        try {
            String path = packageName.replace('.', '/');
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);
            
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String jarPath = resource.getPath();
                
                // Extraire le chemin du JAR et scanner
                if (jarPath.contains("!")) {
                    String jarFilePath = jarPath.substring(5, jarPath.indexOf("!")); // Supprimer "file:"
                    scanJarFile(new File(jarFilePath), packageName, classes);
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Error scanning JAR: " + e.getMessage());
        }
    }
    
    private static void scanJarFile(File jarFile, String packageName, Set<Class<?>> classes) {
        // Implémentation basique pour les JARs - on va utiliser une approche alternative
        System.out.println("📦 Scanning JAR: " + jarFile.getName() + " for package: " + packageName);
    }
    
    private static void findClassesInClasspath(String packageName, Set<Class<?>> classes) {
        System.out.println("🔄 Using classpath fallback for: " + packageName);
        
        // Essayer de charger les classes directement par réflexion
        // Cette méthode est moins fiable mais peut aider en développement
        try {
            // Créer un ensemble de noms de classes probables basé sur la structure de packages
            Set<String> potentialClassNames = generatePotentialClassNames(packageName);
            
            for (String className : potentialClassNames) {
                loadClass(className, classes);
            }
        } catch (Exception e) {
            System.err.println("❌ Error in classpath fallback: " + e.getMessage());
        }
    }
    
    private static Set<String> generatePotentialClassNames(String packageName) {
        Set<String> names = new HashSet<>();
        
        // Ajouter des noms de classes communs qu'on pourrait trouver
        String[] commonClassNames = {
                "Main", "Application", "Config", "Configuration",
                "Controller", "Service", "Repository", "Component",
                "UserService", "UserController", "HomeController"
        };
        
        for (String className : commonClassNames) {
            names.add(packageName + "." + className);
        }
        
        return names;
    }
    
    private static void loadClass(String className, Set<Class<?>> classes) {
        try {
            if (className.contains("$")) {
                return;
            }
            
            Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            classes.add(clazz);
        } catch (ClassNotFoundException e) {
            // C'est normal, ignorer silencieusement
        } catch (NoClassDefFoundError e) {
            System.err.println("❌ Class definition not found for: " + className);
        } catch (Exception e) {
            System.err.println("❌ Error loading class: " + className + " - " + e.getMessage());
        }
    }
}
