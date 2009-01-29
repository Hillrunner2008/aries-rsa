package org.apache.cxf.dosgi.dsw;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.jdom.Element;
import org.jdom.Namespace;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.discovery.ServiceEndpointDescription;

public class OsgiUtilsTest extends TestCase {

    public void testGetPublishableInterfacesAll() throws Exception {
        doTestGetPublishableInterfaces("foo,bar,snafu",
                                       new String[] {"snafu", "foo", "bar"},
                                       new String[] {"foo", "bar", "snafu"});
    }

    public void testGetPublishableInterfacesSubset() throws Exception {
        doTestGetPublishableInterfaces("foo,snafu",
                                       new String[] {"snafu", "foo", "bar"},
                                       new String[] {"foo", "snafu"});
    }

    public void testGetPublishableInterfacesSuperset() throws Exception {
        doTestGetPublishableInterfaces("foo,bar,snafu",
                                       new String[] {"snafu", "bar"},
                                       new String[] {"bar", "snafu"});
    }

    public void testGetPublishableInterfacesNonexistant() throws Exception {
        doTestGetPublishableInterfaces("foo,bar,tofu",
                                       new String[] {"snafu", "foo", "bar"},
                                       new String[] {"foo", "bar"});
    }

    public void testGetPublishableInterfacesWildcarded() throws Exception {
        doTestGetPublishableInterfaces("*",
                                       new String[] {"snafu", "foo", "bar"},
                                       new String[] {"snafu", "foo", "bar"});
    }

    public void doTestGetPublishableInterfaces(String requested, 
                                               String[] actual, 
                                               String[] expected)
        throws Exception {
        ServiceEndpointDescription sd =
            EasyMock.createMock(ServiceEndpointDescription.class);
        ServiceReference sref = EasyMock.createMock(ServiceReference.class);
        EasyMock.expect(sd.getProperty(Constants.REMOTE_INTERFACES_PROPERTY)).andReturn(requested);
        EasyMock.expect(sref.getProperty(org.osgi.framework.Constants.OBJECTCLASS)).andReturn(actual);
        EasyMock.replay(sd);
        EasyMock.replay(sref);
        
        String[] ret = OsgiUtils.getPublishableInterfaces(sd, sref);

        assertNotNull(ret);
        assertEquals(expected.length, ret.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], ret[i]);
        }

        EasyMock.verify();
    }

    public void testGetRemoteReferencesFromRegistrationProperties() {
        final Map<String, Object> props = new HashMap<String, Object>();
        props.put(org.osgi.framework.Constants.OBJECTCLASS, new String [] {"myClass"});
        props.put(Constants.REMOTE_INTERFACES_PROPERTY, "*");
        
        Bundle b = EasyMock.createNiceMock(Bundle.class);        
        EasyMock.replay(b);
        
        ServiceReference sr = EasyMock.createMock(ServiceReference.class);
        // set behaviour for getPropertyKeys() and getProperty() based on the map above.
        EasyMock.expect(sr.getPropertyKeys()).
            andReturn(props.keySet().toArray(new String [] {})).anyTimes();
        EasyMock.expect(sr.getProperty((String) EasyMock.anyObject())).
            andAnswer(new IAnswer<Object>() {
                public Object answer() throws Throwable {
                    return props.get(EasyMock.getCurrentArguments()[0]);
                }                
            }).anyTimes();
        EasyMock.expect(sr.getBundle()).andReturn(b).anyTimes();
        EasyMock.replay(sr);
        
        // Actual test starts here
        ServiceEndpointDescription sd = OsgiUtils.getRemoteReference(sr, true);
        assertEquals("*", sd.getProperties().get(Constants.REMOTE_INTERFACES_PROPERTY));
        
        EasyMock.verify(sr);
    }
    
    public void testNoIntentMap() {
        Bundle b = EasyMock.createNiceMock(Bundle.class);
        EasyMock.replay(b);
        BundleContext bc = EasyMock.createNiceMock(BundleContext.class);
        EasyMock.expect(bc.getBundle()).andReturn(b).anyTimes();
        EasyMock.replay(bc);
        
        assertNull(OsgiUtils.readIntentMap(bc));
        assertNotNull(OsgiUtils.getIntentMap(bc));
    }    
    
    public void testIntentsParsingAndFormatting() {
        String initial = "A SOAP_1.1 integrity";

        String[] expected = {"A", "SOAP_1.1", "integrity"};
        String[] actual = OsgiUtils.parseIntents(initial);
        assertTrue(Arrays.equals(expected, actual));
        
        assertEquals(initial, OsgiUtils.formatIntents(actual));
    }
    
    public void testNoRemoteServicesXMLFiles() {
        Bundle b = EasyMock.createNiceMock(Bundle.class);
        EasyMock.replay(b);
        
        List<Element> rsElements = OsgiUtils.getAllDescriptionElements(b);
        assertEquals(0, rsElements.size());        
    }
    
    public void testRemoteServicesXMLFiles() {
        URL rs1URL = getClass().getResource("/test-resources/rs1.xml");
        
        Bundle b = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(b.findEntries(
            EasyMock.eq("OSGI-INF/remote-service"), 
            EasyMock.eq("*.xml"), EasyMock.anyBoolean())).andReturn(
                Collections.enumeration(Arrays.asList(rs1URL))).anyTimes();
        EasyMock.replay(b);
        
        List<Element> rsElements = OsgiUtils.getAllDescriptionElements(b);
        assertEquals(2, rsElements.size());
        Namespace ns = Namespace.getNamespace("http://www.osgi.org/xmlns/sd/v1.0.0");
        assertEquals("SomeService", rsElements.get(0).getChild("provide", ns).getAttributeValue("interface"));
        assertEquals("SomeOtherService", rsElements.get(1).getChild("provide", ns).getAttributeValue("interface"));
    }
    
    public void testMultiRemoteServicesXMLFiles() {
        URL rs1URL = getClass().getResource("/test-resources/rs1.xml");
        URL rs2URL = getClass().getResource("/test-resources/rs2.xml");
        
        Bundle b = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(b.findEntries(
            EasyMock.eq("OSGI-INF/remote-service"), 
            EasyMock.eq("*.xml"), EasyMock.anyBoolean())).andReturn(
                Collections.enumeration(Arrays.asList(rs1URL, rs2URL))).anyTimes();
        EasyMock.replay(b);
        
        List<Element> rsElements = OsgiUtils.getAllDescriptionElements(b);
        assertEquals(3, rsElements.size());
        Namespace ns = Namespace.getNamespace("http://www.osgi.org/xmlns/sd/v1.0.0");
        assertEquals("SomeService", rsElements.get(0).getChild("provide", ns).getAttributeValue("interface"));
        assertEquals("SomeOtherService", rsElements.get(1).getChild("provide", ns).getAttributeValue("interface"));
        assertEquals("org.example.Service", rsElements.get(2).getChild("provide", ns).getAttributeValue("interface"));
    }
    
    @SuppressWarnings("unchecked")
    public void testRemoteServicesXMLFileAlternateLocation() {
        URL rs1URL = getClass().getResource("/test-resources/rs1.xml");
        Dictionary headers = new Hashtable();        
        headers.put("Remote-Service", "META-INF/osgi");
        headers.put("Bundle-Name", "testing bundle");
        
        Bundle b = EasyMock.createNiceMock(Bundle.class);
        EasyMock.expect(b.getHeaders()).andReturn(headers).anyTimes();
        EasyMock.expect(b.findEntries(
            EasyMock.eq("META-INF/osgi"), 
            EasyMock.eq("*.xml"), EasyMock.anyBoolean())).andReturn(
                Collections.enumeration(Arrays.asList(rs1URL))).anyTimes();
        EasyMock.replay(b);
        
        List<Element> rsElements = OsgiUtils.getAllDescriptionElements(b);
        assertEquals(2, rsElements.size());
        Namespace ns = Namespace.getNamespace("http://www.osgi.org/xmlns/sd/v1.0.0");
        assertEquals("SomeService", rsElements.get(0).getChild("provide", ns).getAttributeValue("interface"));
        assertEquals("SomeOtherService", rsElements.get(1).getChild("provide", ns).getAttributeValue("interface"));
    }
}