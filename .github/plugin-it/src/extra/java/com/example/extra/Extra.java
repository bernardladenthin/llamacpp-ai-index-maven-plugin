// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package com.example.extra;

/**
 * Fixture source under a NON-default source root.
 *
 * <p>It exists so {@code <subtrees>} is observable: the engine's own fallback is
 * {@code src/main/java}, so a fixture that lists only that value cannot tell a bound
 * {@code <subtrees>} from an unbound one -- deleting the element left the integration test green.
 * This file is reachable only through the second {@code <subtree>} entry.</p>
 */
public final class Extra {

    private Extra() {
        // utility class
    }

    /**
     * Returns a constant.
     *
     * @return the answer
     */
    public static int answer() {
        return 42;
    }
}
