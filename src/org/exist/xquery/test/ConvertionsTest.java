/*
 * Created on 04.07.2005 - $Id$
 */
package org.exist.xquery.test;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xquery.XPathException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

/** Tests for various XQuery (XML Schema) simple types conversions.
 * @author jmvanel
 */
public class ConvertionsTest extends TestCase {

	private XPathQueryService service;
	private Collection root = null;
	private Database database = null;
	
	public static void main(String[] args) throws XPathException {
		TestRunner.run(XQueryFunctionsTest.class);
	}
	
	/**
	 * Constructor for XQueryFunctionsTest.
	 * @param arg0
	 */
	public ConvertionsTest(String arg0) {
		super(arg0);
	}	
	

/** test conversion from QName to string */	
	public void testQName2string() throws XPathException {
		ResourceSet result 	= null;
		String		r		= "";
		String		query	= null;
		try {
			query = "declare namespace foo = 'http://foo'; \n" +
					"let $a := ( xs:QName('foo:bar'), xs:QName('foo:john'), xs:QName('foo:doe') )\n" +
						"for $b in $a \n" +
							"return \n" +
								"<blah>{string($b)}</blah>" ;
			result 	= service.query( query );
			/* which returns :
				<blah>foo:bar</blah>
				<blah>foo:john</blah>
				<blah>foo:doe</blah>"
			*/
			r = (String) result.getResource(0).getContent();
			assertEquals( "<blah>foo:bar</blah>", r );
			assertEquals( "XQuery: " + query, 3, result.getSize() );
			
		} catch (XMLDBException e) {
			System.out.println("testQName2string(): " + e);
			fail(e.getMessage());
		}
	}

	
	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		// initialize driver
		Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
		database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);
		root = DatabaseManager.getCollection("xmldb:exist:///db", "admin", null);
		service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		DatabaseManager.deregisterDatabase(database);
		DatabaseInstanceManager dim =
			(DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
		dim.shutdown();
		//System.out.println("tearDown PASSED");
	}
}
