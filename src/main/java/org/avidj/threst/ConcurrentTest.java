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
import org.junit.jupiter.api.Assertions;

import org.avidj.threst.TestRun.TestThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Utility for executing concurrent test with the purpose to reveal concurrency
 * bugs such as deadlocks or missing mutual exclusion. All threads passing does
 * not guarantee that there are no concurrency bugs. An appropriate number of
 * repetitions must be executed to reveal concurrency bugs, which cannot ever be
 * guaranteed. The number of repetitions can easily be in the order of several
 * thousands.
 */
public class ConcurrentTest {

  private static final Logger LOG = LoggerFactory.getLogger(ConcurrentTest.class);
  final ExecutorService pool
          = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
            public Thread newThread(Runnable runnable) {
              Thread thread = new Thread(runnable);
              threads.add(thread);
              return thread;
            }
          });
  final List<TestThread> testThreads;
  final List<Thread> threads;
  private int repeat = 1;
  private int nextIndex = 0;
  final int sessionCount;
  private TestRun lastRun;

  private ConcurrentTest(int sessionCount) {
    this.sessionCount = sessionCount;
    testThreads = new ArrayList<>(sessionCount);
    threads = new ArrayList<>(sessionCount);
  }

  /**
   * Create a concurrent test with any number of test threads. This method
   * follows the builder pattern in that the returned test can be further
   * configured by calls to the other public methods before running it.
   *
   * @param t0 the first test thread
   * @param more any number of additional test threads
   * @return this
   */
  public static ConcurrentTest threads(TestThread t0, TestThread... more) {
    ConcurrentTest test = new ConcurrentTest(more.length + 1);
    test.add(t0);
    for (TestThread t : more) {
      test.add(t);
    }
    return test;
  }

  private void add(TestThread testThread) {
    testThread.setIndex(nextIndex++);
    testThreads.add(testThread);
  }

  /**
   * The number of repetitions of the test. This must be sufficiently high for
   * finding concurrency bugs. The default of 1 is only sufficient for actually
   * sequential tests.
   *
   * @param repeat the number of repetitions to do
   * @return this
   */
  public ConcurrentTest repeat(int repeat) {
    this.repeat = repeat;
    return this;
  }

  /**
   * @return true iff all sessions were successful
   */
  public ConcurrentTest assertSuccess() {
    return assertSuccessCount(sessionCount);
  }

  /**
   * Executes the configured actions concurrently expecting a certain number of
   * successful concurrent actions.
   *
   * @param count the number of successful threads required
   * @return this
   */
  public ConcurrentTest assertSuccessCount(int count) {
    // Repetitions increase the probability to find erroneous interleavings of operations.
    for (int i = 0; i < repeat; i++) {
      LOG.trace("run {}", i + 1);
      lastRun = new TestRun(this);
      lastRun.runOnce();
      if (lastRun.hasAssertionError()) {
        throw lastRun.getAssertionError();
      }
      assertSuccessCount(lastRun, count);
    }
    return this;
  }

  private void assertSuccessCount(TestRun run, int count) {
    if (run.successCount() != count) {
      List<Throwable> throwables = run.getThrowables();
      for (int i = 0, n = throwables.size(); i < n; i++) {
        LOG.error("Error occurred in thread " + i + ": ", throwables.get(i));
      }
      Assertions.fail(String.format("success count deviates, expected %d but got %d", count, run.successCount()));
    }
  }

  /**
   * Execute the test.
   *
   * @return this
   */
  private ConcurrentTest run() {
    // Repetitions increase the probability to find erroneous interleavings of operations.
    for (int i = 0; i < repeat; i++) {
      TestRun run = new TestRun(this);
      run.runOnce();
    }
    return this;
  }

  /**
   * The number of successful test threads. That is, the number of threads that
   * did not fail with an exception or (assertion) error.
   *
   * @return the number of successful test threads
   */
  public int successCount() {
    return lastRun.successCount();
  }

  /**
   * Returns the list of errors and exceptions thrown during the previous run of
   * the test.
   *
   * @return the list of throwables thrown during the previous run of the test
   */
  public List<Throwable> getThrowables() {
    return Collections.unmodifiableList(lastRun.getThrowables());
  }

  /**
   * Create a new thread of actions to be tested.
   *
   * @return a new test thread, a container for a set of operations that are to
   * be executed in parallel to other threads
   */
  public static TestThread thread() {
    return new TestThread();
  }

  /**
   * You can provide the test thread as an argument to a test block and then
   * access it, e.g., to wait for certain ticks.
   */
  @FunctionalInterface
  public interface Actions {

    /**
     * A block of actions performed concurrently with the others.
     *
     * @param testThread the test thread, can be used for waiting for ticks
     * within blocks
     */
    public void execute(TestThread testThread) throws Exception;
  }

  /**
   * Functional interface used for the executions that don't require arguments.
   */
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
    public void execute(TestThread testThread) throws Exception {
      actions.execute();
    }
  }
}
