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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

class TestThreadObserver implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(TestThreadObserver.class);
  private final ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
  private final TestRun testRun;
  private List<ThreadInfo> deadlock;

  TestThreadObserver(TestRun testRun) {
    this.testRun = testRun;
  }
  
  List<ThreadInfo> getDeadlock() {
    return deadlock;
  }

  @Override
  public void run() {
    while ( !testRun.finished() ) {
      if ( noThreadsRunning() ) {
        synchronized ( testRun.lock ) {
          if ( noThreadsRunning() ) {
            deadlock = findJavaLevelDeadlock();
            testRun.tick++;
            testRun.lock.notifyAll();
            if ( deadlock != null ) {
              LOG.info(Strings.join(deadlock));
              return;
            }
          }
        }
      }
      try {
        Thread.sleep(TestRun.SLEEP_INTERVAL );
      } catch (InterruptedException e) {
        // this cannot happen
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
  
  private List<ThreadInfo> findJavaLevelDeadlock() {
    for ( Thread t : testRun.concurrentTest.threads ) {
      if ( t.getState() == Thread.State.BLOCKED ) {
        List<ThreadInfo> loop = new LinkedList<ThreadInfo>();
        ThreadInfo currentInfo = threadMxBean.getThreadInfo(t.getId());
        loop.add(currentInfo);
        Long blockerId;
        do {
          blockerId = currentInfo.getLockOwnerId();
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