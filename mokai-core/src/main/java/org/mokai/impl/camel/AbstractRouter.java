package org.mokai.impl.camel;

import java.util.Date;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.RecipientList;
import org.mokai.Acceptor;
import org.mokai.ConnectorService;
import org.mokai.Message;
import org.mokai.Processor;
import org.mokai.RoutingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class of {@link ApplicationsRouter} and {@link ConnectionsRouter}.
 *
 * @author German Escobar
 */
public abstract class AbstractRouter {

	private Logger log = LoggerFactory.getLogger(AbstractRouter.class);

	protected RoutingEngine routingEngine;

	/**
	 * This method is called from Apache Camel to route messages to connections or applications.
	 *
	 * @param exchange
	 * @return an Apache Camel endpoint URI.
	 */
	@RecipientList
	public final String route(Exchange exchange) {
		Message message = exchange.getIn().getBody(Message.class);

		long startTime = new Date().getTime();
		String route =  route(message);
		long endTime = new Date().getTime();

		log.trace("route took: " + (endTime - startTime) + " millis");

		return route;
	}

	/**
	 * Made public for testing.
	 *
	 * @param message
	 * @return
	 */
	public String route(Message message) {

		// check if the message already has a destination
		if (message.getDestination() != null) {

			ConnectorService connectorService = getConnectorService(message.getDestination());

			if (connectorService != null && acceptsMessage(connectorService, message, false)) {
				return getUriPrefix() + connectorService.getId();
			}

			// unroutable
			message.setStatus(Message.STATUS_UNROUTABLE);
			return getUnroutableMessagesUri();
		}

		// try to route the message
		List<ConnectorService> connectorServices = getConnectorServices();
		for (ConnectorService connectorService : connectorServices) {
			if (acceptsMessage(connectorService, message, true)) {
				return getUriPrefix() + connectorService.getId();
			}
		}

		// unroutable
		message.setStatus(Message.STATUS_UNROUTABLE);
		return getUnroutableMessagesUri();
	}

	/**
	 * Tells whether a connector service accepts a message or not.
	 *
	 * @param connectorService
	 * @param message
	 * @param checkAcceptors if we have to check the acceptors
	 * @return true if the connector service accepts a message, false otherwise.
	 */
	private boolean acceptsMessage(ConnectorService connectorService, Message message, boolean checkAcceptors) {
		// return if not a Processor instance
		if (!Processor.class.isInstance(connectorService.getConnector())) {
			return false;
		}

		Processor processor = (Processor) connectorService.getConnector();

		// check if the processor supports the message
		boolean supported = false;
		if (processor.supports(message)) {
			supported = true;
		}

		// check the acceptors only if the message is supported
		if (supported && checkAcceptors) {
			List<Acceptor> acceptors = connectorService.getAcceptors();
			for (Acceptor acceptor : acceptors) {
				try {
					if (acceptor.accepts(message)) {
						return true;
					}
				} catch (Exception e) {
					log.error("Exception while calling Acceptor " + acceptor + ": " + e.getMessage(), e);
				}
			}

			return false;
		}

		return supported;
	}

	public final void setRoutingEngine(RoutingEngine routingContext) {
		this.routingEngine = routingContext;
	}

	/**
	 * Retrieves the list of connector services that the router will test to route the message.
	 *
	 * @return a List of {@link ConnectorService} objects.
	 */
	protected abstract List<ConnectorService> getConnectorServices();

	/**
	 * Retrieves the connector service given an id.
	 *
	 * @param id the id of the connector service to retrieve.
	 * @return the {@link ConnectorService} object with the given id or null if not found.
	 */
	protected abstract ConnectorService getConnectorService(String id);

	/**
	 * @return the URI endpoint prefix where the message should me sent if the connector service accepts the
	 * message.
	 */
	protected abstract String getUriPrefix();

	/**
	 * @return the URI enpoint of the unroutable messages.
	 */
	protected abstract String getUnroutableMessagesUri();

}
