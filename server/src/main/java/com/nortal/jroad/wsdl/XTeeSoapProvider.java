/**
 * Copyright 2015 Nortal Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 **/

package com.nortal.jroad.wsdl;

import com.nortal.jroad.annotation.XTeeService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.naming.NameNotFoundException;
import javax.wsdl.Binding;
import javax.wsdl.BindingInput;
import javax.wsdl.BindingOperation;
import javax.wsdl.BindingOutput;
import javax.wsdl.Definition;
import javax.wsdl.Input;
import javax.wsdl.Output;
import javax.wsdl.Port;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.ExtensionRegistry;
import javax.wsdl.extensions.UnknownExtensibilityElement;
import javax.wsdl.extensions.soap.SOAPBinding;
import javax.wsdl.extensions.soap.SOAPBody;
import javax.wsdl.extensions.soap.SOAPHeader;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.util.StringUtils;
import org.springframework.ws.server.endpoint.MessageEndpoint;
import org.springframework.ws.wsdl.wsdl11.provider.Soap11Provider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.nortal.jroad.mapping.XTeeEndpointMapping;
import com.nortal.jroad.model.XRoadHeader;

/**
 * Creates X-Road specific SOAP headers and bindings (<code>Document/Literal</code> is used). Used by
 * {@link XTeeWsdlDefinition}.
 *
 * @author Lauri Lättemäe (lauri.lattemae@nortal.com) - protocol 4.0
 */
public class XTeeSoapProvider extends Soap11Provider {
  private static final String ENCODED = "encoded";
  private static final String LITERAL = "literal";
  private static final String ENCODING = "http://schemas.xmlsoap.org/soap/encoding/";

  private String xRoadDatabase;
  private String use = LITERAL;

  private XTeeEndpointMapping xRoadEndpointMapping;

  private List<SOAPHeader> makeHeaders(Definition definition, String bindingMethod) throws WSDLException {
    List<SOAPHeader> list = new ArrayList<SOAPHeader>();
    List<String> parts = new ArrayList<String>(Arrays.asList(XRoadHeader.CLIENT.getLocalPart(),
        XRoadHeader.SERVICE.getLocalPart(), XRoadHeader.USER_ID.getLocalPart(), XRoadHeader.ID.getLocalPart(),
        XRoadHeader.PROTOCOL_VERSION.getLocalPart()));

    try {
      if (!StringUtils.isEmpty(bindingMethod)) {
        // Get endpoint
        MessageEndpoint endpoint = null;
        for (String method : xRoadEndpointMapping.getMethods()) {
          String methodTail = method.substring(method.indexOf('.') + 1).toLowerCase();
          if (methodTail.startsWith(bindingMethod.toLowerCase() + ".")) {
            endpoint = xRoadEndpointMapping.getMethodMap().get(method);
            break;
          }
        }

        if (endpoint == null)
          throw new NameNotFoundException();

        // Get annotation from endpoint and check that it contains a name.
        XTeeService xRoadServiceAnnotation = endpoint.getClass().getAnnotation(XTeeService.class);
        if (xRoadServiceAnnotation == null)
          throw new NameNotFoundException();

        if (xRoadServiceAnnotation.representedParty()) {
          parts.add(XRoadHeader.REPRESENTED_PARTY.getLocalPart());
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    ExtensionRegistry extReg = definition.getExtensionRegistry();
    for (String part: parts) {
      SOAPHeader header =
          (SOAPHeader) extReg.createExtension(BindingInput.class, new QName(SOAP_11_NAMESPACE_URI, "header"));
      header.setMessage(new QName(definition.getTargetNamespace(), XTeeWsdlDefinition.XROAD_HEADER));
      header.setPart(part);
      if (use.equalsIgnoreCase(LITERAL)) {
        header.setUse(LITERAL);
      } else {
        header.setUse(ENCODED);
        header.setEncodingStyles(Arrays.asList(ENCODING));
      }
      list.add(header);
    }

    return list;
  }

  @Override
  protected void populateBindingInput(Definition definition, BindingInput bindingInput, Input input)
      throws WSDLException {
    for (SOAPHeader header : makeHeaders(definition, input.getName().replace("Request", ""))) {
      bindingInput.addExtensibilityElement(header);
    }
    super.populateBindingInput(definition, bindingInput, input);
  }

  @Override
  protected void populateBindingOutput(Definition definition, BindingOutput bindingOutput, Output output)
      throws WSDLException {
    for (SOAPHeader header : makeHeaders(definition, null)) {
      bindingOutput.addExtensibilityElement(header);
    }
    super.populateBindingOutput(definition, bindingOutput, output);
  }

  @Override
  protected void populateBindingOperation(Definition definition, BindingOperation bindingOperation)
      throws WSDLException {
    super.populateBindingOperation(definition, bindingOperation);
    XTeeElement element = (XTeeElement) definition.getExtensionRegistry().createExtension(BindingOperation.class,
                                                                                          XTeeElement.VERSION_TYPE);
    String version = "v1";
    String name = bindingOperation.getName().toLowerCase();
    for (String method : xRoadEndpointMapping.getMethods()) {
      method = method.substring(method.indexOf('.') + 1).toLowerCase();
      if (method.startsWith(name + ".")) {
        version = method.substring(method.indexOf('.') + 1);
        break;
      }
    }
    element.setValue(version);
    bindingOperation.addExtensibilityElement(element);
  }

  @Override
  protected void populateBinding(Definition definition, Binding binding) throws WSDLException {
    definition.getExtensionRegistry().mapExtensionTypes(BindingOperation.class,
                                                        XTeeElement.VERSION_TYPE,
                                                        XTeeElement.class);
    definition.getExtensionRegistry().registerSerializer(BindingOperation.class,
                                                         XTeeElement.VERSION_TYPE,
                                                         new XTeeElement.XRoadElementSerializer());
    super.populateBinding(definition, binding);
  }

  @Override
  protected void populateSoapBinding(SOAPBinding soapBinding, Binding binding) throws WSDLException {
    soapBinding.setStyle("document");
    soapBinding.setTransportURI(getTransportUri());
  }

  @Override
  protected void populatePort(Definition definition, Port port) throws WSDLException {
    super.populatePort(definition, port);
    ExtensionRegistry extensionRegistry = definition.getExtensionRegistry();
    extensionRegistry.mapExtensionTypes(Port.class,
                                        new QName(XTeeWsdlDefinition.XROAD_NAMESPACE,
                                                  "address",
                                                  XTeeWsdlDefinition.XROAD_PREFIX),
                                        UnknownExtensibilityElement.class);
    UnknownExtensibilityElement element =
        (UnknownExtensibilityElement) extensionRegistry.createExtension(Port.class,
                                                                        new QName(XTeeWsdlDefinition.XROAD_NAMESPACE,
                                                                                  "address",
                                                                                  XTeeWsdlDefinition.XROAD_NAMESPACE));
    Document doc;
    try {
      doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    } catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
    Element xRoadAddr = doc.createElementNS(XTeeWsdlDefinition.XROAD_NAMESPACE, "address");
    xRoadAddr.setPrefix(XTeeWsdlDefinition.XROAD_PREFIX);
    xRoadAddr.setAttribute("producer", xRoadDatabase);
    element.setElement(xRoadAddr);
    port.addExtensibilityElement(element);
  }

  public void setXRoadDatabase(String xRoadDatabase) {
    this.xRoadDatabase = xRoadDatabase;
  }

  public void setXRoadEndpointMapping(XTeeEndpointMapping xRoadEndpointMapping) {
    this.xRoadEndpointMapping = xRoadEndpointMapping;
  }

  public void setUse(String use) {
    this.use = use;
  }

  @Override
  protected void populateSoapBody(SOAPBody soapBody) throws WSDLException {
    if (use.equalsIgnoreCase(LITERAL)) {
      soapBody.setUse(LITERAL);
    } else {
      soapBody.setUse(ENCODED);
      List<String> encStyles = new ArrayList<String>(1);
      encStyles.add(ENCODING);
      soapBody.setEncodingStyles(encStyles);
    }
  }
}
