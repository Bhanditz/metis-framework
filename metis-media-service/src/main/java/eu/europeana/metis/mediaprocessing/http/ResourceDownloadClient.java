package eu.europeana.metis.mediaprocessing.http;

import eu.europeana.metis.mediaprocessing.extraction.MediaProcessor;
import eu.europeana.metis.mediaprocessing.model.RdfResourceEntry;
import eu.europeana.metis.mediaprocessing.model.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceDownloadClient extends HttpClient<Resource> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResourceDownloadClient.class);

  public ResourceDownloadClient(int followRedirects) {
    super(followRedirects, 10000, 20000);
  }

  @Override
  protected Resource createResult(RdfResourceEntry resourceEntry, URI actualUri, String mimeType,
      ContentRetriever contentRetriever) throws IOException {

    // Create resource
    final Resource resource = new Resource(resourceEntry, mimeType, actualUri);

    // In case we are expecting a file, we download it.
    try {
      if (!MediaProcessor.supportsLinkProcessing(mimeType)) {
        LOGGER.debug("Starting download of resource: {}", resourceEntry.getResourceUrl());
        downloadResource(resourceEntry.getResourceUrl(), resource, contentRetriever);
        LOGGER.debug("Finished download of resource: {}", resourceEntry.getResourceUrl());
      }
    } catch (IOException | RuntimeException e) {
      // Close the resource if a problem occurs.
      resource.close();
      throw e;
    }

    // Done: return the resource.
    return resource;
  }

  private static void downloadResource(String resourceUrl, Resource resource,
      ContentRetriever contentRetriever) throws IOException {
    try (final InputStream inputStream = contentRetriever.getContent()) {
      Files.copy(inputStream, resource.getContentPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    if (resource.getContentSize() == 0) {
      throw new IOException("Download failed of resource " + resourceUrl + ": no content found.");
    }
  }
}