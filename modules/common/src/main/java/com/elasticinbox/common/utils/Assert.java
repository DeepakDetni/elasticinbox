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

package com.elasticinbox.common.utils;

/**
* A generic low weight assert utility
* 
*/
public class Assert
{

	public static void notNull(Object object, String message)
	{
		if (object == null) {
			throw new IllegalArgumentException(message);
		}
	}

	public static void noneNull(Object ... object)
	{
		for (int i = 0; i < object.length; ++i) {
			if (object[i] == null) {
				throw new NullPointerException("Null not allowed, number "
						+ (i + 1));
			}
		}
	}

	/**
	 * Will throw {@link IllegalArgumentException} if expression is false.
	 * 
	 * @param b Boolean expression
	 * @param message Exception message
	 */
	public static void isTrue(boolean b, String message)
	{
		if (!b) {
			throw new IllegalArgumentException(message);
		}
	}

	/**
	 * Will throw {@link IllegalArgumentException} if expression is true.
	 * 
	 * @param b Boolean expression
	 * @param message Exception message
	 */
	public static void isFalse(boolean b, String message)
	{
		if (b) {
			throw new IllegalArgumentException(message);
		}
	}
}
