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

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TickTest {
  private static final Logger LOG = LoggerFactory.getLogger(TickTest.class);

  @Test
  public void testWaitOnce() {
    threads(
        thread().exec((t) -> {
          LOG.debug("We shall wait for 1");
          t.waitFor(1);
          LOG.trace("1");
        }),
        thread().exec((t) -> {
          LOG.trace("0");
        }))
        .repeat(1000)
        .assertSuccess();
  }  

  @Test
  public void testWaitAnyParallel() {
    threads(
        thread().exec((t) -> {
          LOG.debug("We shall wait for 3 with two threads");
          t.waitFor(3);
          LOG.trace("3");
        }),
        thread().exec((t) -> {
          t.waitFor(3);
          LOG.trace("3");
        }))
        .repeat(1000)
        .assertSuccess();
  }  

  @Test ( expected = AssertionError.class )
  public void testWaitOutOfOrder() {
    threads(
        thread().exec((t) -> {
          LOG.debug("We shall wait for 2 and then 1");
          t.waitFor(2);
          LOG.trace("2");
          t.waitFor(1);
          LOG.trace("1");
        }))
        .repeat(1000)
        .assertSuccess();
  }  

  @Test
  public void testWaitWithGapsAndParallel() {
    threads(
        thread().exec((t) -> {
          LOG.trace("We shall wait for 1, 2, 3, 5, 8, 13, 21 (twice), 34 (twice)");
          t.waitFor(1);
          LOG.trace("1");
          t.waitFor(5);
          LOG.trace("5");
          t.waitFor(21);
          LOG.trace("21");
          t.waitFor(34);
          LOG.trace("34");
        }),
        thread().exec((t) -> {
          t.waitFor(2);
          LOG.trace("2");
          t.waitFor(3);
          LOG.trace("3");
          t.waitFor(21);
          LOG.trace("21");
        }),
        thread().exec((t) -> {
          t.waitFor(8);
          LOG.trace("8");
          t.waitFor(13);
          LOG.trace("13");
          t.waitFor(34);
          LOG.trace("34");
        }))
        .repeat(1000)
        .assertSuccess();
  }  

  @Test
  public void testWaitForNothing() {
    threads(
        thread().exec((t) -> {
          LOG.debug("I'm fine");
        }),
        thread().exec((t) -> {
          LOG.trace("Me too");
        }),
        thread().exec((t) -> {
          LOG.trace("And me as well");
        }))
        .repeat(1000000)
        .assertSuccess();
  }  
}
