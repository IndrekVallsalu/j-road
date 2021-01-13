/**
 * Copyright 2015 Nortal Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 **/

package com.nortal.jroad.wsdl;

import java.util.Properties;

import javax.annotation.Resource;
import javax.wsdl.Definition;
import javax.wsdl.Message;
import javax.wsdl.Part;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.schema.Schema;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.StringUtils;
import org.springframework.ws.wsdl.wsdl11.ProviderBasedWsdl4jDefinition;
import org.springframework.ws.wsdl.wsdl11.Wsdl11Definition;
import org.springframework.ws.wsdl.wsdl11.provider.InliningXsdSchemaTypesProvider;
import org.springframework.ws.wsdl.wsdl11.provider.SuffixBasedMessagesProvider;
import org.springframework.xml.xsd.XsdSchema;
import org.springframework.xml.xsd.XsdSchemaCollection;
import org.w3c.dom.Element;

import com.nortal.jroad.mapping.XTeeEndpointMapping;
import com.nortal.jroad.model.XRoadHeader;
import org.w3c.dom.NodeList;

/**
 * Generates WSDL for X-Road services from a schema, much like Spring's WSDL generator it delegates to
 * <code>InliningXsdSchemaTypesProvider</code>, <code>DefaultMessagesProvider</code>,
 * <code>SuffixBasedPortTypesProvider</code>, <code>ProviderBasedWsdl4jDefinition</code> and {@link XTeeSoapProvider}
 * underneath.
 *
 * @author Dmitri Danilkin
 * @author Lauri Lättemäe (lauri.lattemae@nortal.com) - protocol 4.0
 */
public class XTeeWsdlDefinition implements Wsdl11Definition, InitializingBean {

  private final InliningXsdSchemaTypesProvider typesProvider = new InliningXsdSchemaTypesProvider();
  private final SuffixBasedMessagesProvider messagesProvider = new XTeeMessagesProvider();
  private final XTeePortTypesProvider portTypesProvider = new XTeePortTypesProvider();
  private final XTeeSoapProvider soapProvider = new XTeeSoapProvider();
  private final ProviderBasedWsdl4jDefinition delegate = new ProviderBasedWsdl4jDefinition();

  private String serviceName;

  @Resource(name = "xteeDatabase")
  private String xRoadDatabase;

  private String xRoadTargetNamespace;

  @Resource
  private XTeeEndpointMapping xRoadEndpointMapping;

  public static final String XROAD_HEADER = "requestheader";
  public static final String XROAD_NAMESPACE = "http://x-road.eu/xsd/xroad.xsd";
  public static final String XROAD_PREFIX = "xrd";
  public static final String XROAD_IDEN_NAMESPACE = "http://x-road.eu/xsd/identifiers";
  public static final String XROAD_REPR_PREFIX = "repr";
  public static final String XROAD_REPR_NS_URI = "http://x-road.eu/xsd/representation.xsd";

  public XTeeWsdlDefinition() {
    delegate.setTypesProvider(typesProvider);
    delegate.setMessagesProvider(messagesProvider);
    delegate.setPortTypesProvider(portTypesProvider);
    delegate.setBindingsProvider(soapProvider);
    delegate.setServicesProvider(soapProvider);
  }

  /**
   * Sets the single XSD schema to inline. Either this property, or {@link #setSchemaCollection(XsdSchemaCollection)
   * schemaCollection} must be set.
   */
  public void setSchema(final XsdSchema schema) {
    typesProvider.setSchema(schema);
  }

  /**
   * Sets the XSD schema collection to inline. Either this property, or {@link #setSchema(XsdSchema) schema} must be
   * set.
   */
  public void setSchemaCollection(XsdSchemaCollection schemaCollection) {
    typesProvider.setSchemaCollection(schemaCollection);
  }

  /** Sets the port type name used for this definition. Required. */
  public void setPortTypeName(String portTypeName) {
    portTypesProvider.setPortTypeName(portTypeName);
  }

  /** Sets the suffix used to detect request elements in the schema. */
  private void setRequestSuffix(String requestSuffix) {
    portTypesProvider.setRequestSuffix(requestSuffix);
    messagesProvider.setRequestSuffix(requestSuffix);
  }

  /** Sets the suffix used to detect response elements in the schema. */
  private void setResponseSuffix(String responseSuffix) {
    portTypesProvider.setResponseSuffix(responseSuffix);
    messagesProvider.setResponseSuffix(responseSuffix);
  }

  /** Sets the suffix used to detect fault elements in the schema. */
  public void setFaultSuffix(String faultSuffix) {
    portTypesProvider.setFaultSuffix(faultSuffix);
    messagesProvider.setFaultSuffix(faultSuffix);
  }

  public void setUse(String use) {
    soapProvider.setUse(use);
  }

  /**
   * Sets the SOAP Actions for this binding. Keys are {@link javax.wsdl.BindingOperation#getName() binding operation
   * names}; values are {@link javax.wsdl.extensions.soap.SOAPOperation#getSoapActionURI() SOAP Action URIs}.
   *
   * @param soapActions the soap
   */
  public void setSoapActions(Properties soapActions) {
    soapProvider.setSoapActions(soapActions);
  }

  /** Sets the service name. */
  public void setServiceName(String serviceName) {
    soapProvider.setServiceName(serviceName);
    this.serviceName = serviceName;
  }

  public void afterPropertiesSet() throws Exception {
    soapProvider.setLocationUri("http://SECURITY_SERVER/cgi-bin/consumer_proxy");
    soapProvider.setXRoadDatabase(xRoadDatabase);
    soapProvider.setXRoadEndpointMapping(xRoadEndpointMapping);
    portTypesProvider.setXRoadEndpointMapping(xRoadEndpointMapping);

    setRequestSuffix(SuffixBasedMessagesProvider.DEFAULT_REQUEST_SUFFIX);
    setResponseSuffix(SuffixBasedMessagesProvider.DEFAULT_RESPONSE_SUFFIX);

    String targetNamespace = StringUtils.hasText(xRoadTargetNamespace)
                                                                       ? xRoadTargetNamespace
                                                                       : "http://" + xRoadDatabase + ".x-road.eu";
    delegate.setTargetNamespace(targetNamespace);

    if (!StringUtils.hasText(delegate.getTargetNamespace()) && typesProvider.getSchemaCollection() != null
        && typesProvider.getSchemaCollection().getXsdSchemas().length > 0) {
      XsdSchema schema = typesProvider.getSchemaCollection().getXsdSchemas()[0];
      delegate.setTargetNamespace(schema.getTargetNamespace());
    }
    if (!StringUtils.hasText(serviceName) && StringUtils.hasText(portTypesProvider.getPortTypeName())) {
      soapProvider.setServiceName(portTypesProvider.getPortTypeName() + "Service");
    }
    delegate.afterPropertiesSet();
    addXRoadExtensions(delegate.getDefinition());
  }

  public Source getSource() {
    return delegate.getSource();
  }

  public Definition getDefinition() {
    return delegate.getDefinition();
  }

  private void addXRoadExtensions(Definition definition) throws WSDLException {
    definition.addNamespace(XROAD_PREFIX, XROAD_NAMESPACE);
    definition.addNamespace(XROAD_REPR_PREFIX, XROAD_REPR_NS_URI);

    Message message = definition.createMessage();
    message.setQName(new QName(definition.getTargetNamespace(), XROAD_HEADER));

    addXroadHeaderPart(definition, message, XRoadHeader.CLIENT);
    addXroadHeaderPart(definition, message, XRoadHeader.SERVICE);
    addXroadHeaderPart(definition, message, XRoadHeader.REPRESENTED_PARTY);
    addXroadHeaderPart(definition, message, XRoadHeader.ID);
    addXroadHeaderPart(definition, message, XRoadHeader.USER_ID);
    addXroadHeaderPart(definition, message, XRoadHeader.PROTOCOL_VERSION);

    message.setUndefined(false);
    definition.addMessage(message);

    // Add XRoad & Third Party Representation schema import to the first schema
    for (Object ex : definition.getTypes().getExtensibilityElements()) {
      if (ex instanceof Schema) {
        Schema schema = (Schema) ex;

        boolean addXroadImportToSchema = true;
        boolean addXRoad3rdPartyImportToSchema = true;
        NodeList importList = schema.getElement().getElementsByTagName("import");
        for (int i = 0; i < importList.getLength(); i++) {
          if (((Element)importList.item(i)).getAttribute("namespace").equalsIgnoreCase(XROAD_NAMESPACE)) {
            addXroadImportToSchema = false;
          }
          if (((Element)importList.item(i)).getAttribute("namespace").equalsIgnoreCase(XROAD_REPR_NS_URI)) {
            addXRoad3rdPartyImportToSchema = false;
          }
        }

        if (addXroadImportToSchema) {
          Element xRoadImport = schema.getElement().getOwnerDocument().createElement(
              schema.getElement().getPrefix() == null ? "import" : schema.getElement().getPrefix() + ":import");
          xRoadImport.setAttribute("namespace", XROAD_NAMESPACE);
          xRoadImport.setAttribute("schemaLocation", XROAD_NAMESPACE);
          schema.getElement().insertBefore(xRoadImport, schema.getElement().getFirstChild());
        }
        if (addXRoad3rdPartyImportToSchema) {
          Element xRoadImport = schema.getElement().getOwnerDocument().createElement(
              schema.getElement().getPrefix() == null ? "import" : schema.getElement().getPrefix() + ":import");
          xRoadImport.setAttribute("namespace", XROAD_REPR_NS_URI);
          xRoadImport.setAttribute("schemaLocation", XROAD_REPR_NS_URI);
          schema.getElement().insertBefore(xRoadImport, schema.getElement().getFirstChild());
        }
        break;
      }
    }
  }

  private void addXroadHeaderPart(Definition definition, Message message, QName partName) {
    Part part = definition.createPart();
    part.setElementName(partName);
    part.setName(partName.getLocalPart());
    message.addPart(part);
  }

  public void setxRoadTargetNamespace(String xRoadTargetNamespace) {
    this.xRoadTargetNamespace = xRoadTargetNamespace;
  }
}
