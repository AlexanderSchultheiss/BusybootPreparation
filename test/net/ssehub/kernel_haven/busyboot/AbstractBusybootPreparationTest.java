package net.ssehub.kernel_haven.busyboot;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.util.Util;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

/**
 * Tests the helper methods of {@link AbstractBusybootPreparation}.
 * 
 * @author Adam
 */
public class AbstractBusybootPreparationTest {

    private static class DummyImpl extends AbstractBusybootPreparation {

        @Override
        protected void runImpl() throws SetUpException {
        }
        
    }

    private static final @NonNull File TESTDATA = new File("testdata/helper");
    
    private static final @NonNull File TMP_DIR = new File(TESTDATA, "tmp");
    
    /**
     * Cleans (or creates) the temporary directory before each test.
     * 
     * @throws IOException If cleaning the directory fails.
     */
    @Before
    public void createOrClearTmpDir() throws IOException {
        TMP_DIR.mkdirs();
        Util.clearFolder(TMP_DIR);
    }
    
    /**
     * Tests the {@link AbstractBusybootPreparation#copyOriginal()} method.
     * 
     * @throws IOException unwanted.
     */
    @Test
    public void testCopyOriginal() throws IOException {
        File expectedDir = new File(TESTDATA, "tmpUnchangedCopy");
        File expectedFile = new File(expectedDir, "test.txt");
        
        try {
            // set up
            AbstractBusybootPreparation prep = new DummyImpl();
            prep.setSourceTree(TMP_DIR);
            
            String content = "TestString\n";
            
            try (FileWriter out = new FileWriter(new File(TMP_DIR, "test.txt"))) {
                out.write(content);
            }
            
            
            // precondition
            assertThat(expectedDir.exists(), is(false));
            
            // execute
            prep.copyOriginal();
            
            // check result
            assertThat(expectedDir.isDirectory(), is(true));
            assertThat(expectedFile.isFile(), is(true));
            
            try (FileInputStream in = new FileInputStream(expectedFile)) {
                assertThat(Util.readStream(in), is(content));
            }
            
        } finally {
            // clean up
            Util.deleteFolder(expectedDir);
        }
    }
    
    /**
     * Tests that the {@link AbstractBusybootPreparation#copyOriginal()} method correctly throws an exception if the
     * target already exists.
     * 
     * @throws IOException wanted.
     */
    @Test(expected = IOException.class)
    public void testCopyOriginalAlreadyExisting() throws IOException {
        File existing = new File(TESTDATA, "tmpUnchangedCopy");
        
        try {
            // set up
            AbstractBusybootPreparation prep = new DummyImpl();
            prep.setSourceTree(TMP_DIR);
            
            existing.mkdir();
            
            // precondition
            assertThat(existing.isDirectory(), is(true));
            
            // execute
            prep.copyOriginal();
            
        } finally {
            // clean up
            Util.deleteFolder(existing);
        }
    }
    
    /**
     * Tests the {@link AbstractBusybootPreparation#replaceInFile(File, File, String, String)} method.
     * 
     * @throws IOException unwanted.
     */
    @Test
    public void replaceInFile() throws IOException {
        String originalContent = "Hello World!\n";
        String expectedNewContent = "Hello All!\n";
        
        // set up a file
        File fileIn = new File(TMP_DIR, "in.txt");
        try (FileWriter out = new FileWriter(fileIn)) {
            out.write(originalContent);
        }
        
        File fileOut = new File(TMP_DIR, "out.txt");
        
        // precondition
        assertThat(fileIn.isFile(), is(true));
        assertThat(fileIn.length(), is((long) originalContent.length()));
        assertThat(fileOut.exists(), is(false));
        
        // execute replace
        AbstractBusybootPreparation.replaceInFile(fileIn, fileOut, "World", "All");
        
        // check result
        assertThat(fileIn.exists(), is(false));
        assertThat(fileOut.isFile(), is(true));
        
        try (FileInputStream in = new FileInputStream(fileOut)) {
            assertThat(Util.readStream(in), is(expectedNewContent));
        }
    }
    
    /**
     * Tests the {@link AbstractBusybootPreparation#replaceInFile(File, File, String, String)} method with the same
     * input and output file.
     * 
     * @throws IOException unwanted.
     */
    @Test
    public void replaceInSameFile() throws IOException {
        String originalContent = "Hello World!\n";
        String expectedNewContent = "Hello All!\n";
        
        // set up a file
        File fileIn = new File(TMP_DIR, "in.txt");
        try (FileWriter out = new FileWriter(fileIn)) {
            out.write(originalContent);
        }
        
        // precondition
        assertThat(fileIn.isFile(), is(true));
        assertThat(fileIn.length(), is((long) originalContent.length()));
        
        // execute replace
        AbstractBusybootPreparation.replaceInFile(fileIn, fileIn, "World", "All");
        
        // check result
        assertThat(fileIn.isFile(), is(true));
        
        try (FileInputStream in = new FileInputStream(fileIn)) {
            assertThat(Util.readStream(in), is(expectedNewContent));
        }
    }
    
    /**
     * Tests the {@link AbstractBusybootPreparation#findFilesByName(File, String)} method.
     */
    @Test
    public void testFindFilesByName() {
        File directory = new File(TESTDATA, "find_files_by_name");
        
        // precondition
        assertThat(directory.isDirectory(), is(true));
        
        // execute
        List<File> result = AbstractBusybootPreparation.findFilesByName(directory, "match.txt");
        
        assertThat(new HashSet<>(result), is(new HashSet<>(Arrays.asList(
            new File(directory, "match.txt"),
            new File(new File(directory, "subdir"), "match.txt")
        ))));
    }
    
    /**
     * Tests that the {@link AbstractBusybootPreparation#findFilesByName(File, String)} method does exact name matching.
     */
    @Test
    public void testFindFilesByNameExactMatch() {
        File directory = new File(TESTDATA, "find_files_by_name");
        
        // precondition
        assertThat(directory.isDirectory(), is(true));
        
        // execute
        List<File> result = AbstractBusybootPreparation.findFilesByName(directory, "Makefile");
        
        assertThat(new HashSet<>(result), is(new HashSet<>(Arrays.asList(
            new File(directory, "Makefile"),
            // new File(directory, "Makefile.extension"), <- this should not be found
            new File(new File(directory, "subdir"), "Makefile")
        ))));
    }
    
    /**
     * Tests that the {@link AbstractBusybootPreparation#findFilesByName(File, String)} method returns an empty list
     * when a file is passed to it instead of a directory.
     */
    @Test
    public void testFindFilesByNameNotADir() {
        File directory = new File(TESTDATA, "not_a_dir.txt");
        
        // precondition
        assertThat(directory.isFile(), is(true));
        
        // execute
        List<File> result = AbstractBusybootPreparation.findFilesByName(directory, "some");
        
        // check result
        assertThat(result, is(Arrays.asList()));
    }
    
}
