package net.ssehub.kernel_haven.busyboot;

import java.io.File;
import java.io.IOException;

import org.junit.Before;

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

}
