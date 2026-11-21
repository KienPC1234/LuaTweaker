package com.luatweaker.interception;

import com.luatweaker.api.recipe.IRecipeManagerService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic-proxy fake of {@link IRecipeManagerService}: records every invocation as
 * (method name, raw arguments) and can fail the next {@code addAnvil} on demand.
 * A proxy keeps the fake from implementing twenty unrelated interface methods.
 */
final class RecordingRecipeService implements InvocationHandler {

    private static final String ADD_ANVIL = "addAnvil";

    record Invocation(String method, Object[] args) {
    }

    private final List<Invocation> invocations = new ArrayList<>();
    private boolean failNextAnvil;

    IRecipeManagerService asService() {
        return (IRecipeManagerService) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[] {IRecipeManagerService.class}, this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (ADD_ANVIL.equals(method.getName()) && failNextAnvil) {
            failNextAnvil = false;
            throw new IllegalStateException("delegate unavailable");
        }
        invocations.add(new Invocation(method.getName(), args == null ? new Object[0] : args.clone()));
        return null;
    }

    void failNextAnvilOnce() {
        failNextAnvil = true;
    }

    List<Invocation> callsOf(String method) {
        List<Invocation> matches = new ArrayList<>();
        for (Invocation invocation : invocations) {
            if (invocation.method().equals(method)) {
                matches.add(invocation);
            }
        }
        return matches;
    }

    List<String> methodsCalled() {
        List<String> methods = new ArrayList<>();
        for (Invocation invocation : invocations) {
            methods.add(invocation.method());
        }
        return methods;
    }
}
