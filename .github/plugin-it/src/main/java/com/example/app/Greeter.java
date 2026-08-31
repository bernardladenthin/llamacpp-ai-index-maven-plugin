// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package com.example.app;

import com.example.util.Names;

/** Fixture source for the plugin integration test; its content is irrelevant beyond being real Java. */
public final class Greeter {

    private final String salutation;

    /**
     * Creates a greeter.
     *
     * @param salutation the leading word
     */
    public Greeter(final String salutation) {
        this.salutation = salutation;
    }

    /**
     * Greets somebody.
     *
     * @param name the name to greet
     * @return the greeting
     */
    public String greet(final String name) {
        return salutation + " " + Names.normalize(name);
    }
}
