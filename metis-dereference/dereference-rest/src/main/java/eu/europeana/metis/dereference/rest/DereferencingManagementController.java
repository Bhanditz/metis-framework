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
package eu.europeana.metis.dereference.rest;

import eu.europeana.metis.dereference.Vocabulary;
import eu.europeana.metis.dereference.service.MongoDereferencingManagementService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.util.List;

/**
 * REST controller for managing vocabularies and entities
 * Created by gmamakis on 12-2-16.
 */
@Controller
@Api("/")

public class DereferencingManagementController {

    @Autowired
    private MongoDereferencingManagementService service;

    /**
     * Save a vocabulary
     * @param vocabulary The vocabulary to save
     * @return OK
     */
    @RequestMapping(value = "/vocabulary",consumes = "application/json",method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Save a vocabulary")
    public void saveVocabulary(@ApiParam @RequestBody Vocabulary vocabulary){
        service.saveVocabulary(vocabulary);

    }


    /**
     * Update a vocabulary
     * @param vocabulary The vocabulary to update
     * @return OK
     */
    @RequestMapping(value = "/vocabulary",consumes = "application/json",method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Update a vocabulary")
    public void updateVocabulary(@ApiParam @RequestBody Vocabulary vocabulary){
        service.updateVocabulary(vocabulary);
    }

    /**
     * Delete a vocabulary
     * @param vocabulary The vocabulary to delete
     * @return OK
     */
    @RequestMapping(value = "/vocabulary",consumes = "application/json",method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Delete a vocabulary")
    public void deleteVocabulary(@ApiParam @RequestBody Vocabulary vocabulary){
        service.deleteVocabulary(vocabulary);
    }

    /**
     * Retrieve a vocabulary by name
     * @param name The name of the vocabulary
     * @return The Vocabulary with this name
     */
    @RequestMapping(value = "/vocabulary/{name}",produces = "application/json",method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value = "Retrieve a vocabulary by name")
    public Vocabulary getVocabulary(@ApiParam("name") @PathVariable("name") String name){
        return service.findByName(name);
    }

    /**
     * Retrieve a list of all the registered vocabularies
     * @return The List of all the registered vocabularies
     */
    @RequestMapping(value = "/vocabularies",produces = "application/json",method = RequestMethod.GET)
    @ResponseBody
    @ApiOperation(value = "Retrieve all the vocabularies", response = List.class)
    public List<Vocabulary> getAllVocabularies(){
        return service.getAllVocabularies();

    }

    /**
     * Delete an entity based on a URI
     * @param uri The uri of the entity to delete
     * @return OK
     */
    @RequestMapping(value = "/entity/{uri}",method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Delete an entity")
    public void deleteEntity(@ApiParam("uri") @PathVariable("uri") String uri){
        service.removeEntity(URLDecoder.decode(uri));
    }

    /**
     * Update an entity
     * @param uri The uri of the entity
     * @param xml The xml of the entity
     * @return OK
     */
    @RequestMapping(value="/entity",method = RequestMethod.PUT)
    @ResponseBody
    @ApiOperation(value = "Update an entity")
    public void updateEntity(@ApiParam("uri") @RequestParam(value = "uri") String uri,@ApiParam("xml") @RequestParam(value = "xml") String xml){
        service.updateEntity(uri,xml);
    }

    /**
     * Empty Cache
     * @return OK
     */
    @RequestMapping(value="/cache",method = RequestMethod.DELETE)
    @ResponseBody
    @ApiOperation(value = "Emtpy the cache")
    public void emptyCache(){
        service.emptyCache();
    }
}