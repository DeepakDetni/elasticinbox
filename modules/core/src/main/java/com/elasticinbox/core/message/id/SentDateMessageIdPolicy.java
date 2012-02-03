/**
 * Copyright (c) 2011-2012 Optimax Software Ltd.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *  * Neither the name of Optimax Software, ElasticInbox, nor the names
 *    of its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.elasticinbox.core.message.id;

import java.util.UUID;

import com.elasticinbox.common.utils.Assert;

import me.prettyprint.cassandra.utils.TimeUUIDUtils;

/**
 * Generates message ID based on the time when message was sent.
 * <p>
 * <b>WARNING:</b> This implementation does not guarantee uniqueness.
 * 
 * @author Rustam Aliyev
 */
public final class SentDateMessageIdPolicy extends AbstractMessageIdPolicy
{
	/** The last time value issued. Used to try to prevent duplicates. */
	private static long lastTime = -1;

	@Override
	protected UUID getMessageId(MessageIdBuilder builder)
	{
		Assert.notNull(builder.sentDate, "sent date cannot be null");

		// The following advances mail time by a milliseconds of current time

		// Counter should be checked against current time 
		long currentUs = System.currentTimeMillis();
		
		// Message date has granularity of seconds. Delta adds milliseconds of
		// current time.
		long emailUs = builder.sentDate.getTime();
		long delta = 0;
		
		// Synchronized to guarantee unique time within and across threads.
		synchronized (SentDateMessageIdPolicy.class)
		{
			if (currentUs > lastTime) {
				lastTime = currentUs;
			} else {
				// the time i got from the system is equals or less
				// (hope not - clock going backwards)
				// One more "millisecond"
				++lastTime;
			}

			// Get ms from the static counter
			delta = lastTime % 1000L;
		}

		// Append ms to the message date
		emailUs += delta;
		return TimeUUIDUtils.getTimeUUID(emailUs);
	}
}