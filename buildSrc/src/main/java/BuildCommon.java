/*******************************************************************************
 * Copyright (c) 2012, THE BOARD OF TRUSTEES OF THE LELAND STANFORD JUNIOR UNIVERSITY
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *    Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *    Neither the name of the STANFORD UNIVERSITY nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/

import org.json.JSONObject;
import org.cyclades.engine.stroma.xstroma.XSTROMARequestBuilder;
import org.cyclades.engine.stroma.xstroma.STROMARequestBuilder;
import org.cyclades.client.Http;
import org.cyclades.engine.stroma.xstroma.XSTROMABrokerResponse;
import org.cyclades.engine.stroma.xstroma.XSTROMABrokerRequest;
import org.cyclades.engine.stroma.STROMAResponse;
import org.cyclades.engine.stroma.xstroma.STROMARequest;
import org.cyclades.engine.util.TransactionIdentifier;
import org.cyclades.io.ResourceRequestUtils;
import org.cyclades.io.StreamUtils;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.io.InputStream;
import java.net.HttpURLConnection;

public class BuildCommon {

    public static void uploadFile (String url, String password, String targetSourceUploadPath, String targetDestinationUploadPath, 
            boolean deleteFirst) throws Exception {
        InputStream sourceInputStream = null;
        InputStream responseInputStream = null;
        try {
        	StringBuilder requestURI = new StringBuilder("/admin?uri=").append(targetDestinationUploadPath).append("&raw-response");
        	if (password != null) requestURI.append("&password=").append(password);
        	if (deleteFirst) ResourceRequestUtils.getData(url + requestURI.toString() + "&action=delete", null);
        	sourceInputStream = ResourceRequestUtils.getInputStream(targetSourceUploadPath, null);
        	Map<String, String> attributeMap = new HashMap<String, String>();
            attributeMap.put("Content-Type", "");
            HttpURLConnection connection = ResourceRequestUtils.getHttpURLConnection(url + requestURI.toString() + "&action=PUT", 
                    "PUT", sourceInputStream, attributeMap, -1, -1);
            responseInputStream = connection.getInputStream();
            String response = new String(StreamUtils.toByteArray(responseInputStream));
        	if (connection.getResponseCode() != 200) throw new Exception("Invalid response code returned: " + 
                connection.getResponseCode() + ": " + response);  
        } finally {
            try { sourceInputStream.close(); } catch (Exception e) {}
            try { responseInputStream.close(); } catch (Exception e) {}
        }
    }

    public static void reloadServiceEngine (String url) throws Exception {
        String result = new String(ResourceRequestUtils.getData(url + "?action=reload", null));
        if (result.indexOf("servicebroker") < 0) throw new Exception("Invalid Result Encountered " + result);
    }

    public static void reloadServiceEngineSafetyMode (String url) throws Exception {
        String result = new String(ResourceRequestUtils.getData(url + "?action=reload&uris=admin", null));
        if (result.indexOf("admin") < 0) throw new Exception("Invalid Result Encountered " + result);
    }

    public static void runXSTROMARequestsHTTP (List<XSTROMABrokerRequest> requests, String url) throws Exception {
        for (XSTROMABrokerRequest request : requests) {
            String xstromaResponseString =  new String(Http.execute(url + "/servicebroker", request));
            XSTROMABrokerResponse xstromaResponseObject = XSTROMABrokerResponse.parse(xstromaResponseString);
            if (!xstromaResponseObject.getServiceName().equals("servicebroker")) {
                STROMAResponse response = new STROMAResponse(xstromaResponseString);
                printSTROMAResponse(response);
                if (response.getErrorCode() != 0) throw new Exception("Error encountered in response: " + response.getErrorMessage());
            } else {
                printXSTROMAResponse(xstromaResponseObject);
                if (xstromaResponseObject.getOrchestrationFault()) throw new Exception("Orchestration fault encountered in response");
            }
        }
    }

    public static void runSTROMARequestsHTTP (List<STROMARequest> requests, String url) throws Exception {
        for (STROMARequest request : requests) {
            String stromaResponseString =  new String(Http.execute(url + "/" + request.getServiceName(), request));
            STROMAResponse response = new STROMAResponse(stromaResponseString);
            printSTROMAResponse(response);
            if (response.getErrorCode() != 0) throw new Exception("Error encountered in response: " + response.getErrorMessage());
        }
    }

    public static void printXSTROMAResponse (XSTROMABrokerResponse xstromaResponseObject) throws Exception {
        if (xstromaResponseObject.getOrchestrationFault()) System.out.println("Orchestration Fault Raised!!!!!");
        System.out.println("\nX-STROMA Level Parameters");
        System.out.println("error-code: " + xstromaResponseObject.getErrorCode());
        System.out.println("transaction-data: " + xstromaResponseObject.getTransactionData());
        System.out.println("service-agent: " + xstromaResponseObject.getServiceAgent());
        System.out.println("duration: " + xstromaResponseObject.getDuration());
        if (xstromaResponseObject.getErrorCode() != 0) {
            System.out.println("error-message: " + xstromaResponseObject.getErrorMessage());
            return;
        }
        for (STROMAResponse sr : xstromaResponseObject.getResponses()) printSTROMAResponse(sr);
    }

    public static void printSTROMAResponse (STROMAResponse sr) throws Exception {
        System.out.println("\n\tService (STROMA) Level Parameters");
        System.out.println("\tservice: " + sr.getServiceName());
        System.out.println("\taction: " + sr.getAction());
        System.out.println("\terror-code: " + sr.getErrorCode());
        System.out.println("\ttransaction-data: " + sr.getTransactionData());
        System.out.println("\tservice-agent: " + sr.getServiceAgent());
        System.out.println("\tduration: " + sr.getDuration());
        if (sr.getErrorCode() != 0) {
            System.out.println("\terror-message: " + sr.getErrorMessage());
        }
        // getData() retrieves any raw payload information embedded in the response (as a JSONObject or Node class type)
        // depending on the meta type requested
        //println "\t" + sr.getData().getClass()
        System.out.println("\t" + sr.getParameters());
    }
    
}
