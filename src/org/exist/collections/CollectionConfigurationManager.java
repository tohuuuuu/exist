/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.collections;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.storage.DBBroker;
import org.exist.storage.sync.Sync;
import org.exist.util.Lock;
import org.exist.util.LockException;
import org.xml.sax.SAXException;

/**
 * Manages index configurations. Index configurations are stored in a collection
 * hierarchy below /db/system/config. CollectionConfigurationManager is called
 * by {@link org.exist.collections.Collection} to retrieve the
 * {@link org.exist.collections.CollectionConfiguration} instance for a given collection.
 * 
 * @author wolf
 */
public class CollectionConfigurationManager {

	private static final Logger LOG = Logger.getLogger(CollectionConfigurationManager.class);
	
    public final static String CONFIG_COLLECTION = "/db/system/config";
    
    private SecurityManager secman;

    private Map cache = new TreeMap();
    
    public CollectionConfigurationManager(DBBroker broker) throws EXistException {
        this.secman = broker.getBrokerPool().getSecurityManager();
		checkConfigCollection(broker);
    }
    
    public synchronized void addConfiguration(DBBroker broker, Collection collection, String config) 
    throws CollectionConfigurationException {
    	try {
			String path = CONFIG_COLLECTION + collection.getName();
			Collection confCol = broker.getOrCreateCollection(path);
			if(confCol == null)
				throw new CollectionConfigurationException("Failed to create config collection: " + path);
			broker.saveCollection(confCol);
			IndexInfo info = confCol.validate(broker, "collection.xconf", config);
			confCol.store(broker, info, config, false);
			broker.sync(Sync.MAJOR_SYNC);
		} catch (PermissionDeniedException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (CollectionConfigurationException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (EXistException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (TriggerException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (SAXException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		} catch (LockException e) {
			throw new CollectionConfigurationException("Failed to store collection configuration: " + e.getMessage(), e);
		}
    }
    
    /**
     * Retrieve the collection configuration instance for the given collection. This
     * creates a new CollectionConfiguration object and recursively scans the collection
     * hierarchy for available configurations.
     * 
     * @param broker
     * @param collection
     * @param collectionPath
     * @return
     * @throws CollectionConfigurationException
     */
    protected synchronized CollectionConfiguration getConfiguration(DBBroker broker, 
            Collection collection) throws CollectionConfigurationException {
    	LOG.debug("Reading config for " + collection.getName());
    	CollectionConfiguration conf = new CollectionConfiguration(collection);
    	String path = collection.getName() + '/';
    	int p = "/db".length();
    	String next;
    	Collection coll = null;
    	while(p != -1) {
    		next = CONFIG_COLLECTION + path.substring(0, p);
    		try {
    			coll = broker.openCollection(next, Lock.READ_LOCK);
    			if(coll != null && coll.hasDocument(CollectionConfiguration.COLLECTION_CONFIG_FILE)) {
    				LOG.debug("Reading collection.xconf from " + coll.getName());
    				DocumentImpl confDoc = 
    					coll.getDocument(broker, CollectionConfiguration.COLLECTION_CONFIG_FILE);
    				conf.read(broker, confDoc);
    			}
    		} finally {
    			if(coll != null)
    				coll.release();
    		}
    		p = path.indexOf('/', p + 1);
	    }
    	cache.put(collection.getName(), conf);
        return conf;
    }
    
    /**
     * Notify the manager that a collection.xconf file has changed. All cached configurations
     * for the corresponding collection and its sub-collections will be cleared. 
     * 
     * @param collectionPath
     */
    protected synchronized void invalidateAll(String collectionPath) {
    	collectionPath = collectionPath.substring(CONFIG_COLLECTION.length());
    	String next;
    	CollectionConfiguration config;
    	for(Iterator i = cache.keySet().iterator(); i.hasNext(); ) {
    		next = (String) i.next();
    		if(next.startsWith(collectionPath)) {
    			config = (CollectionConfiguration) cache.get(next);
    			if (config != null)
    				config.getCollection().invalidateConfiguration();
    			cache.remove(next);
    		}
    	}
    }
    
    /**
     * Called by the collection cache if a collection is removed from the cache.
     * This will delete the cached configuration instance for this collection.
     * 
     * @param collectionPath
     */
    protected synchronized void invalidate(String collectionPath) {
    	if (!collectionPath.startsWith(CONFIG_COLLECTION))
    		return;
    	collectionPath = collectionPath.substring(CONFIG_COLLECTION.length());
    	CollectionConfiguration config = (CollectionConfiguration) cache.get(collectionPath);
    	if (config != null) {
    		config.getCollection().invalidateConfiguration();
    		cache.remove(config);
    	}
    }
    
    private void checkConfigCollection(DBBroker broker) throws EXistException {
    	try {
    		Collection root = broker.getCollection(CONFIG_COLLECTION);
    		if(root == null) {
    			root = broker.getOrCreateCollection(CONFIG_COLLECTION);
    			broker.saveCollection(root);
    		}
    	} catch (PermissionDeniedException e) {
    		throw new EXistException("Failed to initialize /db/system/config: " + e.getMessage());
    	}
    }
}
