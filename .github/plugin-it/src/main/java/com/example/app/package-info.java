// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0

/**
 * Exists so the fixture's {@code <excludes>} pattern has something to exclude.
 *
 * <p>Without a file this pattern can actually match, {@code <excludes>} binds from the XML and the
 * integration test cannot tell -- deleting the element left the test green. The generate check
 * asserts that no {@code package-info.java.ai.md} was written.</p>
 */
package com.example.app;
