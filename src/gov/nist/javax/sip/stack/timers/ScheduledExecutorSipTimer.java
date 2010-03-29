/*
* Conditions Of Use
*
* This software was developed by employees of the National Institute of
* Standards and Technology (NIST), an agency of the Federal Government.
* Pursuant to title 15 Untied States Code Section 105, works of NIST
* employees are not subject to copyright protection in the United States
* and are considered to be in the public domain.  As a result, a formal
* license is not needed to use the software.
*
* This software is provided by NIST as a service and is expressly
* provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
* OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
* AND DATA ACCURACY.  NIST does not warrant or make any representations
* regarding the use of the software or the results thereof, including but
* not limited to the correctness, accuracy, reliability or usefulness of
* the software.
*
* Permission to use this software is contingent upon your acceptance
* of the terms of this agreement
*
* .
*
*/
package gov.nist.javax.sip.stack.timers;

import gov.nist.javax.sip.stack.SIPStackTimerTask;

import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the SIP Timer based on java.util.concurrent.ScheduledThreadPoolExecutor
 * Seems to perform 
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public class ScheduledExecutorSipTimer implements SipTimer {

	ScheduledThreadPoolExecutor threadPoolExecutor;
	// Counts the number of cancelled tasks
    private volatile int numCancelled = 0;
    
	public ScheduledExecutorSipTimer() {
		threadPoolExecutor = new ScheduledThreadPoolExecutor(1);		
	}
	
	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#stop()
	 */
	@Override
	public void stop() {
		threadPoolExecutor.shutdown();
	}

	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#schedule(gov.nist.javax.sip.stack.SIPStackTimerTask, long)
	 */
	@Override
	public boolean schedule(SIPStackTimerTask task, long delay) {
		ScheduledFuture<?> future = threadPoolExecutor.schedule(new ScheduledSipTimerTask(task), delay, TimeUnit.MILLISECONDS);
		task.setSipTimerTask(future);
		return true;
	}

	/* (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.timers.SipTimer#setConfigurationProperties(java.util.Properties)
	 */
	@Override
	public void start(Properties configurationProperties) {
		// TODO have a param in the stack properties to set the number of thread for the timer executor
		threadPoolExecutor.prestartAllCoreThreads();
	}

	@Override
	public boolean cancel(SIPStackTimerTask task) {
		boolean cancelled = false;
		ScheduledFuture<?> sipTimerTask = (ScheduledFuture<?>) task.getSipTimerTask();
		if(sipTimerTask != null) {
			task.cleanUpBeforeCancel();			
			task.setSipTimerTask(null);
			cancelled = sipTimerTask.cancel(false);
		} 
		// Purge is expensive when called frequently, only call it every now and then.
        // We do not sync the numCancelled variable. We dont care about correctness of
        // the number, and we will still call purge rought once on every 100 cancels.
        numCancelled++;
        if(numCancelled % 25 == 0) {
            threadPoolExecutor.purge();
        }
		return cancelled;
	}

	private class ScheduledSipTimerTask implements Runnable {
		private SIPStackTimerTask task;

		public ScheduledSipTimerTask(SIPStackTimerTask task) {
			this.task= task;			
		}
		
		public void run() {
			 try {
				 // task can be null if it has been cancelled
				 if(task != null) {
					 task.runTask();
				 }
	        } catch (Throwable e) {
	            System.out.println("SIP stack timer task failed due to exception:");
	            e.printStackTrace();
	        }
		}				
	}
	
}
