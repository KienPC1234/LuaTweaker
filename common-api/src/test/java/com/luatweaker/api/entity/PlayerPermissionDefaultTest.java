package com.luatweaker.api.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PlayerPermissionDefaultTest {

    @Test
    void defaultDeniesOperatorLevel() {
        IPlayerExtras extras = new IPlayerExtras() {
        };

        assertFalse(extras.hasPermissionLevel(4),
                "outside a server every permission check must fail closed");
    }

    @Test
    void defaultDeniesLevelZero() {
        IPlayerExtras extras = new IPlayerExtras() {
        };

        assertFalse(extras.hasPermissionLevel(0),
                "even level 0 must fail closed without a server-side op list");
    }
}
