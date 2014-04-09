/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.charite.compbio.exomiser.resources;

import de.charite.compbio.exomiser.io.FileOperationStatus;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.AllFileSelector;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the extraction of files from their downloaded state to an
 * unzipped/untarred state ready for parsing.
 *
 * @author Jules Jacobsen <jules.jacobsen@sanger.ac.uk>
 */
public class ResourceExtractionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ResourceExtractionHandler.class);

    public static void extractResources(Iterable<ExternalResource> externalResources, Path inPath, Path outPath) {
        for (ExternalResource externalResource : externalResources) {
            extractResource(externalResource, inPath, outPath);
        }
    }

    public static void extractResource(ExternalResource externalResource, Path inPath, Path outPath) {
        logger.info("Extracting resource {}...", externalResource.getRemoteFileName());

        //make sure the output path exists
        outPath.toFile().mkdir();
        
        //expected types: .tar.gz, .gz, .zip, anything else
        File inFile = new File(inPath.toFile(), externalResource.getRemoteFileName());
        File outFile = new File(outPath.toFile(), externalResource.getExtractedFileName());
        
        FileOperationStatus status = FileOperationStatus.FAILURE;
        String scheme = externalResource.getExtractionScheme();
        logger.info("Using resource extractionScheme: {}", scheme);
        switch (scheme) {
            case "gz":
                status = gunZipFile(inFile, outFile);
                break;
            case "tgz":
                status = extractTgzArchive(inFile, outFile);
                break;
            case "zip":
                status = extractArchive(inFile, outFile);
                break;
            case "copy":
                status = copyFile(inFile, outFile);                
                break;
            case "none":
                status = FileOperationStatus.SUCCESS;
                break;
            default:
                status = copyFile(inFile, outFile);
        }

        externalResource.setExtractStatus(status);
    }
    
    private static FileOperationStatus gunZipFile(File inFile, File outFile) {
        logger.info("Unzipping file: {} to {}", inFile, outFile);
        
        try (FileInputStream fileInputStream = new FileInputStream(inFile);
                GZIPInputStream gZIPInputStream = new GZIPInputStream(fileInputStream);
                OutputStream out = new FileOutputStream(outFile);
                ) {            
            //good for under 2GB otherwise IOUtils.copyLarge is needed
            IOUtils.copy(gZIPInputStream, out);
           
            return FileOperationStatus.SUCCESS;

        } catch (FileNotFoundException ex) {
            logger.error(null, ex);
            return FileOperationStatus.FILE_NOT_FOUND;
        } catch (IOException ex) {
            logger.error(null, ex);            
        }
        return FileOperationStatus.FAILURE;
    }

    /**
     * Will extract a .zip or .tar archive to the specified outFile location 
     * @param inFile
     * @param outPath
     * @return 
     */
    private static FileOperationStatus extractArchive(File inFile, File outFile) {
        
        try {
            URI inFileUri = inFile.toURI();
            logger.info("Extracting archive: {}", inFileUri);
            FileSystemManager fileSystemManager = VFS.getManager();
            FileObject tarFile = fileSystemManager.resolveFile(inFileUri.toString());
            try {
                FileObject zipFileSystem = fileSystemManager.createFileSystem(tarFile);
                try {
                    fileSystemManager.toFileObject(outFile).copyFrom(zipFileSystem, new AllFileSelector());
                } finally {
                    zipFileSystem.close();
                }
            } finally {
                tarFile.close();
            }
            return FileOperationStatus.SUCCESS;

        } catch (FileSystemException ex) {
            logger.error(null, ex);
        }
        return FileOperationStatus.FAILURE;
    }

    private static FileOperationStatus extractTgzArchive(File inFile, File outFile) {
        logger.info("Extracting tar.gz file: {} to {}", inFile, outFile);
        File intermediateTarArchive = new File(outFile.getParentFile(), outFile.getName() + ".tar");
        
        //first unzip the file
        gunZipFile(inFile, intermediateTarArchive);
        //then extract the archive files
        FileOperationStatus returnStatus = extractArchive(intermediateTarArchive, outFile);       
        //finally clean-up the intermediate file
        intermediateTarArchive.delete();
        return returnStatus;
    }
        

    private static FileOperationStatus copyFile(File inFile, File outFile) {
        logger.info("Copying file {} to {}", inFile, outFile);
        
        try {
            Files.copy(inFile.toPath(), outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            //update the last modified time to now so that it's easy to see when the file was 
            //copied over otherwise the last modified time will be when the file was downloaded.
            //this is merely a nicety for when manually inspecting the db build process. 
            outFile.setLastModified(System.currentTimeMillis());
            return FileOperationStatus.SUCCESS;
        } catch (IOException ex) {
            logger.error(null, ex);
        }
        return FileOperationStatus.FAILURE;
    }
}
