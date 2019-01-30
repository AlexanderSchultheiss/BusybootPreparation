package net.ssehub.kernel_haven.busyboot;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import net.ssehub.kernel_haven.util.Util;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

/**
 * Tests the {@link PrepareBusybox}.
 * 
 * @author Adam
 */
public class PrepareBusyboxTest {
    
    private static final @NonNull File TESTDATA = new File("testdata/prepare_busybox");
    
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
     * Tests the {@link PrepareBusybox#makeDummyMakefile()} method.
     * 
     * @throws IOException unwanted.
     */
    @Test
    public void testMakeDummyMakefile() throws IOException {
        // set up
        PrepareBusybox prep = new PrepareBusybox();
        prep.setSourceTree(TMP_DIR);
        
        File makefile = new File(TMP_DIR, "Makefile");
        
        // precondition
        assertThat(makefile.exists(), is(false));
        
        // execute
        prep.makeDummyMakefile();
        
        // check result
        assertThat(makefile.isFile(), is(true));
        
        try (FileInputStream in = new FileInputStream(makefile)) {
            assertThat(Util.readStream(in), is("allyesconfig:\n\nprepare:\n"));
        }
    }
    
}
