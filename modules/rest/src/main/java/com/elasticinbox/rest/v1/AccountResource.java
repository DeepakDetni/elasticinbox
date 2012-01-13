/**
 * Copyright (c) 2011 Optimax Software Ltd
 * 
 * This file is part of ElasticInbox.
 * 
 * ElasticInbox is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 2 of the License, or (at your option) any later
 * version.
 * 
 * ElasticInbox is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * ElasticInbox. If not, see <http://www.gnu.org/licenses/>.
 */

package com.elasticinbox.rest.v1;

import java.io.IOException;
import java.net.URI;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elasticinbox.core.AccountDAO;
import com.elasticinbox.core.DAOFactory;
import com.elasticinbox.core.model.Mailbox;
import com.elasticinbox.rest.BadRequestException;
import com.elasticinbox.rest.JSONResponse;

/**
 * This JAX-RS resource is responsible for managing user accounts.
 * 
 * @author Rustam Aliyev
 */
@Path("{account}")
public final class AccountResource
{
	private final AccountDAO accountDAO;

	private final static Logger logger = 
		LoggerFactory.getLogger(AccountResource.class);

	@Context UriInfo uriInfo;

	public AccountResource() {
		DAOFactory dao = DAOFactory.getDAOFactory();
		accountDAO = dao.getAccountDAO();
	}

	/**
	 * Get account information
	 * 
	 * @param account
	 * @return
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response get(@PathParam("account") String account)
	{
		//TODO: implement...
		return Response.noContent().build();
	}
	
	/**
	 * Initialize new account
	 * 
	 * @param account
	 * @return
	 */
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response add(@PathParam("account") String account)
	{
		Mailbox mailbox = new Mailbox(account);

		try {
			accountDAO.add(mailbox);
		} catch (IllegalArgumentException iae) {
			throw new BadRequestException(iae.getMessage());
		} catch (IOException e) {
			logger.error("Account initialization failed: {}", account);
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}

		URI messageUri = uriInfo.getAbsolutePathBuilder().path("mailbox").build();

		return Response.created(messageUri).entity(JSONResponse.OK).build();
	}

	/**
	 * Delete account and all associated objects
	 * 
	 * @param account
	 * @return
	 */
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public Response delete(@PathParam("account") final String account)
	{
		final Mailbox mailbox = new Mailbox(account);

		try {
			// run deletion work in separate thread
			Thread t = new Thread() {
				@Override
				public void run() {
					try {
						accountDAO.delete(mailbox);
					} catch (IOException e) {
						logger.info("Account deletion failed: ", e);
					}
				}
			};

			// start thread
			t.start();
			t.join();
		} catch (Exception e) {
			logger.error("Account deletion failed: ", e);
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}

		return Response.noContent().build();
	}
}
