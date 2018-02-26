package fr.pilato.elasticsearch.crawler.fs.crawler;

import fr.pilato.elasticsearch.crawler.fs.beans.FileModel;

import java.io.Closeable;
import java.io.InputStream;
import java.util.Collection;

/**
 * FsParser interface. Any implementation class will have to implement
 * how the crawling implementation is done
 */
public interface FsParser extends Closeable {

    /**
     * Method call at every run
     * @param run   The run number
     */
    void crawl(int run);

    /**
     * Open a connection. By default: do nothing.
     */
    default void openConnection() throws Exception {

    }

    /**
     * Validate that settings are correct, ie dir exist on the File System
     */
    void validate();

    /**
     * Close the connection. By default: do nothing.
     */
    @Override
    default void close() { }

    /**
     * Get files from a given path
     * @param dir path
     * @return the list of existing files/dir
     */
    Collection<FileModel> getFiles(String dir) throws Exception;

    /**
     * Get an input stream from a given file
     * @param file  File to stream
     * @return the input stream
     * @throws Exception if anything goes wrong while getting the stream
     */
    InputStream getInputStream(FileModel file) throws Exception;
}
