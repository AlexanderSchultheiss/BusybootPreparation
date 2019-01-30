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

public class PrepareCorebootTest {
    
    private static final @NonNull File TESTDATA = new File("testdata/prepare_coreboot");
    
    private static final @NonNull File TMP_DIR = new File(TESTDATA, "tmp");
    
    /**
     * Cleans (or creates) the temporary directory before each test.
     * 
     * @throws IOException If cleaning the directory fails.
     */
    @Before
    public void createOrClearTmpDir() throws IOException {
        Util.clearFolder(TMP_DIR);
    }
    
    /**
     * Tests the {@link PrepareCoreboot#initializeExternInt()} method.
     * 
     * @throws IOException unwanted.
     */
    @Test
    public void testInitializeExternInt() throws IOException {
        // set up
        File dir = new File(TMP_DIR, "scripts/kconfig");
        File lkcH = new File(dir, "lkc.h");
        
        dir.mkdirs();
        Util.copyFile(new File(TESTDATA, "lkc.h"), lkcH);
        
        PrepareCoreboot prep = new PrepareCoreboot();
        prep.setSourceTree(TMP_DIR);
        
        // precondition
        assertThat(lkcH.isFile(), is(true));
        
        // execute
        prep.initializeExternInt();
        
        // check
        try (FileInputStream in = new FileInputStream(lkcH)) {
            assertThat(Util.readStream(in), is("/* conf.c */\nextern int kconfig_warnings = 0;\n"));
        }
    }
    
}
