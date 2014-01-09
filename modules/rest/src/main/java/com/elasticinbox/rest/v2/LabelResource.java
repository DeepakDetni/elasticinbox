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

package com.elasticinbox.rest.v2;

import java.net.URI;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elasticinbox.common.utils.JSONUtils;
import com.elasticinbox.core.DAOFactory;
import com.elasticinbox.core.ExistingLabelException;
import com.elasticinbox.core.IllegalLabelException;
import com.elasticinbox.core.LabelDAO;
import com.elasticinbox.core.MessageDAO;
import com.elasticinbox.core.model.Label;
import com.elasticinbox.core.model.Mailbox;
import com.elasticinbox.rest.BadRequestException;
import com.elasticinbox.rest.RESTApplicationException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This JAX-RS resource is responsible for managing mailbox labels.
 * 
 * @author Rustam Aliyev
 */
@Path("{domain}/{user}/mailbox/label")
public final class LabelResource
{
	private final MessageDAO messageDAO;
	private final LabelDAO labelDAO;

	private final static Logger logger = 
			LoggerFactory.getLogger(LabelResource.class);

	@Context UriInfo uriInfo;

	public LabelResource()
	{
		DAOFactory dao = DAOFactory.getDAOFactory();
		messageDAO = dao.getMessageDAO();
		labelDAO = dao.getLabelDAO();
	}

	/**
	 * Get all messages labeled with given label
	 * 
	 * @param account
	 * @param labelId
	 * @param withMetadata
	 * @param reverse
	 * @param start
	 * @param count
	 * @return
	 */
	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMessages(
			@PathParam("user") String user,
			@PathParam("domain") String domain,
			@PathParam("id") Integer labelId,
			@QueryParam("metadata") @DefaultValue("false") boolean withMetadata,
			@QueryParam("includebody") @DefaultValue("false") boolean includeBody,
			@QueryParam("reverse") @DefaultValue("true") boolean reverse,
			@QueryParam("start") UUID start,
			@QueryParam("count") @DefaultValue("50") int count)
	{
		Mailbox mailbox = new Mailbox(user, domain);
		byte[] response;
		
		try {
			if (withMetadata) {
				response = JSONUtils.fromObject(messageDAO.getMessageIdsWithMetadata(mailbox,
						labelId, start, count, reverse, includeBody));
			} else {
				response = JSONUtils.fromObject(messageDAO.getMessageIds(mailbox,
						labelId, start, count, reverse));
			}
		} catch (Exception e) {
			logger.error("REST get of message headers for {}/{} failed: {}", 
					new Object[] { mailbox.getId(), labelId, e.getMessage() });
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}

		return Response.ok(response).build();
	}

	/**
	 * Update existing label
	 * 
	 * @param account
	 * @param labelId
	 * @param label
	 * @return
	 */
	@PUT
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response updateLabel(
			@PathParam("user") String user,
			@PathParam("domain") String domain,
			@PathParam("id") Integer labelId,
			@QueryParam("name") String labelName,
			String requestJSONContent)
	{
		Mailbox mailbox = new Mailbox(user, domain);
		Label label;

		if (requestJSONContent.isEmpty())
		{
			// if request body is empty, use path param
			label = new Label();
			label.setName(labelName);
		}
		else
		{
			// deserialize request body to Label
			try {
				ObjectMapper mapper = new ObjectMapper();
				label = mapper.readValue(requestJSONContent, Label.class);
			} catch (Exception e) {
				logger.info("Malformed JSON request: {}", e.getMessage());
				throw new BadRequestException("Malformed JSON request");
			}

			if (label.getName() == null && labelName != null) {
				label.setName(labelName);
			}
		}

		label.setId(labelId);

		try {
			labelDAO.update(mailbox, label);
		} catch (IllegalLabelException ile) {
			throw new BadRequestException(ile.getMessage());
		} catch (ExistingLabelException ele) {
			throw new RESTApplicationException(Status.CONFLICT, ele.getMessage());
		} catch (Exception e) {
			logger.error("Updating label failed: ", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		}

		return Response.noContent().build();
	}

	/**
	 * Add new label
	 * 
	 * @param account
	 * @param labelName
	 * @return
	 */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response addLabel(
			@PathParam("user") String user,
			@PathParam("domain") String domain,
			@QueryParam("name") String labelName,
			String requestJSONContent)
	{
		Mailbox mailbox = new Mailbox(user, domain);
		Label label;

		if (requestJSONContent.isEmpty())
		{
			// if request body is empty, use path param
			label = new Label();
			label.setName(labelName);
		}
		else
		{
			// deserialize request body to Label
			try {
				ObjectMapper mapper = new ObjectMapper();
				label = mapper.readValue(requestJSONContent, Label.class);
			} catch (Exception e) {
				logger.info("Malformed JSON request: {}", e.getMessage());
				throw new BadRequestException("Malformed JSON request");
			}
		}

		if (label.getName() == null && labelName == null) {
			throw new BadRequestException("Label name must be specified");
		}

		Integer newLabelId = null;

		try {
			newLabelId = labelDAO.add(mailbox, label);
		} catch (IllegalLabelException ile) {
			throw new BadRequestException(ile.getMessage());
		} catch (ExistingLabelException ele) {
			throw new RESTApplicationException(Status.CONFLICT, ele.getMessage());
		} catch (Exception e) {
			logger.error("Adding label failed", e);
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}

		// build response
		URI messageUri = uriInfo.getAbsolutePathBuilder()
				.path(Integer.toString(newLabelId)).build();

		String responseJson = "{\"id\":" + newLabelId + "}";

		return Response.created(messageUri).entity(responseJson).build();
	}

	/**
	 * Delete label
	 * 
	 * @param account
	 * @param labelId
	 * @return
	 */
	@DELETE
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response deleteLabel(
			@PathParam("user") String user,
			@PathParam("domain") String domain,
			@PathParam("id") Integer labelId)
	{
		Mailbox mailbox = new Mailbox(user, domain);

		try {
			labelDAO.delete(mailbox, labelId);
		} catch (IllegalLabelException ile) {
			throw new BadRequestException(ile.getMessage());
		} catch (Exception e) {
			logger.error("Deleting label failed", e);
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}

		return Response.noContent().build();
	}

}
