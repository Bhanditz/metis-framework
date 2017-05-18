/*
 * Copyright 2007-2013 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 *
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under
 *  the Licence.
 */
package eu.europeana.metis.core.mongo;

import com.mongodb.MongoClient;
import eu.europeana.metis.core.api.MetisKey;
import eu.europeana.metis.core.common.AltLabel;
import eu.europeana.metis.core.common.FolderMetadata;
import eu.europeana.metis.core.common.FtpMetadata;
import eu.europeana.metis.core.common.HarvestingMetadata;
import eu.europeana.metis.core.common.HttpMetadata;
import eu.europeana.metis.core.common.OAIMetadata;
import eu.europeana.metis.core.common.PrefLabel;
import eu.europeana.metis.core.dataset.Dataset;
import eu.europeana.metis.core.dataset.FtpDatasetMetadata;
import eu.europeana.metis.core.dataset.HttpDatasetMetadata;
import eu.europeana.metis.core.dataset.OAIDatasetMetadata;
import eu.europeana.metis.core.organization.Organization;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.springframework.stereotype.Component;

/**
 * Class providing connections to Mongo
 * Created by ymamakis on 2/17/16.
 */
@Component
public class MorphiaDatastoreProvider {

    private Datastore datastore;

    public MorphiaDatastoreProvider(MongoClient mongoClient,String db){
        Morphia morphia = new Morphia();
        morphia.map(Dataset.class);
        morphia.map(Organization.class);
        morphia.map(FtpDatasetMetadata.class);
        morphia.map(FtpMetadata.class);
        morphia.map(FolderMetadata.class);
        morphia.map(HttpMetadata.class);
        morphia.map(HarvestingMetadata.class);
        morphia.map(OAIMetadata.class);
        morphia.map(HttpDatasetMetadata.class);
        morphia.map(OAIDatasetMetadata.class);
        morphia.map(PrefLabel.class);
        morphia.map(AltLabel.class);
        morphia.map(MetisKey.class);
        datastore = morphia.createDatastore(mongoClient,db);
        datastore.ensureIndexes();
    }
    /**
     * Retrieve the datastore connection to Mongo
     * @return The datastore connection to Mongo
     */
    public Datastore getDatastore(){
        return datastore;
    }
}