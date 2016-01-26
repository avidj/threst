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

import com.google.common.base.Preconditions;

import org.avidj.threst.ConcurrentTest.Actions;
import org.avidj.threst.ConcurrentTest.NoArgActions;
import org.avidj.threst.ConcurrentTest.NoArgActionsWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single test run. It is observed
 */
public class TestRun {
  static final long SLEEP_INTERVAL = 5;
  
  private final List<Throwable> throwables;
  private final CountDownLatch startFlag = new CountDownLatch(1);
  private final AtomicInteger successCount = new AtomicInteger();
  private final AtomicInteger finishedCount = new AtomicInteger();
  

  final Object lock = new Object();// { @Override public String toString() { return LOCK_NAME; } };
  final String lockName = lock.toString();
  final ConcurrentTest concurrentTest;

  volatile int tick = 0;
  final PriorityQueue<Integer> ticks = new PriorityQueue<>();

  // Increments the tick counter when all threads are blocked, waiting, or terminated, so as to 
  // allow waiting threads to continue. Also discovers deadlocks.
  private final TestThreadObserver threadObserver = new TestThreadObserver(this);
  
  List<Throwable> getThrowables() {
    return Collections.unmodifiableList(throwables);
  }

  TestRun(ConcurrentTest concurrentTest) {
    this.concurrentTest = concurrentTest;
    throwables = new ArrayList<>(concurrentTest.sessionCount);
    for ( int i = 0; i < concurrentTest.sessionCount; i++ ) {
      throwables.add(null);
    }
  }

  private void appendWaitFor(int tick) {
    synchronized ( lock ) {
      if ( this.tick == tick ) {
        return;
      }
      if ( this.tick > tick ) {
        throw new RuntimeException("Ticks expected out of order. This is a bug in your test.");
      }
      final Integer newTick = Integer.valueOf(tick);
      if ( ticks.contains(newTick) ) {
        return;
      }
      // TODO: INSERT (SORT) TICK AT CORRECT POSITION!
      ticks.add(newTick);
    }
  }

  void runOnce() {
    // start the threads, actually they will wait for the start flag
    for ( TestThread thread : concurrentTest.testThreads ) {
      thread.test = this;
      concurrentTest.pool.execute(thread);
    }
    // give all test threads the start signal
    startFlag.countDown();
    new Thread(threadObserver).start();
    try {
      awaitFinished();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void awaitFinished() throws InterruptedException {
    synchronized ( lock ) {
      while ( finishedCount.get() < concurrentTest.sessionCount 
        && threadObserver.getAssertionError() == null ) {
        lock.wait();
      }
    }
  }

  /**
   * Returns whether all test threads were successful.
   * 
   * @return true, iff all test threads succeeded
   */
  boolean success() {
    return successCount.get() == concurrentTest.sessionCount;
  }
  
  boolean finished() {
    return finishedCount.get() == concurrentTest.sessionCount || hasAssertionError();
  }
  
  /**
   * The number of successful test threads. That is, the number of threads that did not fail with
   * an exception or (assertion) error.
   * 
   * @return the number of successful test threads
   */
  int successCount() {
    return successCount.get();
  }

  public boolean hasAssertionError() {
    return ( threadObserver.getAssertionError() != null );
  }
  
  public AssertionError getAssertionError() {
    return threadObserver.getAssertionError();
  }
  
  public static class TestThread implements Runnable {
    private final List<Actions> blocks = new ArrayList<>();
    private int index;
    private TestRun test;

    TestThread() {
    }
    
    void setIndex(int index) {
      this.index = index;
    }
    
    /**
     * @param block a block of actions to be executed
     * @return this
     */
    public TestThread exec(Actions block) {
      this.blocks.add(block);
      return this;
    }

    public TestThread exec(NoArgActions block) {
      this.blocks.add(new NoArgActionsWrapper(block));
      return this;
    }

    @Override
    public void run() {
      try {
        test.startFlag.await();
        for ( Actions block : blocks ) {
          block.execute(this);
        }
        test.successCount.getAndIncrement();
      } catch ( Throwable t ) {
        test.throwables.set(index, t);
      } finally {
        test.finishedCount.getAndIncrement();
        synchronized ( test.lock ) {
          test.lock.notify();
        }
      }
    }

    /**
     * Wait for the given tick. Ticks must be waited for in order, gaps are fine.
     * @param tick the tick to wait for
     * @throws IllegalArgumentException if the tick is negative or out of order
     */
    public void waitFor(int tick) throws IllegalArgumentException {
      Preconditions.checkArgument(tick >= 0, "ticks must be > 0");
      test.appendWaitFor(tick);
      try {
        synchronized ( test.lock ) {
          while ( test.tick < tick ) {
            test.lock.wait();
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
