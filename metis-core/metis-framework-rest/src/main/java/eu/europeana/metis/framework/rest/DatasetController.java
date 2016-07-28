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
package eu.europeana.metis.framework.rest;

import eu.europeana.metis.framework.OrgDatasetDTO;
import eu.europeana.metis.framework.dataset.Dataset;
import eu.europeana.metis.framework.exceptions.NoDatasetFoundException;
import eu.europeana.metis.framework.service.DatasetService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * The dataset controller
 * Created by ymamakis on 2/18/16.
 */
@Controller
@Api("/")
public class DatasetController {

    @Autowired
    private DatasetService datasetService;

    /**
     * Method to create a dataset (OK)
     */
    @RequestMapping (value = "/dataset",method = RequestMethod.POST, consumes = "application/json")
    @ResponseBody
    @ApiOperation(value="Create a dataset")
    public ResponseEntity<Void> createDataset(@ApiParam @RequestBody OrgDatasetDTO dto){
        datasetService.createDataset(dto.getOrganization(),dto.getDataset());
        return ResponseEntity.noContent().build();
    }

    /**
     * Update a dataset
     * @param ds The dataset to update
     */
    @RequestMapping (value = "/dataset",method = RequestMethod.PUT, consumes = "application/json")
    @ResponseBody
    @ApiOperation(value = "Update a dataset")
    public ResponseEntity<Void> updateDataset(@ApiParam @RequestBody Dataset ds){
        datasetService.updateDataset(ds);
        return ResponseEntity.ok().build();

    }

    /**
     * Delete a dataset
     */
    @RequestMapping (value = "/dataset",method = RequestMethod.DELETE, consumes = "application/json")
    @ResponseBody
    @ApiOperation(value = "Delete a dataset from an organization")
    public ResponseEntity<Void> deleteDataset(@ApiParam @RequestBody OrgDatasetDTO dto){
        datasetService.deleteDataset(dto.getOrganization(),dto.getDataset());
        return ResponseEntity.noContent().build();
    }

    /**
     * Get a dataset by name
     * @param name The name of the dataset
     * @return The Dataset
     */
    @RequestMapping (value = "/dataset/{name}",method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    @ApiOperation(value = "Retrieve a dataset by name", response = Dataset.class)
    public Dataset getByName(@ApiParam("name") @PathVariable("name") String name) throws NoDatasetFoundException{
        return datasetService.getByName(name);
    }
}