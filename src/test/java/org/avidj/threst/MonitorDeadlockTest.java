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

public class MonitorDeadlockTest {

  private static final Logger LOG = LoggerFactory.getLogger(MonitorDeadlockTest.class);

  @Test
  public void testMonitorDeadlock() {
    final MonitorDeadlocker testClass = new MonitorDeadlocker();
    AssertionError e = assertThrows(AssertionError.class, () -> threads(
            thread().exec(() -> testClass.a()),
            thread().exec(() -> testClass.b()))
            .assertSuccess());
    assertThat(e.getMessage(), stringContainsInOrder("Deadlock detected:"));
  }

  private static class MonitorDeadlocker {

    private final Object lockA = new Object();
    private final Object lockB = new Object();
    private volatile boolean aLocked = false;
    private volatile boolean bLocked = false;

    void a() throws InterruptedException {
      synchronized (lockA) {
        aLocked = true;
        LOG.info("a");
        aWaitBLocked();
        b();
      }
      aLocked = false;
    }

    private void aWaitBLocked() throws InterruptedException {
      while (!bLocked) {
        lockA.wait();
      }
    }

    void b() throws InterruptedException {
      synchronized (lockB) {
        bLocked = true;
        LOG.info("b");
        bWaitALocked();
        a();
      }
      bLocked = false;
    }

    private void bWaitALocked() throws InterruptedException {
      while (!aLocked) {
        lockB.wait();
      }
    }
  }
}
