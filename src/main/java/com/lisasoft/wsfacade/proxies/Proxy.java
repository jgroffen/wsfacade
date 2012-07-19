/*
 * Copyright 2012 LISAsoft - lisasoft.com. 
 * All rights reserved.
 *
 * This file is part of Web Services Facade.
 *
 * Web Services Facade is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Web Services Facade is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Web Services Facade.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.lisasoft.wsfacade.proxies;

import static com.lisasoft.wsfacade.config.ConfigKeys.CLIENT;
import static com.lisasoft.wsfacade.config.ConfigKeys.CONNECTION_PROXY;
import static com.lisasoft.wsfacade.config.ConfigKeys.ENABLED;
import static com.lisasoft.wsfacade.config.ConfigKeys.PASSWORD;
import static com.lisasoft.wsfacade.config.ConfigKeys.PORT;
import static com.lisasoft.wsfacade.config.ConfigKeys.PROXIES;
import static com.lisasoft.wsfacade.config.ConfigKeys.PROXY_MANAGED_URLS;
import static com.lisasoft.wsfacade.config.ConfigKeys.SERVER;
import static com.lisasoft.wsfacade.config.ConfigKeys.TYPE;
import static com.lisasoft.wsfacade.config.ConfigKeys.URL;
import static com.lisasoft.wsfacade.config.ConfigKeys.USERNAME;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;

import com.lisasoft.wsfacade.config.Translatable;
import com.lisasoft.wsfacade.generators.GeneratorFactory;
import com.lisasoft.wsfacade.generators.HttpGenerator;
import com.lisasoft.wsfacade.interpreters.HttpInterpreter;
import com.lisasoft.wsfacade.interpreters.InterpreterFactory;
import com.lisasoft.wsfacade.mappers.Mapper;
import com.lisasoft.wsfacade.mappers.MapperFactory;
import com.lisasoft.wsfacade.mappers.UnsupportedModelException;
import com.lisasoft.wsfacade.models.Model;

public class Proxy {
    private static final Logger log = Logger.getLogger(Proxy.class);
	public static final String PROXY_PREFIX = "/proxies";

	protected String name = null;
	protected String proxyUrl = null;
	protected Translatable clientType = null;
	protected Translatable serverType = null;
    protected String serverUrl = null;
    protected List<String> proxyManagedUrls = null;

	protected Mapper clientMapper = null;
	protected Mapper serverMapper = null;
	
	protected boolean connectionProxyEnabled = false;
	protected String connectionProxyHost = null;
	protected String connectionProxyUrl = null;
	protected int connectionProxyPort = 0;
	protected String connectionProxyUsername = null;
	protected String connectionProxyPassword = null;
	
    protected HttpInterpreter clientRequestInterpreter = null;
	protected HttpGenerator serverRequestGenerator = null;

	protected HttpInterpreter serverResponseInterpreter = null;
	protected HttpGenerator clientResponseGenerator = null;
	
	public void loadConfig(Context context) throws NamingException {
		proxyUrl = (String)context.lookup(String.format("%s.%s.%s", PROXIES, name, URL));
		
		if(!proxyUrl.startsWith("/")) {
			proxyUrl = "/" + proxyUrl;
		}
		if(!proxyUrl.startsWith(PROXY_PREFIX)) {
			proxyUrl = PROXY_PREFIX + proxyUrl;
		}

		String pmu = (String)context.lookup(String.format("%s.%s.%s", PROXIES, name, PROXY_MANAGED_URLS));
		
		if(pmu == null) {
			proxyManagedUrls = new ArrayList<String>();
		} else {
			proxyManagedUrls = Arrays.asList(pmu.split(","));
		}
		
		clientType = Translatable.valueOf(((String)context.lookup(String.format("%s.%s.%s.%s", PROXIES, name, CLIENT, TYPE))).toUpperCase());
		serverType = Translatable.valueOf(((String)context.lookup(String.format("%s.%s.%s.%s", PROXIES, name, SERVER, TYPE))).toUpperCase());
		serverUrl = (String)context.lookup(String.format("%s.%s.%s.%s", PROXIES, name, SERVER, URL));

		clientMapper = MapperFactory.getInstance(this, clientType, CLIENT, context);
		serverMapper = MapperFactory.getInstance(this, serverType, SERVER, context);

		clientRequestInterpreter = InterpreterFactory.getInstance(clientType, clientMapper);
		serverRequestGenerator = GeneratorFactory.getInstance(serverType, serverMapper);

		serverResponseInterpreter = InterpreterFactory.getInstance(serverType, serverMapper);
		clientResponseGenerator = GeneratorFactory.getInstance(clientType, clientMapper);

		// connection proxy details - if SIP can only access the translated service via a proxy
		connectionProxyEnabled = (Boolean)context.lookup(String.format("%s.%s", CONNECTION_PROXY, ENABLED));
		connectionProxyUrl = (String)context.lookup(String.format("%s.%s", CONNECTION_PROXY, URL));
		connectionProxyPort = (Integer)context.lookup(String.format("%s.%s", CONNECTION_PROXY, PORT));
		connectionProxyUsername = (String)context.lookup(String.format("%s.%s", CONNECTION_PROXY, USERNAME));
		connectionProxyPassword = (String)context.lookup(String.format("%s.%s", CONNECTION_PROXY, PASSWORD));
		
	}
	
	public void processRequest(String serviceRequestType, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {

		URL host = new URL("http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + proxyUrl);
		
		if(clientRequestInterpreter == null) {
			throw new ServletException("Failed to load translation details: clientRequestInterpreter was null");
		} else if(serverRequestGenerator == null) {
			throw new ServletException("Failed to load translation details: serverRequestGenerator was null");
		} else if(serverResponseInterpreter == null) {
			throw new ServletException("Failed to load translation details: serverResponseInterpreter was null");
		} else if(clientResponseGenerator == null) {
			throw new ServletException("Failed to load translation details: clientResponseGenerator was null");
		}

    	if(log.isDebugEnabled()) {
	    	log.debug(String.format("Request for Proxy (%s)", name));
    	}

    	// remove the proxyUrl, we only take what was after the proxy path and the query string.
    	String requestedUrl = ("/proxies" + request.getPathInfo()).replace(proxyUrl, "") + "?" + request.getQueryString();
    	if(proxyManagedUrls.contains(requestedUrl)) {
    		processProxyManagedUrl(host, requestedUrl, serviceRequestType, request, response);
    	} else {
	    	if(log.isDebugEnabled()) {
		    	log.debug(String.format("Step 1: Interpret Client Request (%s, %s)", 
		    			clientRequestInterpreter.getClass().getName(), 
		    			clientMapper.getClass().getName()));
	    	}
	    	Model model = clientRequestInterpreter.interpretRequest(request);
	    	model.properties.put("host", host.toString());
	    	
	    	if(log.isDebugEnabled()) {
		    	log.debug(String.format("Step 2: Generate Service Request (%s, %s)", 
		    			serverRequestGenerator.getClass().getName(), 
		    			serverMapper.getClass().getName()));
	    	}

	    	HttpRequestBase serverRequest = null;

	    	// Exceptions at this level should only be thrown for situations the 
	    	// interpreters / generators can't handle.
	    	try {
	    		serverRequest = serverRequestGenerator.generateRequest(model, serverUrl, serviceRequestType);
	    	} catch(UnsupportedModelException ume) {
		    	log.error("UnsupportedModelException while generating server request.", ume);
		    	throw new ServletException(ume);
	    	} catch(URISyntaxException use) {
		    	log.error("URISyntaxException while generating server request.", use);
		    	throw new ServletException(use);
		    }
	    	
//	    	HttpClient httpClient = null;
//
//	    	if(connectionProxyEnabled) {
//	    		ProxyClient proxyClient = new ProxyClient();
//	    		proxyClient.getHostConfiguration().setHost(serverUrl);
//	    		proxyClient.getHostConfiguration().setProxy(connectionProxyUrl, connectionProxyPort);
//
//	            // set the proxy credentials, only necessary for authenticating proxies
//	            proxyClient.getState().setProxyCredentials(
//	                new AuthScope(connectionProxyUrl, connectionProxyPort, null),
//	                new UsernamePasswordCredentials(connectionProxyUsername, connectionProxyPassword));
//	    		
//	    		
//	    		httpClient = proxyClient;
//	    	} else {
//		    	httpClient = new DefaultHttpClient();
//	    	}

	    	HttpClient httpClient = new DefaultHttpClient();
	    	HttpResponse serverResponse = httpClient.execute(serverRequest);
	
	    	if(log.isDebugEnabled()) {
		    	log.debug(String.format("Step 3: Interpret Server Response (%s, %s)", 
		    			serverResponseInterpreter.getClass().getName(), 
		    			serverMapper.getClass().getName()));
	    	}
	    	model = serverResponseInterpreter.interpretResponse(serverResponse);
	    	model.properties.put("host", host.toString());
	    	
	    	if(log.isDebugEnabled()) {
		    	log.debug(String.format("Step 4: Generate Client Response (%s, %s)", 
		    			clientResponseGenerator.getClass().getName(), 
		    			clientMapper.getClass().getName()));
	    	}
	    	
	    	try {
	    		clientResponseGenerator.generateResponse(model, response);
	    	} catch(UnsupportedModelException ume) {
		    	log.error("UnsupportedModelException while generating response to client.", ume);
		    	throw new ServletException(ume);
		    }
    	}
    }

	/**
	 * Override this method if you would like the proxy to deal with particular queries.
	 * @param host
	 * @param url
	 * @param serviceRequestType
	 * @param request
	 * @param response
	 * @throws ServletException
	 * @throws IOException
	 */
	protected void processProxyManagedUrl(URL host, String url, String serviceRequestType, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
		
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getProxyUrl() {
		return proxyUrl;
	}

	public void setProxyUrl(String proxyUrl) {
		this.proxyUrl = proxyUrl;
	}

	public Translatable getClientType() {
		return clientType;
	}

	public void setClientType(Translatable clientType) {
		this.clientType = clientType;
	}

	public Translatable getServerType() {
		return serverType;
	}

	public void setServerType(Translatable serverType) {
		this.serverType = serverType;
	}

	public String getServerUrl() {
		return serverUrl;
	}

	public void setServerUrl(String serverUrl) {
		this.serverUrl = serverUrl;
	}

	public Mapper getClientMapper() {
		return clientMapper;
	}

	public Mapper getServerMapper() {
		return serverMapper;
	}
}