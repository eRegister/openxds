package gov.nist.registry.ws.serviceclasses;

import gov.nist.registry.common2.exception.XdsException;
import gov.nist.registry.common2.exception.XdsInternalException;
import gov.nist.registry.common2.exception.XdsValidationException;
import gov.nist.registry.common2.logging.LoggerException;
import gov.nist.registry.common2.registry.MetadataSupport;
import gov.nist.registry.common2.registry.XdsCommon;
import gov.nist.registry.common2.xml.Util;
import gov.nist.registry.ws.AdhocQueryRequest;

import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openhealthtools.openxds.xca.Aggregator;
import org.openhealthtools.openxds.xca.QueryAggregator;
import org.openhealthtools.openxds.xca.SoapCall;
import org.openhealthtools.openxds.xca.api.XcaIG;

import com.misyshealthcare.connect.net.IConnectionDescription;

public class XcaRegistry extends RegistryB {
	private final static Log logger = LogFactory.getLog(XcaRegistry.class);
	
	private XcaIG actor = Ig.getActor();
		
	protected void validateQueryInputDecoration(OMElement sor, AdhocQueryRequest a)
	throws XdsValidationException {

		String home = getHomeParameter(sor, a);
		boolean hasHome = home != null && !home.equals("");
		boolean homeRequired = a.requiresHomeInXGQ(sor);

		if (homeRequired && !hasHome) 
			throw new XdsValidationException("This endpoint simulates Stored Query in the presence of XCA, homeCommunityId is required on this Stored Query");
	}

	protected void decorateQueryOutput(OMElement sor, AdhocQueryRequest a, OMElement result) throws XdsValidationException {
		//no need to setHome as it is already taken care of during aggregation.
	}

	protected OMElement processAdhocQueryRequest(AdhocQueryRequest a, final OMElement ahqr) throws AxisFault, XdsException, XdsValidationException, LoggerException {
		Set<String> rgs = Ig.rgQueryMap.keySet();

		int requestNum = rgs.size();
		Aggregator ag = null;
		String homeFromRequest = getHomeParameter(ahqr, a);
		if (homeFromRequest != null && !homeFromRequest.equals("")) {
			//If the home is specified, send the request to only that home community. 
			requestNum = 1;
			ag = new QueryAggregator(requestNum, log_message);
			if (rgs.contains(homeFromRequest)) {
				//request to the remote responding gateway community
				IConnectionDescription rgConnection = Ig.rgQueryMap.get(homeFromRequest);
				SoapCall sc = new SoapCall(rgConnection, ahqr,  "urn:ihe:iti:2007:CrossGatewayQuery", homeFromRequest, false/*mtom*/, ag, this);
				Ig.exec.execute( sc );				
			} else if (homeFromRequest.equals(Ig.home)) {
				//request to the local community
				if (actor.getRegistryClientConnection() != null) {
					//forward to the local registry
					SoapCall sc = new SoapCall(actor.getRegistryClientConnection(), ahqr,  "urn:ihe:iti:2007:RegistryStoredQuery", Ig.home, false/*mtom*/, ag, this);
					Ig.exec.execute( sc );
				} else {
					throw new XdsInternalException("Unconfigured XdsRegistryClient for home ID:" + homeFromRequest);					
				}
			} else {
				throw new XdsInternalException("Unrecogized home ID:" + homeFromRequest);
			}
		} else {	
			//Request to all available communities
			if (Ig.home != null && actor.getRegistryClientConnection() != null) {
				requestNum++;
			}
			ag = new QueryAggregator(requestNum, log_message);

			for (String rgHomeId : rgs) {
				//forward to each gateway
				IConnectionDescription rgConnection = Ig.rgQueryMap.get(rgHomeId);

				OMElement request = Util.deep_copy(ahqr);
				OMElement aq = request.getFirstChildWithName(new QName(MetadataSupport.ebRIMns3_uri, "AdhocQuery" ));
				aq.addAttribute("home", rgHomeId, null);

				SoapCall sc = new SoapCall(rgConnection, request,  "urn:ihe:iti:2007:CrossGatewayQuery", rgHomeId, false/*mtom*/, ag, this);
				Ig.exec.execute( sc );
			}				
			if (Ig.home != null && actor.getRegistryClientConnection() != null) {
				//forward to the local registry
				OMElement request = Util.deep_copy(ahqr);
				OMElement aq = request.getFirstChildWithName(new QName(MetadataSupport.ebRIMns3_uri, "AdhocQuery" ));
				aq.addAttribute("home", Ig.home, null);

				SoapCall sc = new SoapCall(actor.getRegistryClientConnection(), request,  "urn:ihe:iti:2007:RegistryStoredQuery", Ig.home, false/*mtom*/, ag, this);
				Ig.exec.execute( sc );
			}
		}
		
		//wait for and aggregate responses	
		ag.waitForAll();

		a.transaction_type = XdsCommon.SQ_transaction;
		a.init(ag.getResponse(), XdsCommon.xds_b, getMessageContext());

		return ag.getResponse().getResponse();
	}
	
}


