// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package com.example.util;

/** Fixture source in a second package, so aggregate-packages has more than one package to write. */
public final class Names {

    private Names() {
        // utility class
    }

    /**
     * Trims a name and collapses inner whitespace.
     *
     * @param name the raw name
     * @return the normalised name
     */
    public static String normalize(final String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }
}
