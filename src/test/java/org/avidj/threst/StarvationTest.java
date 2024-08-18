package org.avidj.threst;

/*
 * #%L
 * threst
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
import static org.avidj.threst.ConcurrentTest.thread;
import static org.avidj.threst.ConcurrentTest.threads;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.stringContainsInOrder;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StarvationTest {

  private static final Logger LOG = LoggerFactory.getLogger(StarvationTest.class);

  @Test
  public void testStarve() {
    final Object lock = new Object();
    AssertionError e = assertThrows(AssertionError.class, () -> threads(
            thread().exec((t) -> {
              t.waitFor(1);
              synchronized (lock) {
                lock.wait(); // there will never be a notification on this lock.
              }
            }),
            thread().exec((t) -> {
              t.waitFor(2);
              LOG.info("Waited for 2, the other thread should be starving.");
            }))
            .assertSuccess());
    assertThat(e.getMessage(), stringContainsInOrder("Threads are starving. Missed signal?"));
  }
}
