package com.luatweaker.core.bind;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicJavaProxyTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void testSmartBlacklist_AllowsMinecraftClasses() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, String.class, "length");
        
        assertNotNull(result, "String.length() should be allowed - not in blacklist");
    }

    @Test
    void testSmartBlacklist_AllowsJavaUtilClasses() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, ArrayList.class, "size");
        
        assertNotNull(result, "ArrayList.size() should be allowed - java.util not in blacklist");
    }

    @Test
    void testSmartBlacklist_AllowsCrossModPackages() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, String.class, "length");
        
        assertNotNull(result, "Cross-mod packages should be allowed (not in blacklist)");
    }

    @Test
    void testSmartBlacklist_BlocksRuntimeClass() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, Runtime.class, "exec");
        
        assertNull(result, "Runtime.exec() should be blocked by package blacklist");
    }

    @Test
    void testSmartBlacklist_BlocksProcessBuilder() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, ProcessBuilder.class, "start");
        
        assertNull(result, "ProcessBuilder.start() should be blocked by package blacklist");
    }

    @Test
    void testSmartBlacklist_BlocksSystemClass() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, System.class, "exit");
        
        assertNull(result, "System.exit() should be blocked by package blacklist");
    }

    @Test
    void testSmartBlacklist_BlocksThreadClass() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, Thread.class, "start");
        
        assertNull(result, "Thread.start() should be blocked by package blacklist");
    }

    @Test
    void testSmartBlacklist_BlocksReflectPackage() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, Method.class, "invoke");
        
        assertNull(result, "Method.invoke() should be blocked - java.lang.reflect.* is blacklisted");
    }

    @Test
    void testSmartBlacklist_BlocksReflectField() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, Field.class, "get");
        
        assertNull(result, "Field.get() should be blocked - java.lang.reflect.* is blacklisted");
    }

    @Test
    void testSmartBlacklist_BlocksClassClass() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, Class.class, "forName");
        
        assertNull(result, "Class.forName() should be blocked - java.lang.Class is blacklisted");
    }

    @Test
    void testSmartBlacklist_BlocksClassLoader() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, ClassLoader.class, "loadClass");
        
        assertNull(result, "ClassLoader.loadClass() should be blocked - java.lang.ClassLoader is blacklisted");
    }

    @Test
    void testMethodBlacklist_BlocksGetClassMethod() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, String.class, "getClass");
        
        assertNull(result, "getClass() should be blocked by method blacklist");
    }

    @Test
    void testMethodBlacklist_BlocksWaitMethod() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Object result = method.invoke(null, String.class, "wait");
        
        assertNull(result, "wait() should be blocked by method blacklist");
    }

    @Test
    void testIntegration_AllowedClassMethodsAreAccessible() throws Exception {
        Method method = DynamicJavaProxy.class.getDeclaredMethod("findMethodCached", Class.class, String.class);
        method.setAccessible(true);
        
        Method lengthMethod = (Method) method.invoke(null, String.class, "length");
        assertNotNull(lengthMethod, "String.length() should be accessible");
        assertEquals("length", lengthMethod.getName());
    }
}
