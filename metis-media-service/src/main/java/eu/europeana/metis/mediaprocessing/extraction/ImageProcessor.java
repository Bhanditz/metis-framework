package eu.europeana.metis.mediaprocessing.extraction;

import java.io.IOException;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import eu.europeana.metis.mediaprocessing.exception.MediaExtractionException;
import eu.europeana.metis.mediaprocessing.model.ImageResourceMetadata;
import eu.europeana.metis.mediaprocessing.model.Resource;
import eu.europeana.metis.mediaprocessing.model.ResourceExtractionResult;
import eu.europeana.metis.mediaprocessing.model.Thumbnail;
import eu.europeana.metis.mediaprocessing.model.UrlType;

/**
 * Implementation of {@link MediaProcessor} that is designed to handle resources of type
 * {@link ResourceType#IMAGE}.
 */
class ImageProcessor implements MediaProcessor {

  private final ThumbnailGenerator thumbnailGenerator;

  /**
   * Constructor.
   * 
   * @param thumbnailGenerator An object that can generate thumbnails.
   */
  ImageProcessor(ThumbnailGenerator thumbnailGenerator) {
    this.thumbnailGenerator = thumbnailGenerator;
  }

  @Override
  public ResourceExtractionResult process(Resource resource) throws MediaExtractionException {

    // Sanity checks
    if (!resource.hasContent()) {
      throw new MediaExtractionException("File content is null");
    }
    
    // Create the thumbnails for this image.
    final Pair<ImageMetadata, List<Thumbnail>> thumbnailsAndMetadata =
        thumbnailGenerator.generateThumbnails(resource.getResourceUrl(), ResourceType.IMAGE,
            resource.getContentPath().toFile());

    // Get the size of the resource
    final long contentSize;
    try {
      contentSize = resource.getContentSize();
    } catch (IOException e) {
      throw new MediaExtractionException(
          "Could not determine the size of the resource " + resource.getResourceUrl(), e);
    }

    // Set the metadata in the web resource.
    final ImageResourceMetadata resourceMetadata;
    if (UrlType.shouldExtractMetadata(resource.getUrlTypes())) {
      final ImageMetadata imageMetadata = thumbnailsAndMetadata.getLeft();
      resourceMetadata =
          new ImageResourceMetadata(resource.getMimeType(), resource.getResourceUrl(), contentSize,
              imageMetadata.getWidth(), imageMetadata.getHeight(), imageMetadata.getColorSpace(),
              imageMetadata.getDominantColors(), thumbnailsAndMetadata.getRight());
    } else {
      resourceMetadata = null;
    }

    // Done
    return new ResourceExtractionResult(resourceMetadata, thumbnailsAndMetadata.getRight());
  }
}
