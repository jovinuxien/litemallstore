package org.linlinjava.litemall.goods.utils;

import org.springframework.util.Assert;

/**
 * A utility class for managing the user context in a multi-threaded environment.
 * This class provides methods to retrieve, set, and create an empty user context.
 * The user context is stored in a thread-local variable to ensure isolation between different threads.
 */
public class UserContextHolder {
    private static final ThreadLocal<UserContext> userContext = new ThreadLocal<UserContext>();

    /**
     * Retrieves the current user context.
     * If no user context is set for the current thread, an empty context is created and set.
     *
     * @return the current user context
     */
    public static final UserContext getContext(){
        UserContext context = userContext.get();

        if (context == null) {
            context = createEmptyContext();
            userContext.set(context);

        }
        return userContext.get();
    }

    /**
     * Sets the user context for the current thread.
     *
     * @param context the user context to be set
     * @throws IllegalArgumentException if the provided context is null
     */
    public static final void setContext(UserContext context) {
        Assert.notNull(context, "Only non-null UserContext instances are permitted");
        userContext.set(context);
    }

    /**
     * Creates an empty user context.
     *
     * @return an empty user context
     */
    public static final UserContext createEmptyContext(){
        return new UserContext();
    }
}
