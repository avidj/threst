package org.avidj.threst;

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

import java.lang.management.ThreadInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.avidj.threst.TestRun.TestThread;
import org.avidj.util.Strings;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Utility for executing concurrent test with the purpose to reveal concurrency bugs such as 
 * deadlocks or missing mutual exclusion. All threads passing does not guarantee that there are
 * no concurrency bugs. An appropriate number of repetitions must be executed to reveal concurrency 
 * bugs, which cannot ever be guaranteed. The number of repetitions can easily be in the order of 
 * several thousands. 
 */
public class ConcurrentTest {
  private static final Logger LOG = LoggerFactory.getLogger(ConcurrentTest.class);
  final ExecutorService pool = 
      Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
        public Thread newThread(Runnable r) {
          Thread t = new Thread(r);
          threads.add(t);
          return t;
        }
      });
  final List<TestThread> testThreads;  
  final List<Thread> threads;  
  private int repeat = 1;
  private int killAfter;
  private int nextIndex = 0;
  final int sessionCount;
  private TestRun lastRun;

  private ConcurrentTest(int sessionCount) {
    this.sessionCount = sessionCount;
    testThreads = new ArrayList<>(sessionCount);
    threads = new ArrayList<>(sessionCount);
  }

  /**
   * Create a concurrent test with any number of test threads. This method follows the builder 
   * pattern in that the returned test can be further configured by calls to the other public 
   * methods before running it.
   * 
   * @param t0 the first test thread
   * @param more any number of additional test threads
   * @return this
   */
  public static ConcurrentTest threads(TestThread t0, TestThread... more) {
    ConcurrentTest test = new ConcurrentTest(more.length + 1);
    test.add(t0);
    for ( TestThread t : more ) {
      test.add(t);
    }
    return test;
  }

  private void add(TestThread testThread) {
    testThread.setIndex(nextIndex++);
    testThreads.add(testThread);
  }

  /**
   * In case of deadlocks, the test must be aborted, i.e., threads must be killed. 
   * @param killAfterMs the maximum number of milliseconds to wait for the test to end
   * @return this
   */
  public ConcurrentTest killAfter(int killAfterMs) {
    this.killAfter = killAfterMs;
    return this;
  }

  /**
   * The number of repetitions of the test. This must be sufficiently high for finding concurrency
   * bugs. The default of 1 is only sufficient for actually sequential tests.
   * @param repeat the number of repetitions to do
   * @return this
   */
  public ConcurrentTest repeat(int repeat) {
    this.repeat = repeat;
    return this;
  }

  public ConcurrentTest assertSuccess() {
    return assertSuccessCount(sessionCount);
  }

  public ConcurrentTest assertSuccessCount(int count) {
    // Repetitions increase the probability to find erroneous interleavings of operations.
    for ( int i = 0; i < repeat; i++ ) {
      LOG.trace("run {}", i + 1);
      lastRun = new TestRun(this);
      lastRun.runOnce();
      if ( lastRun.getDeadlock() != null ) {
        List<ThreadInfo> deadlock = lastRun.getDeadlock();
        LOG.warn("\nDeadlock detected:\n" + Strings.join("", deadlock));
        Assert.fail();
      }
      assertSuccessCount(lastRun, count);
    }
    return this;
  }

  private void assertSuccessCount(TestRun run, int count) {
    if ( run.successCount() != count ) {
      List<Throwable> throwables = run.getThrowables();
      for ( int i = 0, n = throwables.size(); i < n; i++ ) {
        LOG.error("Error occurred in thread " + i + ": ", throwables.get(i));
      }
      Assert.fail();
    }
  }

  /**
   * Execute the test.
   * @return this
   */
  public ConcurrentTest run() {
    // Repetitions increase the probability to find erroneous interleavings of operations.
    for ( int i = 0; i < repeat; i++ ) {
      TestRun run = new TestRun(this);
      run.runOnce();
    }
    return this;
  }

  /**
   * The number of successful test threads. That is, the number of threads that did not fail with
   * an exception or (assertion) error.
   * 
   * @return the number of successful test threads
   */
  public int successCount() {
    return lastRun.successCount();
  }

  public List<Throwable> getThrowables() {
    return Collections.unmodifiableList(lastRun.getThrowables());
  }

  public static TestThread thread() {
    return new TestThread();
  }
    
  @FunctionalInterface
  public interface Actions {
    /**
     * @param t the test thread, can be used for waiting for ticks within blocks
     */
    abstract void execute(TestThread t) throws Exception;
  }

  @FunctionalInterface
  public interface NoArgActions {
    abstract void execute() throws Exception;
  }
  
  // A wrapper to adapt the Actions interface to the NoArgActions interface
  static class NoArgActionsWrapper implements Actions {
    private final NoArgActions actions;
    
    NoArgActionsWrapper(NoArgActions actions) {
      this.actions = actions;
    }
    
    @Override
    public void execute(TestThread t) throws Exception {
      actions.execute();
    }
  }
}
