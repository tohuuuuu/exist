package org.exist.xmldb.test;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

import junit.framework.TestCase;

import org.exist.dom.XMLUtil;
import org.exist.util.XMLFilenameFilter;
import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

public class CreateCollectionsTest extends TestCase {

	private final static String URI = "xmldb:exist:///db";
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

	public Collection root = null;

	public CreateCollectionsTest(String arg0) {
		super(arg0);
	}

	protected void setUp() {
		try {
			// initialize driver
			Class cl = Class.forName(DRIVER);
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);

			// try to get collection
			root = DatabaseManager.getCollection(URI);
		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
	}

	public void testCreateCollection() {
		assertNotNull(root);
		try {
			System.out.println(
				"Created Collection: "
					+ root.getName()
					+ "( "
					+ root.getClass()
					+ " )");

			Service[] services = root.getServices();
			System.out.println("services array: " + services);
			assertTrue(
				"Collection must provide at least one Service",
				services != null && services.length > 0);
			System.out.println("  number of services: " + services.length);
			for (int i = 0; i < services.length; i++) {
				System.out.println(
					"  Service: "
						+ services[i].getName()
						+ "( "
						+ services[i].getClass()
						+ " )");
			}

			Collection parentCollection = root.getParentCollection();
			System.out.println("root parentCollection: " + parentCollection);
			assertNull("root collection has no parent", parentCollection);

			CollectionManagementService service =
				(CollectionManagementService) root.getService(
					"CollectionManagementService",
					"1.0");
			assertNotNull(service);
			Collection testCollection = service.createCollection("test");

			assertNotNull(testCollection);
			int ccc = testCollection.getChildCollectionCount();
			assertTrue(
				"Collection just created: ChildCollectionCount==0",
				ccc == 0);
			assertTrue(
				"Collection state should be Open after creation",
				testCollection.isOpen());

			String directory = "samples/shakespeare";
			System.out.println("---------------------------------------");
			System.out.println("storing all XML files in directory " +directory+"...");
			System.out.println("---------------------------------------");
			File f = new File(directory);
			File files[] = f.listFiles(new XMLFilenameFilter());

			for (int i = 0; i < files.length; i++) {
				storeResourceFromFile(files[i], testCollection);
			}

			HashSet fileNamesJustStored = new HashSet();
			for (int i = 0; i < files.length; i++) {
				String file = files[i].toString();
				int lastSeparator = file.lastIndexOf(File.separatorChar);
				fileNamesJustStored.add(file.substring(lastSeparator + 1));
			}
			System.out.println("fileNames stored: " + fileNamesJustStored.toString());

			String[] resourcesNames = testCollection.listResources();
			int resourceCount = testCollection.getResourceCount();
			System.out.println(  "testCollection.getResourceCount()=" + resourceCount);

			ArrayList fileNamesPresentInDatabase = new ArrayList();
			for (int i = 0; i < resourcesNames.length; i++) {
				fileNamesPresentInDatabase.add( resourcesNames[i]);
			}
			assertTrue( "resourcesNames must contain fileNames just stored",
					fileNamesPresentInDatabase. containsAll( fileNamesJustStored) );

			String fileToRemove = "macbeth.xml";
			Resource resMacbeth = testCollection.getResource(fileToRemove);
			assertNotNull("getResource(" + fileToRemove + "\")", resMacbeth);
			testCollection.removeResource(resMacbeth);
			assertTrue(
				"After removal resource count must decrease",
				testCollection.getResourceCount() == resourceCount - 1);
			// restore the resource just removed :
			storeResourceFromFile(
				new File(
					directory + File.separatorChar + fileToRemove),
				testCollection);

			byte[] data = storeBinaryResourceFromFile( new File( "webapp/logo.jpg"), testCollection);
			Object content = testCollection.getResource("logo.jpg").getContent();
			byte[] dataStored = (byte[])content;
			assertTrue("After storing binary resource, data out==data in", 
					Arrays.equals(dataStored, data) );
			
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	private XMLResource storeResourceFromFile(
		File file,
		Collection testCollection)
		throws XMLDBException, IOException {
		System.out.println("storing " + file.getAbsolutePath());
		XMLResource res;
		String xml;
		res =
			(XMLResource) testCollection.createResource(
				file.getName(),
				"XMLResource");
		assertNotNull("storeResourceFromFile", res);
		xml = XMLUtil.readFile(file, "UTF-8");
		res.setContent(xml);
		testCollection.storeResource(res);
		System.out.println("stored " + file.getAbsolutePath());
		return res;
	}

	private byte[] storeBinaryResourceFromFile(
			File file,
			Collection testCollection)
			throws XMLDBException, IOException {
			System.out.println("storing " + file.getAbsolutePath());

			Resource res =
				(BinaryResource) testCollection.createResource(
					file.getName(),
					"BinaryResource" );
			assertNotNull("store binary Resource From File", res);
			
			// Get an array of bytes from the file:
			 FileInputStream istr = new FileInputStream(file); 
			 BufferedInputStream bstr = new BufferedInputStream( istr ); // promote
			 int size = (int) file.length();  // get the file size (in bytes)
			 byte[] data = new byte[size]; // allocate byte array of right size
			 bstr.read( data, 0, size );   // read into byte array
			 bstr.close();
			 
			res.setContent(data);
			testCollection.storeResource(res);
			System.out.println("stored " + file.getAbsolutePath());
			return data;
		}
	
	public void testMultipleCreates() {
		try {
        	Collection rootColl = DatabaseManager.getCollection("xmldb:exist:///db");
        	CollectionManagementService cms = (CollectionManagementService)
				rootColl.getService("CollectionManagementService", "1.0");
			assertNotNull(cms);
        	cms.createCollection("dummy1");
        	printChildren(rootColl);
        	Collection c1 = rootColl.getChildCollection("dummy1");
			assertNotNull(c1);
        	cms.setCollection(c1);
        	cms.createCollection("dummy2");
        	Collection c2 = c1.getChildCollection("dummy2");
			assertNotNull(c2);
        	cms.setCollection(c2);
        	cms.createCollection("dummy3");
        	Collection c3 = c2.getChildCollection("dummy3");
			assertNotNull(c3);
        	cms.setCollection(rootColl);
        	cms.removeCollection("dummy1");
        	printChildren(rootColl);
			assertTrue("number of child collections should be 2", 
				rootColl.getChildCollectionCount()==2);
		} catch(Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

    private static void printChildren(Collection c) throws XMLDBException {
        System.out.print("Children of " + c.getName() + ":");
        String[] names = c.listChildCollections();
        for (int i = 0; i < names.length; i++)
            System.out.print(" " + names[i]);
        System.out.println();
    }
    
	public static void main(String[] args) {
		junit.textui.TestRunner.run(CreateCollectionsTest.class);
		//junit.swingui.TestRunner.run(LexerTest.class);
	}
}
