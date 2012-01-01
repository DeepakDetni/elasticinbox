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

package com.elasticinbox.core.cassandra;

import static me.prettyprint.hector.api.factory.HFactory.createMutator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.mutation.Mutator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elasticinbox.config.Configurator;
import com.elasticinbox.core.IllegalLabelException;
import com.elasticinbox.core.MessageDAO;
import com.elasticinbox.core.OverQuotaException;
import com.elasticinbox.core.blob.BlobProxy;
import com.elasticinbox.core.blob.naming.BlobNameBuilder;
import com.elasticinbox.core.cassandra.persistence.LabelCounterPersistence;
import com.elasticinbox.core.cassandra.persistence.LabelIndexPersistence;
import com.elasticinbox.core.cassandra.persistence.Marshaller;
import com.elasticinbox.core.cassandra.persistence.MessagePersistence;
import com.elasticinbox.core.cassandra.persistence.PurgeIndexPersistence;
import com.elasticinbox.core.model.LabelCounters;
import com.elasticinbox.core.model.Labels;
import com.elasticinbox.core.model.Mailbox;
import com.elasticinbox.core.model.Marker;
import com.elasticinbox.core.model.Message;
import com.elasticinbox.core.model.ReservedLabels;

public final class CassandraMessageDAO extends AbstractMessageDAO implements MessageDAO
{
	private final static Keyspace keyspace = CassandraDAOFactory.getKeyspace();
	private final static StringSerializer strSe = StringSerializer.get();

	private final static Logger logger = 
			LoggerFactory.getLogger(CassandraMessageDAO.class);

	@Override
	public Message getParsed(final Mailbox mailbox, final UUID messageId)
	{
		return MessagePersistence.fetch(mailbox.getId(), messageId, true);
	}

	@Override
	public InputStream getRaw(final Mailbox mailbox, final UUID messageId)
			throws IOException
	{
		return getBlobInputStream(mailbox, messageId);
	}

	@Override
	public Map<UUID, Message> getMessageIdsWithHeaders(final Mailbox mailbox,
			final int labelId, final UUID start, final int count, boolean reverse)
	{
		List<UUID> messageIds = 
				getMessageIds(mailbox, labelId, start, count, reverse);

		return MessagePersistence.fetch(mailbox.getId(), messageIds, false);
	}

	@Override
	public List<UUID> getMessageIds(final Mailbox mailbox, final int labelId,
			final UUID start, final int count, final boolean reverse)
	{
		return LabelIndexPersistence.get(mailbox.getId(), labelId, start, count, reverse);
	}

	@Override
	public void put(final Mailbox mailbox, UUID messageId, Message message, InputStream in)
			throws IOException, OverQuotaException
	{
		URI uri = null;
		logger.info("Storing message: key={}", messageId.toString());

		// Check quota
		LabelCounters mailboxCounters = LabelCounterPersistence.get(
				mailbox.getId(), ReservedLabels.ALL_MAILS.getLabelId());

		long requiredBytes = mailboxCounters.getTotalBytes() + message.getSize();
		long requiredCount = mailboxCounters.getTotalMessages() + 1;

		if ((requiredBytes > Configurator.getDefaultQuotaBytes()) ||
			(requiredCount > Configurator.getDefaultQuotaCount()))
		{
			logger.info("Mailbox is over quota: {} size={}/{}, count={}/{}",
					new Object[] { mailbox.getId(), requiredBytes,
							Configurator.getDefaultQuotaBytes(), requiredCount,
							Configurator.getDefaultQuotaCount() });

			throw new OverQuotaException("Mailbox is over quota");
		}

		// Order is important, add to label after message written

		// store blob
		if (in != null) {
			try {
				// get blob name
				String blobName = new BlobNameBuilder()
						.setMailbox(mailbox).setMessageId(messageId)
						.setMessageSize(message.getSize()).build();

				// store message in blobstore
				uri = BlobProxy.write(blobName, in, message.getSize());
				
				// update location in metadata
				message.setLocation(uri);
			} catch (Exception e) {
				throw new IOException("Failed to store blob: ", e);
			} finally {
				if(in != null) 
					in.close();
			}
		}

		// automatically add "all" label to all new messages
		message.addLabel(ReservedLabels.ALL_MAILS.getLabelId());

		try {
			// begin batch operation
			Mutator<String> m = createMutator(keyspace, strSe);

			// store metadata
			MessagePersistence.persistMessage(m, mailbox.getId(), messageId, message);
			// add indexes
			LabelIndexPersistence.add(m, mailbox.getId(), messageId, message.getLabels());
			// update counters
			LabelCounterPersistence.add(m, mailbox.getId(), message.getLabels(), message.getLabelCounters());

			// commit batch operation
			m.execute();
		} catch (Exception e) {
			logger.warn(
					"Unable to store metadata for message {}, deleting blob {}",
					messageId, uri);

			// rollback
			if (uri != null)
				BlobProxy.delete(uri);

			throw new IOException("Unable to store message metadata: ", e);
		}
	}

	@Override
	public void addMarker(final Mailbox mailbox, final Set<Marker> markers,
			final List<UUID> messageIds)
	{
		if(markers.isEmpty() || messageIds.isEmpty())
			return;

		Labels labels = null;

		// get label stats, only required for SEEN marker change
		if (markers.contains(Marker.SEEN))
			labels = countStatsForMessageLabels(mailbox, messageIds);

		// build list of attributes
		Set<String> attributes = new HashSet<String>(markers.size());
		for (Marker marker : markers) {
			String a = new StringBuilder(Marshaller.CN_MARKER_PREFIX)
					.append(marker.toInt()).toString();
			attributes.add(a);
		}

		// begin batch operation
		Mutator<String> m = createMutator(keyspace, strSe);

		// add markers to messages
		MessagePersistence.persistAttributes(m, mailbox.getId(), messageIds, attributes);

		// decrement new message counter for each of the labels
		if (markers.contains(Marker.SEEN)) {
			for (Integer labelId : labels.getIds()) {
				LabelCounters labelCounters = new LabelCounters();
				labelCounters.setNewMessages(labels.getLabelCounters(labelId).getNewMessages());
				LabelCounterPersistence.subtract(m, mailbox.getId(), labelId, labelCounters);
			}
		}

		// commit batch operation
		m.execute();
	}

	@Override
	public void removeMarker(final Mailbox mailbox, final Set<Marker> markers,
			final List<UUID> messageIds)
	{
		if(markers.isEmpty() || messageIds.isEmpty())
			return;

		Labels labels = null;

		// get label stats, only required for SEEN marker change
		if (markers.contains(Marker.SEEN))
			labels = countStatsForMessageLabels(mailbox, messageIds);

		// build list of attributes
		Set<String> attributes = new HashSet<String>(markers.size());
		for (Marker marker : markers) {
			String a = new StringBuilder(Marshaller.CN_MARKER_PREFIX)
					.append(marker.toInt()).toString();
			attributes.add(a);
		}

		// begin batch operation
		Mutator<String> m = createMutator(keyspace, strSe);

		// remove markers from messages
		MessagePersistence.deleteAttributes(m, mailbox.getId(), messageIds, attributes);

		// increment new message counter for each of the labels
		if (markers.contains(Marker.SEEN)) {
			for (Integer labelId : labels.getIds()) {
				LabelCounters labelCounters = new LabelCounters();

				// only seen messages will be marked as new, so we count only seen 
				Long seenMessages = 
						labels.getLabelCounters(labelId).getTotalMessages()
						- labels.getLabelCounters(labelId).getNewMessages();
				labelCounters.setNewMessages(seenMessages);

				LabelCounterPersistence.add(m, mailbox.getId(), labelId, labelCounters);
			}
		}

		// commit batch operation
		m.execute();
	}

	@Override
	public void addLabel(final Mailbox mailbox, final Set<Integer> labelIds,
			final List<UUID> messageIds)
	{
		if(labelIds.isEmpty() || messageIds.isEmpty())
			return;

		// build list of attributes
		Set<String> attributes = new HashSet<String>(labelIds.size());
		for (Integer labelId : labelIds) {
			String a = new StringBuilder(Marshaller.CN_LABEL_PREFIX)
					.append(labelId).toString();
			attributes.add(a);
		}

		// begin batch operation
		Mutator<String> m = createMutator(keyspace, strSe);

		// get message stats for counters
		LabelCounters labelCounters = countStatsForMessages(mailbox, messageIds);

		// add labels to messages
		MessagePersistence.persistAttributes(m, mailbox.getId(), messageIds, attributes);

		// add messages to label index
		LabelIndexPersistence.add(m, mailbox.getId(), messageIds, labelIds);

		// increment label counters
		LabelCounterPersistence.add(m, mailbox.getId(), labelIds, labelCounters);

		// commit batch operation
		m.execute();
	}

	@Override
	public void removeLabel(final Mailbox mailbox, final Set<Integer> labelIds,
			final List<UUID> messageIds)
	{
		if(labelIds.isEmpty() || messageIds.isEmpty())
			return;

		// label "all" cannot be removed from message
		if (labelIds.contains(ReservedLabels.ALL_MAILS.getLabelId()))
			throw new IllegalLabelException("This label cannot be removed");

		// build list of attributes
		Set<String> attributes = new HashSet<String>(labelIds.size());
		for (Integer labelId : labelIds) {
			String a = new StringBuilder(Marshaller.CN_LABEL_PREFIX)
					.append(labelId).toString();
			attributes.add(a);
		}

		// begin batch operation
		Mutator<String> m = createMutator(keyspace, strSe);

		// get message stats for counters, negative
		LabelCounters labelCounters = countStatsForMessages(mailbox, messageIds);

		// remove labels from messages
		MessagePersistence.deleteAttributes(m, mailbox.getId(), messageIds, attributes);

		// remove messages from label index
		LabelIndexPersistence.remove(m, mailbox.getId(), messageIds, labelIds);

		// decrement label counters (add negative value)
		LabelCounterPersistence.subtract(m, mailbox.getId(), labelIds, labelCounters);

		// commit batch operation
		m.execute();
	}

	@Override
	public void delete(final Mailbox mailbox, final List<UUID> messageIds)
	{
		// begin batch operation
		Mutator<String> m = createMutator(keyspace, strSe);

		// add messages to purge index
		PurgeIndexPersistence.add(m, mailbox.getId(), messageIds);

		// get label stats
		Labels labels = countStatsForMessageLabels(mailbox, messageIds);

		// remove from all label indexes, including "all"
		LabelIndexPersistence.remove(m, mailbox.getId(), messageIds, labels.getIds());
		
		// decrement label counters (add negative value)
		for (Integer labelId : labels.getIds()) {
			LabelCounterPersistence.subtract(m, mailbox.getId(), labelId, labels.getLabelCounters(labelId));
		}

		// commit batch operation
		m.execute();
	}

	@Override
	public void purge(final Mailbox mailbox, final Date age)
	{
		Map<UUID, UUID> purgeIndex = null;

		logger.debug("Purging all messages older than {} for {}", age.toString(), mailbox);

		// loop until we process all purged items
		do {
			// begin batch operation
			Mutator<String> m = createMutator(keyspace, strSe);

			// get message IDs of messages to purge
			purgeIndex = PurgeIndexPersistence.get(mailbox.getId(), age,
					CassandraDAOFactory.MAX_COLUMNS_PER_REQUEST);

			// get metadata/blob location
			Map<UUID, Message> messages = 
					MessagePersistence.fetch(mailbox.getId(), purgeIndex.values(), false);

			// delete message sources from object store
			for(UUID messageId : messages.keySet()) {
				BlobProxy.delete(messages.get(messageId).getLocation());
			}

			// purge expired (older than age) messages
			MessagePersistence.deleteMessage(m, mailbox.getId(), purgeIndex.values());

			// remove from purge index
			PurgeIndexPersistence.remove(m, mailbox.getId(), purgeIndex.keySet());

			// commit batch operation
			m.execute();
		}
		while (purgeIndex.size() >= CassandraDAOFactory.MAX_COLUMNS_PER_REQUEST);
	}

	/**
	 * Get blob contents
	 * 
	 * @param mailbox
	 * @param messageId
	 * @return
	 * @throws IOException
	 */
	private InputStream getBlobInputStream(final Mailbox mailbox,
			final UUID messageId) throws IOException
	{
		// fetch message metadata
		Message metadata = MessagePersistence.fetch(mailbox.getId(), messageId, false);
		return BlobProxy.read(metadata.getLocation());
	}

	/**
	 * Get aggregated {@link LabelCounter} stats for the list of messages
	 * 
	 * @param mailbox
	 * @param messageIds
	 * @return
	 */
	private static LabelCounters countStatsForMessages(final Mailbox mailbox,
			final List<UUID> messageIds)
	{
		LabelCounters labelCounters = new LabelCounters();

		// get message headers
		Map<UUID, Message> messages = 
				MessagePersistence.fetch(mailbox.getId(), messageIds, false);

		for(UUID messageId : messages.keySet()) {
			labelCounters.add(messages.get(messageId).getLabelCounters());
		}

		return labelCounters;
	}

	/**
	 * Get aggregated {@link LabelCounter} stats for each label in the list of
	 * messages. Results aggregated by label ID.
	 * 
	 * @param mailbox
	 * @param messageIds
	 * @return
	 */
	private static Labels countStatsForMessageLabels(final Mailbox mailbox,
			final List<UUID> messageIds)
	{
		Labels labels = new Labels();

		// get message headers
		Map<UUID, Message> headers = 
				MessagePersistence.fetch(mailbox.getId(), messageIds, false);

		// get all labels of all messages, including label "all"
		for (UUID messageId : headers.keySet())
		{
			Set<Integer> messageLabels = headers.get(messageId).getLabels();

			for (int labelId : messageLabels) {
				if(!labels.containsId(labelId))
					labels.addCounters(labelId, new LabelCounters());

				labels.getLabelCounters(labelId).add(
						headers.get(messageId).getLabelCounters());
			}
		}

		return labels;
	}

}
