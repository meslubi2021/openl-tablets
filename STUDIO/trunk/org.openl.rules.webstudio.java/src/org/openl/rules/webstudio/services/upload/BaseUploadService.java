package org.openl.rules.webstudio.services.upload;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.openl.rules.webstudio.services.Service;
import org.openl.rules.webstudio.services.ServiceException;
import org.openl.rules.webstudio.services.ServiceParams;
import org.openl.rules.webstudio.services.ServiceResult;
import org.openl.util.FileTypeHelper;

import org.richfaces.model.UploadItem;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Base class for upload services.
 *
 * @author Andrey Naumenko
 */
public abstract class BaseUploadService implements Service {
    private static final Log LOG = LogFactory.getLog(BaseUploadService.class);

    /**
     * {@inheritDoc}
     */
    public ServiceResult execute(ServiceParams serviceParams) throws ServiceException {
        UploadServiceParams params = (UploadServiceParams) serviceParams;
        UploadServiceResult result = null;

        try {
            UploadItem file = params.getFile();
            if (file == null) {
                throw new ServiceException("File was not found.");
            }
            if (FileTypeHelper.isZipFile(file.getFileName())
                    && params.isUnpackZipFile()) {
                result = uploadAndUnpackZipFile(params);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Zip file uploaded and unpacked: " + result.getResultFiles());
                }
            } else {
                result = uploadFile(params);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("File uploaded to '" + result.getResultFile().getName() + "'");
                }
            }
        } finally {
            // uploadFile.cleanup();
        }

        return result;
    }

    /**
     * Return file object where uploaded file should be stored.
     *
     * @param params service parameters
     * @param fileName name of uploaded file
     *
     * @return where uploaded file should be stored
     *
     * @throws IOException if I/O error occurs
     */
    protected abstract File getFile(UploadServiceParams params, String fileName) throws IOException;

    private void saveFile(UploadServiceParams params, File tempFile) throws FileNotFoundException, IOException {
        OutputStream tempOS = new FileOutputStream(tempFile);
        InputStream in = new FileInputStream(params.getFile().getFile());
        try {
            FileCopyUtils.copy(in, tempOS);
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(tempOS);
        }
    }

    /**
     * Unpack uploaded archive file.
     *
     * @param params service parameters
     * @param result service result
     * @param zipFile zippedFile
     *
     * @throws IOException if I/O error occurs
     * @throws ServiceException if error occurs
     */
    protected abstract void unpack(UploadServiceParams params, UploadServiceResult result, File zipFile)
            throws IOException, ServiceException;

    private UploadServiceResult uploadFile(UploadServiceParams params) throws ServiceException {
        File targetFile;
        UploadServiceResult result = new UploadServiceResult();

        // determine file to save uploaded file
        try {
            // targetFile = getFile(params, params.getFile().getName());
            targetFile = File.createTempFile("upload", "file");
            saveFile(params, targetFile);

            result.setResultFile(targetFile);
            result.setUploadCount(1);
            return result;
        } catch (IOException e) {
            String msg = "Unable to upload file '" + params.getFile().getFileName() + "'";
            throw new ServiceException(msg + ": " + e.getMessage(), e);
        }
    }

    private UploadServiceResult uploadAndUnpackZipFile(UploadServiceParams params) throws ServiceException {
        UploadServiceResult result = new UploadServiceResult();

        try {
            File tempFile = File.createTempFile("upload", "zip");
            try {
                saveFile(params, tempFile);
                unpack(params, result, tempFile);
            } finally {
                tempFile.delete();
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("File '" + params.getFile().getFileName() + "' unpacked");
            }
        } catch (IOException e) {
            throw new ServiceException(e);
        }
        return result;
    }
}
