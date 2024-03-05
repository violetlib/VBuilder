/*
 * Copyright (c) 2024 Alan Snyder.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the license agreement. For details see
 * accompanying license terms.
 */

package org.violetlib.vbuilder;

/**
  A dependency scope indicating requirements for direct dependencies.
  <p>
  Possible values:
  <ul>
  <li>REQUIRED: the item is required for compilation and execution.</li>
  <li>COMPILE: the item is required for compilation, but not for execution.</li>
  <li>RUNTIME: the item is required for execution, but not for compilation.</li>
  <li>INCLUDED: the item should be included in a Uber JAR.</li>
  </ul>
*/

public enum Scope {REQUIRED, COMPILE, RUNTIME, INCLUDED}
