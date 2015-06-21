package org.avidj.util;

/*
 * #%L
 * zuul-core
 * %%
 * Copyright (C) 2015 David Kensche
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.Iterator;

/**
 * Utilities for working with strings.
 */
public final class Strings {

  private Strings() { /* hidden utility class constructor */ }

  /**
   * Given an iterable creates a string representation where all elements are comma-separated.
   * @param iterable the iterable to join, not {@code null}
   * @return a string containing all the elements separated by commas  
   */
  public static String join(Iterable<?> iterable) {
    return join(", ", iterable);
  }
  
  /**
   * Given an iterable creates a string representation where all elements are separated by the
   * given separator string.
   * @param separator the separator to insert between iterated elements
   * @param iterable the iterable to join, not {@code null}
   * @return a string containing all the elements separated by commas  
   */
  public static String join(String separator, Iterable<?> iterable) {
    Iterator<?> iter = iterable.iterator();
    if ( !iter.hasNext() ) {
      return "";
    }
    StringBuilder string = new StringBuilder();
    string.append(iter.next());
    while ( iter.hasNext() ) {
      string.append(separator).append(iter.next());
    }
    return string.toString();
  }
}
