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

import org.avidj.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

class TestThreadObserver implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(TestThreadObserver.class);
  private static final Set<Thread.State> WAIT_STATES = Collections.unmodifiableSet(EnumSet.of(
      Thread.State.TIMED_WAITING, Thread.State.WAITING));

  private final ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
  private final TestRun testRun;
  private List<ThreadInfo> deadlock;
  private volatile AssertionError assertionError;
  private long[] waitCounts;
  private long[] waitTimes;

  TestThreadObserver(TestRun testRun) {
    this.testRun = testRun;
  }
  
  AssertionError getAssertionError() {
    return assertionError;
  }
  
  @Override
  public void run() {
    this.waitCounts = new long[testRun.concurrentTest.threads.size()];
    this.waitTimes = new long[testRun.concurrentTest.threads.size()];
    Arrays.fill(waitCounts, 0);
    Arrays.fill(waitTimes, 0);
    while ( !testRun.finished() ) {
      if ( noThreadsRunning() ) {
        if ( waitingForTick() ) {
          tick();
        } else {
          if ( noThreadsRunning() ) {
            // deadlock?
            deadlock = findJavaLevelDeadlock();
            if ( deadlock != null ) {
              assertionError = 
                  new AssertionError("\nDeadlock detected:\n" + Strings.join("", deadlock));
            }
            // starvation? 
            ThreadInfo starving = findStarving(waitCounts, waitTimes);
            if ( starving != null ) {
              assertionError = new AssertionError("Threads are starving. Missed signal?");
            }
          }
        }
      }
      try {
        Thread.sleep(TestRun.SLEEP_INTERVAL);
      } catch (InterruptedException e) {
        // this cannot happen
      }
    }
    synchronized ( testRun.lock ) {
      testRun.lock.notifyAll();
    }
  }
  
  private void tick() {
    if ( !testRun.ticks.isEmpty() ) {
      synchronized ( testRun.lock ) {
        testRun.tick = testRun.ticks.remove().intValue();
        testRun.lock.notifyAll();
      }
    }
  }

  private boolean noThreadsRunning() {
    for ( Thread t : testRun.concurrentTest.threads ) {
      if ( t.getState() == Thread.State.RUNNABLE ) {
        return false;
      }
    }
    return true;
  }

  private boolean waitingForTick() {
    assert ( waitCounts.length == testRun.concurrentTest.threads.size() );
    int waiting = 0;
    int newT = 0;
    int blocked = 0;
    int runnable = 0;
    int terminated = 0;
    for ( Thread t : testRun.concurrentTest.threads ) {
      final ThreadInfo info = threadMxBean.getThreadInfo(t.getId());
      
      switch ( t.getState() ) {
        case TIMED_WAITING:
        case WAITING:
          LOG.trace(toString(info));
          if ( testRun.lockName.equals(info.getLockName()) ) {
            // thread is waiting for this thread, i.e., for the tick
            return true;
          }
          waiting++;
          break;
        case NEW:
          newT++;
          break;
        case RUNNABLE:
          runnable++;
          break;
        case BLOCKED:
          blocked++;
          break;
        case TERMINATED:
          terminated++;
          break;
        default:
          throw new RuntimeException("Unknown thread state.");
      }
    }
    LOG.trace("waiting: {}, newT: {}, blocked: {}, runnable: {}, terminated: {}",
        waiting, newT, blocked, runnable, terminated);
    return false;
  }
  
  private ThreadInfo findStarving(long[] waitCounts, long[] waitTimes) {
    assert ( waitCounts.length == testRun.concurrentTest.threads.size() );
    for ( int i = 0, n = testRun.concurrentTest.threads.size(); i < n; i++ ) {
      Thread thread = testRun.concurrentTest.threads.get(i);
      final ThreadInfo info = threadMxBean.getThreadInfo(thread.getId());
      if ( WAIT_STATES.contains(thread.getState()) ) {
        LOG.trace(toString(info));
        if ( waitCounts[i] < info.getWaitedCount() ) {
          waitCounts[i] = info.getWaitedCount();
          waitTimes[i] = System.currentTimeMillis();
        } else if ( System.currentTimeMillis() - waitTimes[i] > 2000 ) { 
          // otherwise it's relaxing in the pool
          return info;
        }
      }
    }
    return null;
  }

  private String toString(ThreadInfo info) {
    return new StringBuilder()
        .append("testRun.lockName=").append(testRun.lockName)
        .append(", info.getLockName()=").append(info.getLockName())
        .append(", ownerName=").append(info.getLockOwnerName())
        .append(", ownerId=").append(info.getLockOwnerId())
        .append(", waitedCount=").append(info.getWaitedCount())
        .toString();
  }

  private List<ThreadInfo> findJavaLevelDeadlock() {
    for ( Thread t : testRun.concurrentTest.threads ) {
      if ( t.getState() == Thread.State.BLOCKED ) {
        List<ThreadInfo> loop = new LinkedList<ThreadInfo>();
        ThreadInfo currentInfo = threadMxBean.getThreadInfo(t.getId());
        loop.add(currentInfo);
        Long blockerId;
        do {
          blockerId = currentInfo.getLockOwnerId();
          if ( blockerId == -1 ) {
            // not blocked anymore
            break;
          }
          currentInfo = threadMxBean.getThreadInfo(blockerId);
          loop.add(currentInfo);
        } while ( 
            currentInfo.getThreadState() == Thread.State.BLOCKED 
            && loop.get(0).getThreadId() != blockerId.longValue() );
        
        if ( currentInfo.getThreadState() == Thread.State.BLOCKED ) {
          return Collections.unmodifiableList(loop.subList(0, loop.size() - 1));
        }
      }
    }
    return null;
  }
}