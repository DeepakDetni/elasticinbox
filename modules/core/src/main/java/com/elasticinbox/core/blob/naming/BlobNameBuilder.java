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

package com.elasticinbox.core.blob.naming;

import java.util.UUID;

import com.elasticinbox.common.utils.Assert;
import com.elasticinbox.core.blob.store.BlobStoreConstants;
import com.elasticinbox.core.model.Mailbox;

/**
 * This builder generates new Blob name based on the provided parameters and
 * specific {@link AbstractBlobNamingPolicy} implementation.
 * 
 * @author Rustam Aliyev
 */
public final class BlobNameBuilder
{
	protected Mailbox mailbox;
	protected UUID messageId;
	protected Long messageSize;

	private static AbstractBlobNamingPolicy uuidPolicy = new UuidBlobNamingPolicy();

	public BlobNameBuilder setMailbox(Mailbox mailbox) {
		this.mailbox = mailbox;
		return this;
	}

	public BlobNameBuilder setMessageId(UUID messageId) {
		this.messageId = messageId;
		return this;
	}

	public BlobNameBuilder setMessageSize(Long size) {
		this.messageSize = size;
		return this;
	}

	/**
	 * Generate new Blob name
	 * 
	 * @return
	 */
	public String build() {
		String name = uuidPolicy.getBlobName(this);
		validateBlobName(name);
		return name;
	}

	/**
	 * Validate generated Blob name
	 * 
	 * @param name
	 */
	private final static void validateBlobName(String name) {
		Assert.isFalse(
				name.endsWith(BlobStoreConstants.COMPRESS_SUFFIX),
				"This suffix is reserved for internal compression. Blob name should not end with "
						+ BlobStoreConstants.COMPRESS_SUFFIX);
	}
}
