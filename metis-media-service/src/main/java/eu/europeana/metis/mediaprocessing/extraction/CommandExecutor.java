package eu.europeana.metis.mediaprocessing.extraction;

import eu.europeana.metis.mediaprocessing.exception.CommandExecutionException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class executes commands (like you would in a terminal). It imposes a maximum number of
 * processes that can perform command-line IO at any given time.
 */
class CommandExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(CommandExecutor.class);

  private final ExecutorService commandThreadPool;

  /**
   * Constructor.
   *
   * @param commandThreadPoolSize The maximum number of processes that can perform command-line IO
   * at any given time.
   */
  CommandExecutor(int commandThreadPoolSize) {
    this.commandThreadPool = Executors.newFixedThreadPool(commandThreadPoolSize);
  }

  /**
   * Execute a command.
   *
   * @param command The command to execute, as a list of directives and parameters
   * @param redirectErrorStream Whether to return the contents of the error stream as part of the
   * command's output. If this is false, and there is error output but no regular output, an
   * exception will be thrown.
   * @return The output of the command as a list of lines.
   * @throws CommandExecutionException In case a problem occurs.
   */
  List<String> execute(List<String> command, boolean redirectErrorStream)
      throws CommandExecutionException {
    final Callable<List<String>> task = () -> executeInternal(command, redirectErrorStream);
    try {
      return commandThreadPool.submit(task).get();
    } catch (ExecutionException e) {
      throw new CommandExecutionException("Problem while executing command.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CommandExecutionException("Process was interrupted.", e);
    }
  }

  private List<String> executeInternal(List<String> command, boolean redirectErrorStream)
      throws IOException {

    // Create process and start it.
    final Process process = new ProcessBuilder(command).redirectErrorStream(redirectErrorStream)
        .start();

    // Open error stream and read it.
    final String error;
    if (!redirectErrorStream) {
      try (InputStream errorStream = process.getErrorStream()) {
        final String errorStreamContents = IOUtils.toString(errorStream, Charset.defaultCharset());
        error = StringUtils.isBlank(errorStreamContents) ? null : errorStreamContents;
      }
    } else {
      error = null;
    }

    // Read process output into lines.
    final List<String> result;
    try (InputStream in = process.getInputStream()) {
      result = IOUtils.readLines(in, Charset.defaultCharset());
    }

    // If there is no regular output but there is error output, throw an exception.
    if (error != null) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn("Command presented with error:\nCommand: [{}]\nError: {}",
            String.join(" ", command), error);
      }
      if (result.isEmpty()) {
        throw new IOException("External process returned error content:\n" + error);
      }
    }

    // Else return the result.
    return result;
  }

  /**
   * Shuts down this command executor. Current tasks will be finished, but no new tasks will be
   * accepted.
   */
  void shutdown() {
    commandThreadPool.shutdown();
  }
}
